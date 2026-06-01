package com.swaroop.photosender

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.*
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "WidgetWorker"

        fun schedulePeriodicUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "PhotoWidgetUpdate",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun updateNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val myId = UserPrefs.getMyId(context) ?: return Result.failure()

        return try {
            val snapshot = Firebase.firestore.collection("photos")
                .whereEqualTo("receiver", myId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val base64 = snapshot.documents.firstOrNull()?.getString("base64") ?: return Result.failure()
            val bytes = Base64.getDecoder().decode(base64)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return Result.failure()

            val file = File(context.filesDir, "widget_photo.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }

            PhotoWidget.updateWidget(context, file.absolutePath)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget", e)
            Result.retry()
        }
    }
}