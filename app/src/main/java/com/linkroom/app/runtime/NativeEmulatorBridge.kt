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

    fun loadRom(localRomPath: String, gameRootPath: String): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping loadRom because native library is unavailable.")
            return "unexpected native error: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeLoadRom(localRomPath, gameRootPath)
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

    fun setInputMask(inputMask: Int) = callNative("setInputMask") {
        nativeSetInputMask(inputMask)
    }

    fun setFastForward(enabled: Boolean) = callNative("setFastForward") {
        nativeSetFastForward(enabled)
    }

    fun getFastForwardStatus(): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping getFastForwardStatus because native library is unavailable.")
            return "fast-forward unavailable: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeGetFastForwardStatus()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: getFastForwardStatus", error)
        }.getOrElse { error ->
            "fast-forward status unavailable: ${error.javaClass.simpleName}"
        }
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

    fun getSaveStatus(): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping getSaveStatus because native library is unavailable.")
            return "save unavailable: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeGetSaveStatus()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: getSaveStatus", error)
        }.getOrElse { error ->
            "save status unavailable: ${error.javaClass.simpleName}"
        }
    }

    fun saveState(slot: Int, statePath: String): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping saveState because native library is unavailable.")
            return "state save failed: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeSaveState(slot, statePath)
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: saveState", error)
        }.getOrElse { error ->
            "state save failed: ${error.javaClass.simpleName}"
        }
    }

    fun loadState(slot: Int, statePath: String): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping loadState because native library is unavailable.")
            return "state load failed: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeLoadState(slot, statePath)
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: loadState", error)
        }.getOrElse { error ->
            "state load failed: ${error.javaClass.simpleName}"
        }
    }

    fun getAudioStatus(): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping getAudioStatus because native library is unavailable.")
            return "audio unavailable: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeGetAudioStatus()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: getAudioStatus", error)
        }.getOrElse { error ->
            "audio status unavailable: ${error.javaClass.simpleName}"
        }
    }

    fun readAudio(buffer: ShortArray, maxSamples: Int): Int {
        val failure = loadFailure
        if (failure != null || buffer.isEmpty() || maxSamples <= 0) {
            return 0
        }

        return runCatching {
            nativeReadAudio(buffer, maxSamples.coerceAtMost(buffer.size))
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: readAudio", error)
        }.getOrDefault(0)
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

    /**
     * Hidden Phase 1A diagnostic. This is intentionally not wired into normal UI.
     */
    fun runLocalLinkSmokeTest(): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping local link smoke test because native library is unavailable.")
            return "local link smoke: unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeRunLocalLinkSmokeTest()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: runLocalLinkSmokeTest", error)
        }.getOrElse { error ->
            "local link smoke: error (${error.javaClass.simpleName})"
        }
    }

    /**
     * Hidden Phase 1B diagnostic. Not exposed in normal app UX.
     */
    fun startLocalLinkTest(primaryRomPath: String, secondaryRomPath: String, baseTestDir: String): String {
        val failure = loadFailure
        if (failure != null) {
            Log.w(TAG, "Skipping local link test because native library is unavailable.")
            return "local link failed: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeStartLocalLinkTest(primaryRomPath, secondaryRomPath, baseTestDir)
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: startLocalLinkTest", error)
        }.getOrElse { error ->
            "local link failed: ${error.javaClass.simpleName}"
        }
    }

    fun stopLocalLinkTest() = callNative("stopLocalLinkTest") {
        nativeStopLocalLinkTest()
    }

    fun getLocalLinkStatus(): String {
        val failure = loadFailure
        if (failure != null) {
            return "local link unavailable: native library unavailable (${failure.javaClass.simpleName})"
        }

        return runCatching {
            nativeGetLocalLinkStatus()
        }.onFailure { error ->
            Log.e(TAG, "Native operation failed: getLocalLinkStatus", error)
        }.getOrElse { error ->
            "local link status unavailable: ${error.javaClass.simpleName}"
        }
    }

    fun attachLocalLinkSurface(surface: Surface) = callNative("attachLocalLinkSurface") {
        if (surface.isValid) {
            nativeAttachLocalLinkSurface(surface)
        } else {
            Log.w(TAG, "Ignoring local link attach for invalid surface.")
        }
    }

    fun resizeLocalLinkSurface(width: Int, height: Int) = callNative("resizeLocalLinkSurface") {
        if (width > 0 && height > 0) {
            nativeResizeLocalLinkSurface(width, height)
        } else {
            Log.w(TAG, "Ignoring local link resize with invalid bounds: $width x $height")
        }
    }

    fun detachLocalLinkSurface() = callNative("detachLocalLinkSurface") {
        nativeDetachLocalLinkSurface()
    }

    fun setLocalLinkInputMask(slot: Int, inputMask: Int) = callNative("setLocalLinkInputMask") {
        nativeSetLocalLinkInputMask(slot, inputMask)
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
    private external fun nativeLoadRom(localRomPath: String, gameRootPath: String): String
    private external fun nativePause()
    private external fun nativeResume()
    private external fun nativeSetInputMask(inputMask: Int)
    private external fun nativeSetFastForward(enabled: Boolean)
    private external fun nativeRelease()
    private external fun nativeGetRuntimeStatus(): String
    private external fun nativeGetSaveStatus(): String
    private external fun nativeSaveState(slot: Int, statePath: String): String
    private external fun nativeLoadState(slot: Int, statePath: String): String
    private external fun nativeGetAudioStatus(): String
    private external fun nativeReadAudio(buffer: ShortArray, maxSamples: Int): Int
    private external fun nativeGetFastForwardStatus(): String
    private external fun nativeGetCoreStatus(): String
    private external fun nativeRunLocalLinkSmokeTest(): String
    private external fun nativeStartLocalLinkTest(
        primaryRomPath: String,
        secondaryRomPath: String,
        baseTestDir: String
    ): String
    private external fun nativeStopLocalLinkTest()
    private external fun nativeGetLocalLinkStatus(): String
    private external fun nativeAttachLocalLinkSurface(surface: Surface)
    private external fun nativeResizeLocalLinkSurface(width: Int, height: Int)
    private external fun nativeDetachLocalLinkSurface()
    private external fun nativeSetLocalLinkInputMask(slot: Int, inputMask: Int)
}
