package com.swaroop.photosender

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

class PhotoViewModel : ViewModel() {

    fun sendPhoto(context: Context, myId: String, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val db = Firebase.firestore

                // Compress + encode to base64
                val baos = ByteArrayOutputStream()
                val scaled = scaleBitmap(bitmap, 500)
                scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
                val base64 = Base64.getEncoder()
                    .encodeToString(baos.toByteArray())

                val photoId = UUID.randomUUID().toString()

                // Save to Firestore
                db.collection("photos").document(photoId).set(
                    mapOf(
                        "base64" to base64,
                        "timestamp" to FieldValue.serverTimestamp(),
                        "sender" to myId,
                        "receiver" to UserPrefs.getPartnerId(myId)
                    )
                ).await()


                // Also trigger a fetch for receiver side
                // (will update when they open app or worker runs)
                WidgetUpdateWorker.updateNow(context)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / width
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }
}