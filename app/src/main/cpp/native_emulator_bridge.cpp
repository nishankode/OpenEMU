#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <thread>

#include "emulator_session.h"
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
std::thread* gEmulationThread = nullptr;
std::atomic<bool> gStopEmulationThread{false};
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

    while (!gStopEmulationThread.load()) {
        bool rendered = false;
        {
            std::lock_guard<std::mutex> lock(gMutex);
            if (!gStopEmulationThread.load() && gSession.hasLoadedRom() && !gSession.isPaused()) {
                const bool advanced = gSession.runFrame();
                if (advanced && gWindow != nullptr && gWidth > 0 && gHeight > 0) {
                    rendered = gSession.renderFrameToWindow(gWindow, gWidth, gHeight);
                    if (rendered && renderedFrames < 5) {
                        __android_log_print(ANDROID_LOG_INFO, kTag, "rendered mGBA video frame %d", renderedFrames + 1);
                    }
                    if (rendered) {
                        ++renderedFrames;
                    }
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
    jstring rom_path
) {
    if (rom_path == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "load ROM ignored: null private path");
        return env->NewStringUTF("unexpected native error: missing copied ROM path");
    }

    const char* chars = env->GetStringUTFChars(rom_path, nullptr);
    std::string path = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(rom_path, chars);
    }

    if (path.empty()) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "load ROM ignored: empty private path");
        return env->NewStringUTF("file not found: copied ROM path is empty");
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
        result = gSession.loadRom(path);
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
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeRelease(JNIEnv*, jobject) {
    stop_emulation_thread();
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "release runtime");
    gSession.release();
    release_window_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetRuntimeStatus(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    const std::string status = gSession.statusMessage();
    return env->NewStringUTF(status.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetCoreStatus(JNIEnv* env, jobject) {
    linkroom::MgbaCoreAdapter adapter;
    const std::string status = adapter.linkedCoreStatus();
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", status.c_str());
    return env->NewStringUTF(status.c_str());
}
