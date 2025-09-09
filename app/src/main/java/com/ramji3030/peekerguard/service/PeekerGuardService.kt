package com.ramji3030.peekerguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ramji3030.peekerguard.MainActivity
import com.ramji3030.peekerguard.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PeekerGuardService - Core background service for privacy detection
 * Runs continuously to monitor for unauthorized screen viewing using front camera
 */
class PeekerGuardService : Service() {

    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var windowManager: WindowManager
    private var alertView: View? = null
    
    private val isDetectionActive = AtomicBoolean(false)
    private val detectionHandler = Handler()
    private var lastDetectionTime = 0L
    private var detectionCount = 0
    
    // Configuration
    private val detectionIntervalMs = 2000L // Check every 2 seconds
    private val alertDurationMs = 3000L // Show alert for 3 seconds
    private val minDetectionConfidence = 0.7f
    private val maxDetectionsPerMinute = 10
    
    companion object {
        private const val TAG = "PeekerGuardService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "peeker_guard_channel"
        private const val WAKE_LOCK_TAG = "PeekerGuard:WakeLock"
        
        @JvmStatic
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startBackgroundThread()
        acquireWakeLock()
        
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        startDetection()
        
        return START_STICKY // Restart if killed
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        
        stopDetection()
        closeCamera()
        stopBackgroundThread()
        releaseWakeLock()
        removeAlertView()
        
        isServiceRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PeekerGuard Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing privacy monitoring service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PeekerGuard Active")
            .setContentText("Monitoring for unauthorized screen viewing")
            .setSmallIcon(R.drawable.ic_security) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            acquire(10 * 60 * 1000L /* 10 minutes */)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun startDetection() {
        if (isDetectionActive.compareAndSet(false, true)) {
            Log.d(TAG, "Starting detection")
            openCamera()
            scheduleNextDetection()
        }
    }

    private fun stopDetection() {
        if (isDetectionActive.compareAndSet(true, false)) {
            Log.d(TAG, "Stopping detection")
            detectionHandler.removeCallbacksAndMessages(null)
            closeCamera()
        }
    }

    private fun openCamera() {
        try {
            val frontCameraId = getFrontCameraId()
            if (frontCameraId != null) {
                cameraManager.openCamera(frontCameraId, cameraStateCallback, backgroundHandler)
            } else {
                Log.e(TAG, "No front camera found")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error opening camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission not granted", e)
        }
    }

    private fun getFrontCameraId(): String? {
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT) {
                    return cameraId
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error getting camera ID", e)
        }
        return null
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            cameraDevice = camera
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            camera.close()
            cameraDevice = null
        }
    }

    private fun createCameraPreviewSession() {
        try {
            // Create ImageReader for capturing images
            imageReader = ImageReader.newInstance(640, 480, android.graphics.ImageFormat.JPEG, 1)
            
            val outputs = listOf(imageReader!!.surface)
            
            cameraDevice?.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Camera session configured")
                    captureSession = session
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera session configuration failed")
                }
            }, backgroundHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error creating camera preview session", e)
        }
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        
        cameraDevice?.close()
        cameraDevice = null
        
        imageReader?.close()
        imageReader = null
    }

    private fun scheduleNextDetection() {
        if (isDetectionActive.get()) {
            detectionHandler.postDelayed({
                performDetection()
                scheduleNextDetection()
            }, detectionIntervalMs)
        }
    }

    private fun performDetection() {
        if (!isDetectionActive.get() || captureSession == null) {
            return
        }
        
        try {
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder?.addTarget(imageReader!!.surface)
            
            val captureRequest = captureRequestBuilder?.build()
            
            if (captureRequest != null) {
                captureSession?.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: android.hardware.camera2.TotalCaptureResult
                    ) {
                        // Image captured, now analyze it
                        analyzeImageForIntruders()
                    }
                }, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error capturing image", e)
        }
    }

    private fun analyzeImageForIntruders() {
        // Placeholder for actual face detection logic
        // In a real implementation, you would:
        // 1. Process the captured image using ML Kit Face Detection
        // 2. Count the number of faces detected
        // 3. Compare with baseline (user's face)
        // 4. Trigger alert if additional faces detected
        
        // For now, simulate detection logic
        val currentTime = System.currentTimeMillis()
        
        // Simulate random detection for demo purposes
        // In production, replace with actual ML face detection
        val simulatedDetection = (Math.random() < 0.05) // 5% chance of detection
        
        if (simulatedDetection) {
            // Rate limiting
            if (currentTime - lastDetectionTime > 60000) {
                detectionCount = 0
            }
            
            if (detectionCount < maxDetectionsPerMinute) {
                Log.w(TAG, "Potential unauthorized viewing detected!")
                showPrivacyAlert()
                detectionCount++
                lastDetectionTime = currentTime
            }
        }
    }

    private fun showPrivacyAlert() {
        if (alertView != null) {
            return // Alert already showing
        }
        
        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 100 // Offset from top
            }

            alertView = LayoutInflater.from(this).inflate(R.layout.privacy_alert, null)
            alertView?.findViewById<TextView>(R.id.alert_text)?.text = 
                "⚠️ Privacy Alert: Someone may be watching your screen!"
            
            windowManager.addView(alertView, layoutParams)
            
            // Auto-hide after alert duration
            detectionHandler.postDelayed({
                removeAlertView()
            }, alertDurationMs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing privacy alert", e)
        }
    }

    private fun removeAlertView() {
        alertView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing alert view", e)
            }
            alertView = null
        }
    }
}
