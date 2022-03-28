package ru.vizbash.grapevine.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ru.vizbash.grapevine.R

class LocationHelper(
    private val activity: AppCompatActivity,
) {
    companion object {
        private val locationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    }

    private lateinit var launcher: ActivityResultLauncher<Array<String>>

    private var onPermissionsGranted: () -> Unit = {}

    fun register(onRefused: () -> Unit) {
        launcher = activity.registerForActivityResult(RequestMultiplePermissions()) { perms ->
            val allGranted = locationPermissions.map { perms[it] }.all { it ?: false }
            if (allGranted) {
                onPermissionsGranted()
            } else {
                onRefused()
            }
        }
    }

    fun requestPermissions(cb: () -> Unit) {
        onPermissionsGranted = cb

        if (!askLocationService(activity)) {
            return
        }

        val allGranted = locationPermissions.map {
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
        }.all { it }

        if (allGranted) {
            onPermissionsGranted()
        } else {
            launcher.launch(locationPermissions)
        }
    }

    private fun askLocationService(context: Context): Boolean {
        val locationManager = context.getSystemService(AppCompatActivity.LOCATION_SERVICE)
                as LocationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            AlertDialog.Builder(activity)
                .setMessage(context.getString(R.string.location_alert))
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.enable) { _, _ ->
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .show()
            return false
        } else {
            return true
        }
    }
}