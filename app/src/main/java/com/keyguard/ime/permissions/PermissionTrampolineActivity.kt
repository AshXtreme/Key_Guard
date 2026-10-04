package com.keyguard.ime.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionTrampolineActivity
 *
 * Lightweight, translucent Activity designed to resolve the Android InputMethodService
 * permission catch-22. An InputMethodService cannot directly trigger requestPermissions(),
 * as it does not possess an Activity window token.
 *
 * Invariants & Guarantees:
 *  1. Zero Animation & Instant Return: Launches without transition animations, requests
 *     Manifest.permission.RECORD_AUDIO, broadcasts the outcome, and finishes immediately.
 *  2. Translucent Chrome: Avoids displacing or restarting the underlying IME service or app window.
 *  3. Air-Gap Integrity: Strictly requests RECORD_AUDIO for local on-device neural dictation
 *     without requiring or including INTERNET permissions.
 */
class PermissionTrampolineActivity : Activity() {

    companion object {
        private const val TAG = "PermissionTrampoline"
        const val ACTION_RECORD_AUDIO_RESULT = "com.keyguard.ime.action.RECORD_AUDIO_RESULT"
        const val EXTRA_IS_GRANTED = "extra_is_granted"
        const val REQUEST_CODE_RECORD_AUDIO = 1001

        /**
         * Launches the trampoline from an InputMethodService context with FLAG_ACTIVITY_NEW_TASK.
         */
        fun launch(context: Context) {
            val intent = Intent(context, PermissionTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableWindowAnimations()

        // Configure window to be completely non-intrusive
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Check if permission is already granted
        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            Log.i(TAG, "RECORD_AUDIO permission already granted. Broadcasting success.")
            broadcastResult(isGranted = true)
            finish()
            return
        }

        Log.i(TAG, "Requesting RECORD_AUDIO runtime permission from trampoline Activity.")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            val isGranted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED

            Log.i(TAG, "RECORD_AUDIO request result: isGranted=$isGranted")
            broadcastResult(isGranted)
            finish()
        }
    }

    private fun broadcastResult(isGranted: Boolean) {
        val resultIntent = Intent(ACTION_RECORD_AUDIO_RESULT).apply {
            setPackage(packageName)
            putExtra(EXTRA_IS_GRANTED, isGranted)
        }
        sendBroadcast(resultIntent)
    }

    override fun finish() {
        super.finish()
        disableWindowAnimations()
    }

    private fun disableWindowAnimations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
