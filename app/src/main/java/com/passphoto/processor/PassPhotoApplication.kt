package com.passphoto.processor

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import androidx.work.WorkManager
import com.passphoto.processor.logging.EncryptedLogger
import com.passphoto.processor.logging.LogStatus
import com.passphoto.processor.security.TamperDetection

/**
 * PassPhotoApplication - Application Class
 * 
 * Initializes:
 * 1. Security checks at startup
 * 2. WorkManager configuration
 * 3. Logging system
 */
class PassPhotoApplication : Application(), Configuration.Provider {

    lateinit var logger: EncryptedLogger
        private set
    
    lateinit var tamperDetection: TamperDetection
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging first
        logger = EncryptedLogger(this)
        
        // Initialize tamper detection
        tamperDetection = TamperDetection(this)
        
        // Perform startup security check
        performSecurityCheck()
        
        logger.log(
            action = "APPLICATION_STARTED",
            status = LogStatus.SUCCESS,
            metadata = mapOf(
                "version" to BuildConfig.VERSION_NAME,
                "debug" to BuildConfig.DEBUG
            )
        )
    }

    /**
     * Perform security checks at application startup
     */
    private fun performSecurityCheck() {
        val securityResult = tamperDetection.performSecurityChecks()
        
        if (!securityResult.isSecure()) {
            val issues = securityResult.getSecurityIssues()
            
            logger.logSecurityEvent(
                eventType = "STARTUP_SECURITY_CHECK",
                details = "Security issues detected: ${issues.joinToString(", ")}",
                severity = EncryptedLogger.LEVEL_WARNING
            )
            
            // In production, you might want to:
            // 1. Disable certain features
            // 2. Show warning to user
            // 3. Refuse to run on rooted/compromised devices
        } else {
            logger.log(
                action = "SECURITY_CHECK_PASSED",
                status = LogStatus.SUCCESS
            )
        }
    }

    /**
     * WorkManager configuration
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
