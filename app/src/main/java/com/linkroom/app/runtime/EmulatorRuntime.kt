package com.linkroom.app.runtime

import android.util.Log
import android.view.Surface
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

    fun loadRom(localRomPath: String): String {
        return if (!released.get()) {
            NativeEmulatorBridge.loadRom(localRomPath)
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

    fun release() {
        if (released.compareAndSet(false, true)) {
            surfaceAttached.set(false)
            NativeEmulatorBridge.release()
        }
    }

    private companion object {
        const val TAG = "EmulatorRuntime"
    }
}
