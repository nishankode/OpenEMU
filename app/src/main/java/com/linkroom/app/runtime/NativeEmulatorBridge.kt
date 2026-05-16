package com.linkroom.app.runtime

import android.util.Log
import android.view.Surface

object NativeEmulatorBridge {
    private const val TAG = "NativeEmulatorBridge"

    private val loadFailure: Throwable? = runCatching {
        System.loadLibrary("linkroom_native")
    }.exceptionOrNull()?.also { error ->
        Log.e(TAG, "Unable to load native placeholder library.", error)
    }

    val isAvailable: Boolean
        get() = loadFailure == null

    val unavailableMessage: String?
        get() = loadFailure?.let {
            "Native placeholder renderer is unavailable on this device build."
        }

    init {
        if (isAvailable) {
            Log.i(TAG, "Native placeholder library loaded.")
        }
    }

    fun attachSurface(surface: Surface) = callNative("attachSurface") {
        if (surface.isValid) {
            nativeAttachSurface(surface)
        } else {
            Log.w(TAG, "Ignoring attach for invalid surface.")
        }
    }

    fun resize(width: Int, height: Int) = callNative("resize") {
        if (width > 0 && height > 0) {
            nativeResize(width, height)
        } else {
            Log.w(TAG, "Ignoring resize with invalid bounds: $width x $height")
        }
    }

    fun detachSurface() = callNative("detachSurface") {
        nativeDetachSurface()
    }

    fun loadRom(localRomPath: String): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping loadRom because native library is unavailable.")
            return "unexpected native error: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeLoadRom(localRomPath)
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: loadRom", error)
        }.getOrElse { error ->
            "unexpected native error: ${error.javaClass.simpleName} while loading ROM"
        }
    }

    fun pause() = callNative("pause") {
        nativePause()
    }

    fun resume() = callNative("resume") {
        nativeResume()
    }

    fun release() = callNative("release") {
        nativeRelease()
    }

    fun getRuntimeStatus(): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping getRuntimeStatus because native library is unavailable.")
            return "failed: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeGetRuntimeStatus()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: getRuntimeStatus", error)
        }.getOrElse { error ->
            "failed: ${error.javaClass.simpleName} while reading runtime status"
        }
    }

    fun getCoreStatus(): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping getCoreStatus because native library is unavailable.")
            return "mGBA core linked: unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeGetCoreStatus()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: getCoreStatus", error)
        }.getOrElse { error ->
            "mGBA core linked: error (${error.javaClass.simpleName})"
        }
    }

    private inline fun callNative(operation: String, block: () -> Unit): Boolean {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping $operation because native library is unavailable.")
            return false
        }

        return runCatching {
            block()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: $operation", error)
        }.isSuccess
    }

    private external fun nativeAttachSurface(surface: Surface)
    private external fun nativeResize(width: Int, height: Int)
    private external fun nativeDetachSurface()
    private external fun nativeLoadRom(localRomPath: String): String
    private external fun nativePause()
    private external fun nativeResume()
    private external fun nativeRelease()
    private external fun nativeGetRuntimeStatus(): String
    private external fun nativeGetCoreStatus(): String
}
