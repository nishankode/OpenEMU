#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>

#include "emulator_session.h"
#include "link/local_link_session.h"
#include "mgba_core_adapter.h"
#include "placeholder_renderer.h"

namespace {
constexpr const char* kTag = "LinkRoomNative";
constexpr auto kFrameInterval = std::chrono::microseconds(16667);

std::mutex gMutex;
ANativeWindow* gWindow = nullptr;
int gWidth = 0;
int gHeight = 0;
linkroom::EmulatorSession gSession;
linkroom::LocalLinkSession gLocalLinkSession;
std::thread* gEmulationThread = nullptr;
std::atomic<bool> gStopEmulationThread{false};
std::atomic<bool> gFastForwardEnabled{false};
std::uint32_t gLastLoggedInputMask = 0;

void release_window_locked() {
    if (gWindow != nullptr) {
        ANativeWindow_release(gWindow);
        gWindow = nullptr;
    }
    gWidth = 0;
    gHeight = 0;
}

void render_locked() {
    if (gSession.hasLoadedRom()) {
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "skip placeholder: ROM frame renderer is active");
    } else if (gWindow != nullptr && gWidth > 0 && gHeight > 0) {
        render_placeholder_frame(gWindow, gWidth, gHeight);
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "skip render without active surface");
    }
}

void emulation_thread_main() {
    __android_log_print(ANDROID_LOG_INFO, kTag, "emulation thread started");
    auto nextFrame = std::chrono::steady_clock::now();
    int renderedFrames = 0;
    bool appliedFastForwardEnabled = false;
    int pacingLogCountdown = 0;

    while (!gStopEmulationThread.load()) {
        bool rendered = false;
        const bool requestedFastForward = gFastForwardEnabled.load(std::memory_order_relaxed);
        {
            std::lock_guard<std::mutex> lock(gMutex);
            if (!gStopEmulationThread.load() && gSession.hasLoadedRom() && !gSession.isPaused()) {
                if (requestedFastForward != appliedFastForwardEnabled) {
                    gSession.setFastForward(requestedFastForward);
                    appliedFastForwardEnabled = requestedFastForward;
                    __android_log_print(
                        ANDROID_LOG_INFO,
                        kTag,
                        "applied fast-forward on emulation thread: enabled=%s speed=2x",
                        appliedFastForwardEnabled ? "true" : "false"
                    );
                }

                const int framesToRun = appliedFastForwardEnabled ? 2 : 1;
                bool advanced = false;
                for (int frame = 0; frame < framesToRun && !gStopEmulationThread.load(); ++frame) {
                    advanced = gSession.runFrame() || advanced;
                }
                if (advanced && gWindow != nullptr && gWidth > 0 && gHeight > 0) {
                    rendered = gSession.renderFrameToWindow(gWindow, gWidth, gHeight);
                    if (rendered && renderedFrames < 5) {
                        __android_log_print(ANDROID_LOG_INFO, kTag, "rendered mGBA video frame %d", renderedFrames + 1);
                    }
                    if (rendered) {
                        ++renderedFrames;
                    }
                }
                if (appliedFastForwardEnabled && ++pacingLogCountdown >= 180) {
                    __android_log_print(
                        ANDROID_LOG_DEBUG,
                        kTag,
                        "fast-forward pacing: framesPerTick=%d latestFrameRendered=%s",
                        framesToRun,
                        rendered ? "true" : "false"
                    );
                    pacingLogCountdown = 0;
                }
            }
        }

        if (rendered) {
            nextFrame += kFrameInterval;
            std::this_thread::sleep_until(nextFrame);
            if (std::chrono::steady_clock::now() > nextFrame + kFrameInterval) {
                nextFrame = std::chrono::steady_clock::now();
            }
        } else {
            nextFrame = std::chrono::steady_clock::now() + kFrameInterval;
            std::this_thread::sleep_for(std::chrono::milliseconds(16));
        }
    }

    __android_log_print(ANDROID_LOG_INFO, kTag, "emulation thread stopped");
}

void stop_emulation_thread() {
    gStopEmulationThread.store(true);
    if (gEmulationThread != nullptr) {
        if (gEmulationThread->joinable()) {
            gEmulationThread->join();
        }
        delete gEmulationThread;
        gEmulationThread = nullptr;
    }
}

