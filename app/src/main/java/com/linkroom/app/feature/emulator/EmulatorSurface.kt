package com.linkroom.app.feature.emulator

import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
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
    val surfaceViewRef = remember { AtomicReference<SurfaceView?>(null) }
    val callback = remember(runtime) {
        object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.i(TAG, "Surface created.")
                runtime.attachSurface(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.i(TAG, "Surface changed: $width x $height")
                runtime.resize(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.i(TAG, "Surface destroyed.")
                runtime.detachSurface()
            }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(callback)
                surfaceViewRef.set(this)
            }
        }
    )

    DisposableEffect(callback, runtime) {
        onDispose {
            surfaceViewRef.getAndSet(null)?.holder?.removeCallback(callback)
            runtime.detachSurface()
        }
    }
}
