#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <mutex>
#include <string>

#include "placeholder_renderer.h"

namespace {
constexpr const char* kTag = "LinkRoomNative";

std::mutex gMutex;
ANativeWindow* gWindow = nullptr;
int gWidth = 0;
int gHeight = 0;

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

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeLoadRom(
    JNIEnv* env,
    jobject,
    jstring uri_string
) {
    if (uri_string == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "load ROM placeholder ignored: null URI");
        return;
    }

    const char* chars = env->GetStringUTFChars(uri_string, nullptr);
    std::string uri = chars != nullptr ? chars : "";
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(uri_string, chars);
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load ROM placeholder: %s",
        uri.c_str()
    );

    // TODO: Replace this stub with emulator core ROM loading through a clean native boundary.
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativePause(JNIEnv*, jobject) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "pause runtime");
    // TODO: Pause emulator core execution when native integration is added.
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeResume(JNIEnv*, jobject) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "resume runtime");
    // TODO: Resume emulator core execution when native integration is added.
}

extern "C" JNIEXPORT void JNICALL
Java_com_linkroom_app_runtime_NativeEmulatorBridge_nativeRelease(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gMutex);
    __android_log_print(ANDROID_LOG_INFO, kTag, "release runtime");
    release_window_locked();
    // TODO: Release emulator core, audio, input, and save resources.
}
