package com.ramji3030.peekerguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ramji3030.peekerguard.service.PeekerGuardService

/**
 * MainActivity - Main entry point for PeekerGuard app
 * Handles permission requests, service controls, and settings navigation
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleSwitch: Switch
    private lateinit var settingsButton: Button
    private lateinit var permissionsButton: Button

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            updateUI()
            checkAndRequestOverlayPermission()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupClickListeners()
        updateUI()
        
        // Check and request permissions on startup
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.status_text)
        toggleSwitch = findViewById(R.id.toggle_switch)
        settingsButton = findViewById(R.id.settings_button)
        permissionsButton = findViewById(R.id.permissions_button)
    }

    private fun setupClickListeners() {
        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (hasAllPermissions()) {
                    startPeekerGuardService()
                } else {
                    toggleSwitch.isChecked = false
                    checkPermissions()
                }
            } else {
                stopPeekerGuardService()
            }
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        permissionsButton.setOnClickListener {
            checkPermissions()
        }
    }

    private fun checkPermissions() {
        when {
            !hasCameraPermission() -> {
                requestCameraPermission()
            }
            !hasOverlayPermission() -> {
                checkAndRequestOverlayPermission()
            }
            else -> {
                updateUI()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun hasAllPermissions(): Boolean {
        return hasCameraPermission() && hasOverlayPermission()
    }

    private fun requestCameraPermission() {
        when {
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                showCameraPermissionRationale()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!hasOverlayPermission()) {
            showOverlayPermissionDialog()
        }
    }

    private fun showCameraPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("PeekerGuard needs camera access to detect when someone is looking at your screen. This is essential for the app's privacy protection features.")
            .setPositiveButton("Grant Permission") { _, _ ->
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updateUI()
            }
            .show()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage("PeekerGuard needs permission to display alerts over other apps. This allows the app to notify you immediately when unauthorized viewing is detected.")
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updateUI()
            }
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Denied")
            .setMessage("Camera permission is required for PeekerGuard to function. You can grant this permission in the app settings.")
            .setPositiveButton("Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun startPeekerGuardService() {
        val serviceIntent = Intent(this, PeekerGuardService::class.java)
        startForegroundService(serviceIntent)
        updateUI()
        Toast.makeText(this, "PeekerGuard monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopPeekerGuardService() {
        val serviceIntent = Intent(this, PeekerGuardService::class.java)
        stopService(serviceIntent)
        updateUI()
        Toast.makeText(this, "PeekerGuard monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val isServiceRunning = PeekerGuardService.isServiceRunning
        val hasPermissions = hasAllPermissions()

        // Update status text
        statusText.text = when {
            !hasPermissions -> "Permissions required"
            isServiceRunning -> "Monitoring active - Your privacy is protected"
            else -> "Monitoring inactive - Tap to start protection"
        }

        // Update toggle switch
        toggleSwitch.isChecked = isServiceRunning
        toggleSwitch.isEnabled = hasPermissions

        // Update permissions button
        permissionsButton.isEnabled = !hasPermissions
        permissionsButton.text = if (hasPermissions) "All Permissions Granted" else "Grant Permissions"
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
