package com.passphoto.processor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.passphoto.processor.BuildConfig
import com.passphoto.processor.ai.PassPhotoDetector
import com.passphoto.processor.ai.VendorAIProcessor
import com.passphoto.processor.databinding.ActivityMainBinding
import com.passphoto.processor.logging.EncryptedLogger
import com.passphoto.processor.logging.LogStatus
import com.passphoto.processor.security.KeystoreManager
import com.passphoto.processor.security.TamperDetection
import com.passphoto.processor.service.PhotoMonitorService
import kotlinx.coroutines.launch

/**
 * MainActivity - Main User Interface
 * 
 * Features:
 * 1. Start/Stop monitoring service
 * 2. View security status
 * 3. View processing logs
 * 4. Configure settings
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var logger: EncryptedLogger
    private lateinit var tamperDetection: TamperDetection
    private lateinit var keystoreManager: KeystoreManager
    private lateinit var vendorProcessor: VendorAIProcessor
    
    private var isMonitoringActive = false

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startMonitoringService()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeComponents()
        setupUI()
        checkSecurityStatus()
        detectDevice()
    }

    /**
     * Initialize security and logging components
     */
    private fun initializeComponents() {
        logger = EncryptedLogger(this)
        tamperDetection = TamperDetection(this)
        keystoreManager = KeystoreManager(this)
        vendorProcessor = VendorAIProcessor(this, logger)
        
        logger.log(
            action = "MAIN_ACTIVITY_CREATED",
            status = LogStatus.SUCCESS
        )
    }

    /**
     * Setup UI elements and click listeners
     */
    private fun setupUI() {
        binding.apply {
            // App info
            textAppVersion.text = "Version: ${BuildConfig.VERSION_NAME}"
            
            // Start/Stop button
            buttonStartStop.setOnClickListener {
                if (isMonitoringActive) {
                    stopMonitoringService()
                } else {
                    checkPermissionsAndStart()
                }
            }
            
            // View logs button
            buttonViewLogs.setOnClickListener {
                showLogsDialog()
            }
            
            // Security status button
            buttonSecurityStatus.setOnClickListener {
                showSecurityStatusDialog()
            }
            
            // Test detection button (for debugging)
            buttonTestDetection.setOnClickListener {
                testPassPhotoDetection()
            }
        }
        
        updateMonitoringStatus()
    }

    /**
     * Check and display security status
     */
    private fun checkSecurityStatus() {
        lifecycleScope.launch {
            val result = tamperDetection.performSecurityChecks()
            
            binding.textSecurityStatus.text = if (result.isSecure()) {
                "✓ Security: OK"
            } else {
                "⚠ Security: ${result.getSecurityIssues().size} issue(s)"
            }
            
            // Check hardware security
            val hasHardwareSecurity = keystoreManager.isHardwareSecurityAvailable()
            binding.textHardwareSecurity.text = if (hasHardwareSecurity) {
                "✓ Hardware Security: StrongBox Available"
            } else {
                "○ Hardware Security: Software-backed"
            }
        }
    }

    /**
     * Detect device vendor and available AI features
     */
    private fun detectDevice() {
        val deviceInfo = vendorProcessor.detectDeviceAndFeatures()
        
        binding.textDeviceInfo.text = """
            Device: ${deviceInfo.brand} ${deviceInfo.model}
            Vendor: ${deviceInfo.vendor.name}
            AI Features: ${deviceInfo.availableFeatures.joinToString { it.name }}
        """.trimIndent()
    }

    /**
     * Check permissions before starting service
     */
    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        
        // Check media permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isEmpty()) {
            startMonitoringService()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /**
     * Start the monitoring service
     */
    private fun startMonitoringService() {
        val serviceIntent = Intent(this, PhotoMonitorService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        isMonitoringActive = true
        updateMonitoringStatus()
        
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
        
        logger.log(
            action = "MONITORING_STARTED_BY_USER",
            status = LogStatus.SUCCESS
        )
    }

    /**
     * Stop the monitoring service
     */
    private fun stopMonitoringService() {
        val serviceIntent = Intent(this, PhotoMonitorService::class.java)
        stopService(serviceIntent)
        
        isMonitoringActive = false
        updateMonitoringStatus()
        
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
        
        logger.log(
            action = "MONITORING_STOPPED_BY_USER",
            status = LogStatus.SUCCESS
        )
    }

    /**
     * Update UI based on monitoring status
     */
    private fun updateMonitoringStatus() {
        binding.apply {
            buttonStartStop.text = if (isMonitoringActive) "Stop Monitoring" else "Start Monitoring"
            textMonitoringStatus.text = if (isMonitoringActive) {
                "● Monitoring Active"
            } else {
                "○ Monitoring Inactive"
            }
        }
    }

    /**
     * Show permission denied dialog with settings option
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Photo access permission is required to monitor gallery. Please grant permission in settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show logs dialog
     */
    private fun showLogsDialog() {
        lifecycleScope.launch {
            val logs = logger.getLogs().takeLast(50)
            val logText = logs.joinToString("\n\n") { log ->
                "[${log.timestamp}] ${log.level}: ${log.action} - ${log.status}"
            }
            
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Activity Logs (Last 50)")
                .setMessage(logText.ifEmpty { "No logs available" })
                .setPositiveButton("OK", null)
                .setNeutralButton("Clear Logs") { _, _ ->
                    logger.clearLogs()
                    Toast.makeText(this@MainActivity, "Logs cleared", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    /**
     * Show security status dialog
     */
    private fun showSecurityStatusDialog() {
        lifecycleScope.launch {
            val result = tamperDetection.performSecurityChecks()
            
            val statusText = """
                Signature Valid: ${if (result.signatureValid) "✓" else "✗"}
                Rooted Device: ${if (result.isRooted) "✗ Yes" else "✓ No"}
                Debugger Attached: ${if (result.debuggerAttached) "✗ Yes" else "✓ No"}
                Emulator: ${if (result.isEmulator) "✗ Yes" else "✓ No"}
                Hook Framework: ${if (result.hookDetected) "✗ Detected" else "✓ None"}
                Installer Valid: ${if (result.installerValid) "✓" else "✗"}
                Package Valid: ${if (result.packageValid) "✓" else "✗"}
                
                Developer Options: ${if (tamperDetection.isDeveloperOptionsEnabled()) "Enabled" else "Disabled"}
                USB Debugging: ${if (tamperDetection.isUsbDebuggingEnabled()) "Enabled" else "Disabled"}
                Hardware Security: ${if (keystoreManager.isHardwareSecurityAvailable()) "StrongBox" else "Software"}
                
                ${if (result.isSecure()) "✓ All security checks passed" else "⚠ Issues: ${result.getSecurityIssues().joinToString(", ")}"}
            """.trimIndent()
            
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Security Status")
                .setMessage(statusText)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /**
     * Test pass photo detection (for debugging)
     */
    private fun testPassPhotoDetection() {
        Toast.makeText(this, "Select a photo to test detection", Toast.LENGTH_SHORT).show()
        
        // Open photo picker
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        photoPickerLauncher.launch(intent)
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                analyzeSelectedPhoto(uri)
            }
        }
    }

    private fun analyzeSelectedPhoto(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.textTestResult.text = "Analyzing..."
                
                val detector = PassPhotoDetector(this@MainActivity, logger)
                val analysisResult = detector.analyzePhoto(uri)
                detector.close()
                
                val resultText = """
                    Is Pass Photo: ${if (analysisResult.isPassPhoto) "✓ YES" else "✗ NO"}
                    Confidence: ${"%.2f".format(analysisResult.confidence * 100)}%
                    Reason: ${analysisResult.reason}
                    Face Count: ${analysisResult.faceCount}
                    Processing Time: ${analysisResult.processingTimeMs}ms
                    ${analysisResult.faceDetails?.let { 
                        "Face Size: ${"%.1f".format(it.boundingBox.heightRatio * 100)}% of image"
                    } ?: ""}
                """.trimIndent()
                
                binding.textTestResult.text = resultText
                
                // If it's a pass photo, offer to process it
                if (analysisResult.isPassPhoto) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Pass Photo Detected!")
                        .setMessage("Would you like to process this photo?")
                        .setPositiveButton("Process") { _, _ ->
                            processSelectedPhoto(uri)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                binding.textTestResult.text = "Error: ${e.message}"
            }
        }
    }

    private fun processSelectedPhoto(uri: Uri) {
        lifecycleScope.launch {
            try {
                binding.textTestResult.text = "Processing..."
                
                val result = vendorProcessor.processPassPhoto(uri)
                
                if (result.success) {
                    binding.textTestResult.text = """
                        ✓ Processing Complete!
                        Output: ${result.outputPath}
                        Time: ${result.processingTimeMs}ms
                        Vendor AI: ${if (result.usedVendorAI) "Yes (${result.vendorFeature})" else "No (ML Kit)"}
                    """.trimIndent()
                    
                    Toast.makeText(this@MainActivity, "Photo saved to ${result.outputPath}", Toast.LENGTH_LONG).show()
                } else {
                    binding.textTestResult.text = "✗ Processing failed: ${result.errorMessage}"
                }
            } catch (e: Exception) {
                binding.textTestResult.text = "Error: ${e.message}"
            }
        }
    }
}
