package app.pulse.monitor.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.pulse.monitor.R

/**
 * Helper class for handling notification permissions on Android 13+ (API 33)
 * Provides proper permission request flow with user-friendly rationale
 */
class NotificationPermissionHelper(
    private val activity: FragmentActivity,
    private val onPermissionResult: (Boolean) -> Unit
) {

    private var permissionLauncher: ActivityResultLauncher<String>? = null
    private var shouldShowRationale = false

    init {
        setupPermissionLauncher()
    }

    private fun setupPermissionLauncher() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            handlePermissionResult(isGranted)
        }
    }

    /**
     * Check if notification permission is required and granted
     */
    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Notification permission not required for Android < 13
            true
        }
    }

    /**
     * Request notification permission with proper rationale handling
     */
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Permission not required for Android < 13
            onPermissionResult(true)
            return
        }

        when {
            isNotificationPermissionGranted() -> {
                onPermissionResult(true)
            }
            shouldShowRequestPermissionRationale() -> {
                showPermissionRationale()
            }
            else -> {
                requestPermission()
            }
        }
    }

    private fun shouldShowRequestPermissionRationale(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            false
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.grant_permission) { _, _ ->
                requestPermission()
            }
            .setNegativeButton(R.string.not_now) { dialog, _ ->
                dialog.dismiss()
                onPermissionResult(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (!isGranted && shouldShowRequestPermissionRationale()) {
            // User denied but can still be asked again
            showPermissionDeniedDialog()
        } else if (!isGranted) {
            // User denied with "Don't ask again" or permanently denied
            showPermissionPermanentlyDeniedDialog()
        } else {
            onPermissionResult(true)
        }
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_denied_title)
            .setMessage(R.string.notification_permission_denied_message)
            .setPositiveButton(R.string.try_again) { _, _ ->
                requestNotificationPermission()
            }
            .setNegativeButton(R.string.continue_without_notifications) { _, _ ->
                onPermissionResult(false)
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionPermanentlyDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.permission_required_title)
            .setMessage(R.string.notification_permission_permanently_denied_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                Utils.openAppSettings(activity)
                onPermissionResult(false)
            }
            .setNegativeButton(R.string.continue_without_notifications) { _, _ ->
                onPermissionResult(false)
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        /**
         * Quick check for notification permission without creating helper instance
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }
}
