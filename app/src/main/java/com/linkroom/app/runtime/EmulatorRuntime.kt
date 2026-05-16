package com.linkroom.app.runtime

import android.net.Uri
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
            ?: "Native placeholder renderer active."

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

    fun loadRom(uri: Uri) {
        if (!released.get()) {
            NativeEmulatorBridge.loadRom(uri.toString())
        }
    }

    fun resume() {
        if (!released.get()) {
            NativeEmulatorBridge.resume()
        }
    }

    fun pause() {
        if (!released.get()) {
            NativeEmulatorBridge.pause()
        }
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
