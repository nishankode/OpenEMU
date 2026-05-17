package com.linkroom.app.runtime

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import android.view.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class EmulatorRuntime {
    private val released = AtomicBoolean(false)
    private val surfaceAttached = AtomicBoolean(false)
    private val audioRunning = AtomicBoolean(false)
    private val audioPaused = AtomicBoolean(true)
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

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

    val audioStatusMessage: String
        get() = NativeEmulatorBridge.getAudioStatus()

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
            val result = NativeEmulatorBridge.loadRom(localRomPath, gameRootPath)
            if (result.startsWith("running:")) {
                startAudio()
            } else {
                Log.w(TAG, "Skipping audio start because ROM load did not enter running state: $result")
            }
            result
        } else {
            "released: emulator runtime was already released"
        }
    }

    fun resume(): String {
        if (!released.get()) {
            NativeEmulatorBridge.resume()
            resumeAudio()
            return NativeEmulatorBridge.getRuntimeStatus()
        }
        return "released: emulator runtime was already released"
    }

    fun pause(): String {
        if (!released.get()) {
            pauseAudio()
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
            stopAudio()
            surfaceAttached.set(false)
            NativeEmulatorBridge.release()
        }
    }

    private fun startAudio() {
        if (!audioRunning.compareAndSet(false, true)) {
            resumeAudio()
            return
        }

        val minBufferSize = AudioTrack.getMinBufferSize(
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            Log.w(TAG, "Audio init failed: invalid AudioTrack min buffer size=$minBufferSize")
            audioRunning.set(false)
            return
        }

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.onFailure { error ->
            Log.e(TAG, "Audio init failed.", error)
        }.getOrNull()

        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            Log.w(TAG, "Audio init failed: AudioTrack was not initialized.")
            track?.release()
            audioRunning.set(false)
            return
        }

        audioTrack = track
        audioPaused.set(false)
        Log.i(TAG, "Audio init: sampleRate=$AUDIO_SAMPLE_RATE minBufferBytes=$minBufferSize playbackBufferBytes=${minBufferSize * 2}")

        audioThread = Thread({
            val buffer = ShortArray(AUDIO_PULL_SAMPLES)
            runCatching {
                track.play()
                while (audioRunning.get()) {
                    if (audioPaused.get()) {
                        Thread.sleep(AUDIO_IDLE_SLEEP_MS)
                        continue
                    }
                    val samplesRead = NativeEmulatorBridge.readAudio(buffer, buffer.size)
                    if (samplesRead > 0) {
                        track.write(buffer, 0, samplesRead, AudioTrack.WRITE_BLOCKING)
                    } else {
                        Thread.sleep(AUDIO_UNDERRUN_SLEEP_MS)
                    }
                }
            }.onFailure { error ->
                Log.e(TAG, "Audio playback loop failed; emulator video remains active.", error)
            }
            Log.i(TAG, "Audio playback thread stopped.")
        }, "LinkRoomAudio")
        audioThread?.start()
    }

    private fun pauseAudio() {
        audioPaused.set(true)
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
        }.onFailure { error ->
            Log.w(TAG, "Audio pause failed.", error)
        }
        Log.i(TAG, "Audio paused.")
    }

    private fun resumeAudio() {
        if (!audioRunning.get()) {
            return
        }
        audioPaused.set(false)
        runCatching {
            audioTrack?.play()
        }.onFailure { error ->
            Log.w(TAG, "Audio resume failed.", error)
        }
        Log.i(TAG, "Audio resumed.")
    }

    private fun stopAudio() {
        audioPaused.set(true)
        audioRunning.set(false)
        audioThread?.join(AUDIO_THREAD_JOIN_MS)
        audioThread = null
        runCatching {
            audioTrack?.stop()
            audioTrack?.release()
        }.onFailure { error ->
            Log.w(TAG, "Audio release failed.", error)
        }
        audioTrack = null
        Log.i(TAG, "Audio released.")
    }

    private companion object {
        const val TAG = "EmulatorRuntime"
        const val AUDIO_SAMPLE_RATE = 48_000
        const val AUDIO_PULL_SAMPLES = 4096
        const val AUDIO_IDLE_SLEEP_MS = 20L
        const val AUDIO_UNDERRUN_SLEEP_MS = 8L
        const val AUDIO_THREAD_JOIN_MS = 500L
    }
}
