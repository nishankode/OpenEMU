package com.linkroom.app.feature.emulator

import android.graphics.SurfaceTexture
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.linkroom.app.runtime.EmulatorRuntime
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "EmulatorSurface"

@Composable
fun EmulatorSurface(
    runtime: EmulatorRuntime,
    modifier: Modifier = Modifier
) {
    val textureViewRef = remember { AtomicReference<TextureView?>(null) }
    val attachedSurfaceRef = remember { AtomicReference<Surface?>(null) }
    val listener = remember(runtime) {
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                Log.i(TAG, "Texture surface available: $width x $height")
                val surface = Surface(surfaceTexture)
                attachedSurfaceRef.getAndSet(surface)?.release()
                runtime.attachSurface(surface)
                runtime.resize(width, height)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                Log.i(TAG, "Texture surface changed: $width x $height")
                runtime.resize(width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                Log.i(TAG, "Texture surface destroyed.")
                runtime.detachSurface()
                attachedSurfaceRef.getAndSet(null)?.release()
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = listener
                textureViewRef.set(this)
            }
        }
    )

    DisposableEffect(listener, runtime) {
        onDispose {
            textureViewRef.getAndSet(null)?.surfaceTextureListener = null
            runtime.detachSurface()
            attachedSurfaceRef.getAndSet(null)?.release()
        }
    }
}
