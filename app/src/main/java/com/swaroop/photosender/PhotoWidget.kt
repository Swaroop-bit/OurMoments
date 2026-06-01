package com.swaroop.photosender

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File

class PhotoWidget : GlanceAppWidget() {

    companion object {
        val PHOTO_PATH_KEY = stringPreferencesKey("widget_photo_path")
        val PHOTO_BASE64_KEY = stringPreferencesKey("widget_photo_base64")

        suspend fun updateWidget(context: Context, localPath: String) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(PhotoWidget::class.java)
                glanceIds.forEach { id ->
                    updateAppWidgetState(context, id) { prefs ->
                        prefs[PHOTO_PATH_KEY] = localPath
                    }
                    PhotoWidget().update(context, id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        suspend fun updateWidgetWithBase64(context: Context, base64: String) {
            try {
                // Decode and save locally first
                val bytes = java.util.Base64.getDecoder().decode(base64)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val file = File(context.filesDir, "widget_photo.png")
                java.io.FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                updateWidget(context, file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun scheduleWidgetUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WidgetContent()
        }
    }

    @Composable
    fun WidgetContent() {
        val prefs = currentState<Preferences>()
        val photoPath = prefs[PHOTO_PATH_KEY] ?: ""

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFFFF0F5)))
                .cornerRadius(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (photoPath.isNotEmpty()) {
                val file = File(photoPath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(photoPath)
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = "Photo from partner",
                            modifier = GlanceModifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        WidgetPlaceholder()
                    }
                } else {
                    WidgetPlaceholder()
                }
            } else {
                WidgetPlaceholder()
            }
        }
    }

    @Composable
    fun WidgetPlaceholder() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💌", style = TextStyle(fontSize = 32.sp))
            Text(
                "Waiting for a moment...",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ColorProvider(Color(0xFFE8275A))
                )
            )
        }
    }
}

class PhotoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = PhotoWidget()
}