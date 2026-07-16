package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class PointlyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Try default initialization first (from google-services.json)
            val app = FirebaseApp.initializeApp(this)
            if (app == null) {
                Log.w("PointlyApplication", "Default FirebaseApp initialization returned null. Using programmatic options.")
                initializeFallback()
            } else {
                Log.d("PointlyApplication", "FirebaseApp initialized successfully with default options.")
            }
        } catch (e: Exception) {
            Log.w("PointlyApplication", "Default FirebaseApp initialization failed: ${e.message}. Using programmatic options.")
            initializeFallback()
        }
    }

    private fun initializeFallback() {
        try {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAItra9SH3n6JoD-fX9_iBargRh7_eoej4")
                .setApplicationId("1:887869103231:android:be8c2fdbee73bfc2ecf34a")
                .setProjectId("pointly77")
                .setDatabaseUrl("https://pointly77-default-rtdb.firebaseio.com")
                .setStorageBucket("pointly77.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
            Log.d("PointlyApplication", "FirebaseApp initialized successfully with user provided options.")
        } catch (fallbackEx: Exception) {
            Log.e("PointlyApplication", "User FirebaseApp initialization failed", fallbackEx)
        }
    }
}
