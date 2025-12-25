package com.passphoto.processor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.passphoto.processor.R
import com.passphoto.processor.logging.EncryptedLogger
import com.passphoto.processor.logging.LogStatus
import com.passphoto.processor.security.TamperDetection
import com.passphoto.processor.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * PhotoMonitorService - Foreground Service for Persistent Monitoring
 * 
 * SERVICE ARCHITECTURE:
 * 1. Foreground service for persistent gallery monitoring
 * 2. Lifecycle-aware using LifecycleService
 * 3. Shows notification to comply with Android requirements
 * 4. Integrates with PhotoMonitorManager for actual monitoring
 * 
 * BATTERY OPTIMIZATION:
 * - Service itself doesn't do polling
 * - Delegates to ContentObserver (event-driven)
 * - Only keeps registration active
 */
class PhotoMonitorService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "photo_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.passphoto.processor.ACTION_STOP"
    }

    private lateinit var monitorManager: PhotoMonitorManager
    private lateinit var logger: EncryptedLogger
    private lateinit var tamperDetection: TamperDetection

    override fun onCreate() {
        super.onCreate()
        
        logger = EncryptedLogger(this)
        tamperDetection = TamperDetection(this)
        monitorManager = PhotoMonitorManager(this)
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Handle stop action
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }
        
        // Security check before starting
        lifecycleScope.launch {
            val securityResult = tamperDetection.performSecurityChecks()
            
            if (!securityResult.isSecure()) {
                logger.logSecurityEvent(
                    "SERVICE_SECURITY_CHECK_FAILED",
                    "Issues: ${securityResult.getSecurityIssues().joinToString()}"
                )
                
                // In production, you might want to stop service on security failure
                // For development, log and continue
            }
            
            startForegroundService()
            startMonitoring()
        }
        
        return START_STICKY
    }

    /**
     * Start as foreground service with notification
     */
    private fun startForegroundService() {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        logger.log(
            action = "SERVICE_STARTED",
            status = LogStatus.SUCCESS
        )
    }

    /**
     * Start photo monitoring
     */
    private fun startMonitoring() {
        monitorManager.startMonitoring()
    }

    /**
     * Stop photo monitoring
     */
    private fun stopMonitoring() {
        monitorManager.stopMonitoring()
        
        logger.log(
            action = "SERVICE_STOPPED",
            status = LogStatus.SUCCESS
        )
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Photo Monitor Service",
                NotificationManager.IMPORTANCE_LOW  // Low importance = no sound
            ).apply {
                description = "Monitors gallery for new pass photos"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create foreground notification
     */
    private fun createNotification(): Notification {
        // Intent to open main activity
        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Intent to stop service
        val stopIntent = Intent(this, PhotoMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pass Photo Processor")
            .setContentText("Monitoring gallery for new photos")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(mainPendingIntent)
            .addAction(
                R.drawable.ic_stop,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
