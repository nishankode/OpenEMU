package com.linkroom.app.runtime

import android.util.Log
import android.view.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EmulatorRuntime {
    private val released = AtomicBoolean(false)
    private val surfaceAttached = AtomicBoolean(false)

    val isNativeAvailable: Boolean
        get() = NativeEmulatorBridge.isAvailable

    val nativeStatusMessage: String
        get() = NativeEmulatorBridge.unavailableMessage
            ?: "Native SurfaceView renderer active."

    val coreStatusMessage: String
        get() = NativeEmulatorBridge.getCoreStatus()

    val runtimeStatusMessage: String
        get() = NativeEmulatorBridge.getRuntimeStatus()

    val saveStatusMessage: String
        get() = NativeEmulatorBridge.getSaveStatus()

    fun attachSurface(surface: Surface): Boolean {
        if (released.get()) {
            Log.w(TAG, "Ignoring surface attach after runtime release.")
            return false
        }

        val attached = NativeEmulatorBridge.attachSurface(surface)
        surfaceAttached.set(attached)
        return attached
    }

    fun resize(width: Int, height: Int) {
        if (!released.get() && surfaceAttached.get()) {
            NativeEmulatorBridge.resize(width, height)
        }
    }

    fun detachSurface() {
        if (surfaceAttached.getAndSet(false)) {
            NativeEmulatorBridge.detachSurface()
        }
    }

    fun loadRom(localRomPath: String, gameRootPath: String): String {
        return if (!released.get()) {
            val batteryDirectory = File(gameRootPath, "battery")
            val batterySaveFile = File(batteryDirectory, "current.sav")
            val created = batteryDirectory.exists() || batteryDirectory.mkdirs()
            Log.i(TAG, "Expected save directory: ${batteryDirectory.absolutePath}; createdOrExists=$created")
            Log.i(TAG, "Expected save file path: ${batterySaveFile.absolutePath}; existsBeforeBoot=${batterySaveFile.exists()}; sizeBeforeBoot=${batterySaveFile.length()}")
            NativeEmulatorBridge.loadRom(localRomPath, gameRootPath)
        } else {
            "released: emulator runtime was already released"
        }
    }

    fun resume(): String {
        if (!released.get()) {
            NativeEmulatorBridge.resume()
            return NativeEmulatorBridge.getRuntimeStatus()
        }
        return "released: emulator runtime was already released"
    }

    fun pause(): String {
        if (!released.get()) {
            NativeEmulatorBridge.pause()
            return NativeEmulatorBridge.getRuntimeStatus()
        }
        return "released: emulator runtime was already released"
    }

    fun setInputMask(inputMask: Int) {
        if (!released.get()) {
            NativeEmulatorBridge.setInputMask(inputMask)
        }
    }

    fun release() {
        if (released.compareAndSet(false, true)) {
            NativeEmulatorBridge.setInputMask(0)
            surfaceAttached.set(false)
            NativeEmulatorBridge.release()
        }
    }

    private companion object {
        const val TAG = "EmulatorRuntime"
    }
}
