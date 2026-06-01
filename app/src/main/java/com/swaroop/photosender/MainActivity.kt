package com.swaroop.photosender

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.swaroop.photosender.ui.theme.PhotoSenderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Start the 15-minute background auto-update
        WidgetUpdateWorker.schedulePeriodicUpdate(this)

        val savedId = UserPrefs.getMyId(this)
        if (!savedId.isNullOrEmpty()) saveFCMToken(savedId)

        setContent {
            PhotoSenderTheme {
                // State to track if we have a user ID
                var myId by remember { mutableStateOf(savedId) }

                if (myId.isNullOrEmpty()) {
                    // Show SetupScreen if no user is logged in
                    SetupScreen { chosenName ->
                        UserPrefs.saveMyId(this@MainActivity, chosenName)
                        myId = chosenName
                        saveFCMToken(chosenName)
                    }
                } else {
                    // Show the app interface if we have an ID
                    MainScreen(myId = myId!!)
                }
            }
        }
    }

    private fun saveFCMToken(username: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Firebase.firestore.collection("tokens").document(username)
                    .set(hashMapOf("fcmToken" to token))
            }
        }
    }
}