package com.linkroom.app.persistence

import android.content.Context
import android.util.Log

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean {
        val complete = preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        Log.i(TAG, "Restored onboarding completion: $complete")
        return complete
    }

    fun setOnboardingComplete(complete: Boolean) {
        preferences.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, complete)
            .apply()
        Log.i(TAG, "Saved onboarding completion: $complete")
    }

    private companion object {
        const val TAG = "AppPreferences"
        const val PREFS_NAME = "linkroom_app_state"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }
}
