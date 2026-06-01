package com.swaroop.photosender

import android.content.Context

object UserPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_MY_ID = "my_id"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"

    // ─── ID Management ───────────────────────────────────────────────────────

    fun getMyId(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MY_ID, null)
    }

    fun saveMyId(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MY_ID, id).apply()
    }

    fun getPartnerId(myId: String): String {
        return if (myId == "swaroop") "vidhi" else "swaroop"
    }

    // ─── Settings Management ─────────────────────────────────────────────────

    fun setNotificationsEnabled(context: Context, isEnabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_NOTIFICATIONS, isEnabled).apply()
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        // Defaults to true, so notifications work out-of-the-box
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS, true)
    }
}