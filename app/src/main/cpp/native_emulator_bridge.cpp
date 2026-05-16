#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <mutex>
#include <string>

#include "emulator_session.h"
#include "mgba_core_adapter.h"
#include "placeholder_renderer.h"

namespace {
constexpr const char* kTag = "LinkRoomNative";

std::mutex gMutex;
ANativeWindow* gWindow = nullptr;
int gWidth = 0;
int gHeight = 0;
linkroom::EmulatorSession gSession;

void release_window_locked() {
    if (gWindow != nullptr) {
        ANativeWindow_release(gWindow);
        gWindow = nullptr;
    }
    gWidth = 0;
    gHeight = 0;
}

void render_locked() {
    if (gWindow != nullptr && gWidth > 0 && gHeight > 0) {
        render_placeholder_frame(gWindow, gWidth, gHeight);
    } else {
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "skip render without active surface");
    }
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

    std::lock_guard<std::mutex> lock(gMutex);
    const linkroom::RomLoadResult result = gSession.loadRom(path);
    __android_log_print(ANDROID_LOG_INFO, kTag, "load ROM result: %s", result.message.c_str());
    render_locked();
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
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeRelease(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "release runtime");
    gSession.release();
    release_window_locked();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeGetCoreStatus(JNIEnv* env, jobject) {
    linkroom::MgbaCoreAdapter adapter;
    const std::string status = adapter.linkedCoreStatus();
    __android_log_print(ANDROID_LOG_INFO, kTag, "%s", status.c_str());
    return env->NewStringUTF(status.c_str());
}
