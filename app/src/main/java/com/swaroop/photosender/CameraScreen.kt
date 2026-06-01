package com.swaroop.photosender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun CameraScreen(myId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showEmojiDialog by remember { mutableStateOf(false) }

    if (capturedBitmap != null && showEmojiDialog) {
        EmojiReactionDialog(
            myId = myId,
            bitmap = capturedBitmap!!,
            onDiscard = {
                capturedBitmap = null
                showEmojiDialog = false
            },
            onDone = {
                capturedBitmap = null
                showEmojiDialog = false
                onBack()
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageCapture
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Back button
        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("Back")
        }

        // Capture button
        Button(
            onClick = {
                capturePhoto(context, imageCapture) { bitmap ->
                    capturedBitmap = bitmap
                    showEmojiDialog = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(80.dp)
        ) {
            Text("Click", fontSize = 14.sp)
        }
    }
}

fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onCaptured: (Bitmap) -> Unit
) {
    val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                onCaptured(bitmap)
            }
            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}