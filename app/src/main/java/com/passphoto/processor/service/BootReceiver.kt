package com.passphoto.processor.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.passphoto.processor.logging.EncryptedLogger
import com.passphoto.processor.logging.LogStatus

/**
 * BootReceiver - Restarts monitoring service after device boot
 * 
 * Ensures continuous protection even after device restart
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val logger = EncryptedLogger(context)
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                logger.log(
                    action = "BOOT_RECEIVED",
                    status = LogStatus.SUCCESS,
                    metadata = mapOf("action" to (intent.action ?: "unknown"))
                )
                
                startMonitoringService(context)
            }
        }
    }

    private fun startMonitoringService(context: Context) {
        val serviceIntent = Intent(context, PhotoMonitorService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