void start_emulation_thread() {
    stop_emulation_thread();
    gStopEmulationThread.store(false);
    gEmulationThread = new std::thread(emulation_thread_main);
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeAttachSurface(
    JNIEnv* env,
    jobject,
    jobject surface
) {
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "attach surface");

    release_window_locked();
    if (surface == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "attach ignored: null surface");
        return;
    }

    gWindow = ANativeWindow_fromSurface(env, surface);
    if (gWindow != nullptr) {
        gWidth = ANativeWindow_getWidth(gWindow);
        gHeight = ANativeWindow_getHeight(gWindow);
    } else {
        __android_log_print(ANDROID_LOG_WARN, kTag, "attach failed: ANativeWindow unavailable");
    }
    render_locked();
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeResize(
    JNIEnv*,
    jobject,
    jint width,
    jint height
) {
    std::lock_guard<std::mutex> lock(gMutex);
    if (width <= 0 || height <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "resize ignored: %d x %d", width, height);
        return;
    }

    gWidth = width;
    gHeight = height;
    __android_log_print(ANDROID_LOG_INFO, kTag, "resize surface: %d x %d", width, height);
    render_locked();
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeDetachSurface(
    JNIEnv*,
    jobject
) {
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "detach surface");
    release_window_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeLoadRom(
    JNIEnv* env,
    jobject,
    jstring rom_path,
    jstring game_root_path
) {
    if (rom_path == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "load ROM ignored: null private path");
        return env->NewStringUTF("unexpected native error: missing copied ROM path");
    }
    if (game_root_path == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "load ROM ignored: null game root path");
        return env->NewStringUTF("unexpected native error: missing game save path");
    }

    const char* chars = env->GetStringUTFChars(rom_path, nullptr);
    std::string path = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(rom_path, chars);
    }
    const char* gameRootChars = env->GetStringUTFChars(game_root_path, nullptr);
    std::string gameRootPath = gameRootChars != nullptr ? gameRootChars : "";
    if (gameRootChars != nullptr) {
        env->ReleaseStringUTFChars(game_root_path, gameRootChars);
    }

    if (path.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "load ROM ignored: empty private path");
        return env->NewStringUTF("file not found: copied ROM path is empty");
    }
    if (gameRootPath.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "load ROM ignored: empty game root path");
        return env->NewStringUTF("unexpected native error: game save path is empty");
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load ROM from private path: %s",
        path.c_str()
    );

    stop_emulation_thread();

    linkroom::RomLoadResult result;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        result = gSession.loadRom(path, gameRootPath);
        __android_log_print(ANDROID_LOG_INFO, kTag, "load ROM result: %s", result.message.c_str());
        render_locked();
    }

    if (result.isSuccess()) {
        start_emulation_thread();
    }
    return env->NewStringUTF(result.message.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativePause(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "pause runtime");
    gSession.pause();
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", gSession.saveStatus().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeResume(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "resume runtime");
    gSession.resume();
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeSetInputMask(JNIEnv*, jobject, jint input_mask) {
    const auto mask = static_cast<std::uint32_t>(input_mask);
    std::lock_guard<std::mutex> lock(gMutex);
    gSession.setInputMask(mask);
    if (mask != gLastLoggedInputMask) {
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "input mask changed: 0x%03x", mask);
        gLastLoggedInputMask = mask;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeSetFastForward(
    JNIEnv*,
    jobject,
    jboolean enabled
) {
    gFastForwardEnabled.store(enabled == JNI_TRUE, std::memory_order_relaxed);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "fast-forward request stored: enabled=%s speed=2x audioMuted=%s",
        enabled == JNI_TRUE ? "true" : "false",
        enabled == JNI_TRUE ? "true" : "false"
    );
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeRelease(JNIEnv*, jobject) {
    stop_emulation_thread();
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "release runtime");
    gFastForwardEnabled.store(false, std::memory_order_relaxed);
    gSession.release();
    release_window_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetSaveStatus(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    const std::string status = gSession.saveStatus();
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeSaveState(
    JNIEnv* env,
    jobject,
    jint slot,
    jstring state_path
) {
    if (state_path == nullptr) {
        return env->NewStringUTF("state save failed: missing state path");
    }

    const char* chars = env->GetStringUTFChars(state_path, nullptr);
    std::string path = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(state_path, chars);
    }

    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "save state request: slot=%d path=%s", slot, path.c_str());
    const std::string status = gSession.saveState(slot, path);
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", status.c_str());
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeLoadState(
    JNIEnv* env,
    jobject,
    jint slot,
    jstring state_path
) {
    if (state_path == nullptr) {
        return env->NewStringUTF("state load failed: missing state path");
    }

    const char* chars = env->GetStringUTFChars(state_path, nullptr);
    std::string path = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(state_path, chars);
    }

    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "load state request: slot=%d path=%s", slot, path.c_str());
    const std::string status = gSession.loadState(slot, path);
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", status.c_str());
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetAudioStatus(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    const std::string status = gSession.audioStatus();
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeReadAudio(
    JNIEnv* env,
    jobject,
    jshortArray buffer,
    jint max_samples
) {
    if (buffer == nullptr || max_samples <= 0) {
        return 0;
    }

    const jsize bufferLength = env->GetArrayLength(buffer);
    const jint requestedSamples = std::min(max_samples, static_cast<jint>(bufferLength));
    if (requestedSamples <= 0) {
        return 0;
    }

    jshort* samples = env->GetShortArrayElements(buffer, nullptr);
    if (samples == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "audio read failed: JNI short array unavailable");
        return 0;
    }

    jint samplesRead = 0;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        samplesRead = gSession.readAudio(reinterpret_cast<std::int16_t*>(samples), requestedSamples);
    }
    env->ReleaseShortArrayElements(buffer, samples, 0);
    return samplesRead;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetRuntimeStatus(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    const std::string status = gSession.statusMessage();
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetFastForwardStatus(JNIEnv* env, jobject) {
    const bool enabled = gFastForwardEnabled.load(std::memory_order_relaxed);
    const std::string status = enabled
        ? "fast-forward: on (2x, audio muted)"
        : "fast-forward: off";
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetCoreStatus(JNIEnv* env, jobject) {
    linkroom::MgbaCoreAdapter adapter;
    const std::string status = adapter.linkedCoreStatus();
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", status.c_str());
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeRunLocalLinkSmokeTest(JNIEnv* env, jobject) {
    linkroom::MgbaCoreAdapter adapter;
    const std::string status = adapter.localLinkSmokeStatus();
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", status.c_str());
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeStartLocalLinkTest(
    JNIEnv* env,
    jobject,
    jstring primary_rom_path,
    jstring secondary_rom_path,
    jstring base_test_dir
) {
    if (primary_rom_path == nullptr || secondary_rom_path == nullptr || base_test_dir == nullptr) {
        return env->NewStringUTF("local link failed: missing start parameter");
    }

    const char* primaryChars = env->GetStringUTFChars(primary_rom_path, nullptr);
    const char* secondaryChars = env->GetStringUTFChars(secondary_rom_path, nullptr);
    const char* baseChars = env->GetStringUTFChars(base_test_dir, nullptr);
    std::string primary = primaryChars != nullptr ? primaryChars : "";
    std::string secondary = secondaryChars != nullptr ? secondaryChars : "";
    std::string base = baseChars != nullptr ? baseChars : "";
    if (primaryChars != nullptr) {
        env->ReleaseStringUTFChars(primary_rom_path, primaryChars);
    }
    if (secondaryChars != nullptr) {
        env->ReleaseStringUTFChars(secondary_rom_path, secondaryChars);
    }
    if (baseChars != nullptr) {
        env->ReleaseStringUTFChars(base_test_dir, baseChars);
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "hidden local link start requested: primary=%s secondary=%s base=%s",
        primary.c_str(),
        secondary.c_str(),
        base.c_str()
    );
    const std::string status = gLocalLinkSession.start(primary, secondary, base);
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeStopLocalLinkTest(JNIEnv*, jobject) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "hidden local link stop requested");
    gLocalLinkSession.stop();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetLocalLinkStatus(JNIEnv* env, jobject) {
    const std::string status = gLocalLinkSession.status();
    return env->NewStringUTF(status.c_str());
}
