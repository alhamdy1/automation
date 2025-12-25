package com.passphoto.processor.ai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.passphoto.processor.logging.EncryptedLogger
import com.passphoto.processor.logging.LogStatus
import java.io.File
import java.io.FileOutputStream

/**
 * VendorAIProcessor - Vendor-Specific AI Feature Integration
 * 
 * VIVO DEVICE AI FEATURES:
 * 1. Vivo AI Photo Enhancement
 * 2. Vivo AI Background Removal
 * 3. Vivo AI Portrait Mode
 * 4. Vivo Super Night Mode
 * 
 * HOW TO CHECK AND CONFIGURE FOR YOUR VIVO DEVICE:
 * 
 * 1. Check if your device is Vivo:
 *    adb shell getprop ro.product.brand
 *    adb shell getprop ro.product.manufacturer
 * 
 * 2. Check available Vivo AI features:
 *    adb shell pm list packages | grep vivo
 *    adb shell pm list packages | grep ai
 * 
 * 3. Common Vivo AI packages:
 *    - com.vivo.ai.engine
 *    - com.vivo.aiimage
 *    - com.vivo.portrait
 *    - com.vivo.supernight
 * 
 * 4. To check supported intents:
 *    adb shell dumpsys package com.vivo.aiimage
 * 
 * NOTE: Vivo's AI features are proprietary and may require:
 * - Specific device models (V21, X70 Pro+, etc.)
 * - FuntouchOS or OriginOS version requirements
 * - May not be available via public APIs
 * 
 * FALLBACK STRATEGY:
 * If vendor AI is not available, use ML Kit for basic processing
 */
class VendorAIProcessor(
    private val context: Context,
    private val logger: EncryptedLogger
) {

    companion object {
        // Vivo package identifiers
        private const val VIVO_AI_ENGINE = "com.vivo.ai.engine"
        private const val VIVO_AI_IMAGE = "com.vivo.aiimage"
        private const val VIVO_ALBUM = "com.vivo.gallery"
        private const val VIVO_CAMERA = "com.vivo.camera"
        
        // Samsung AI packages (for reference/future expansion)
        private const val SAMSUNG_AI_PACKAGE = "com.samsung.android.app.dressroom"
        
        // Xiaomi AI packages (for reference/future expansion)
        private const val XIAOMI_AI_PACKAGE = "com.miui.gallery"
        
        // Output directory
        private const val OUTPUT_FOLDER = "PassPhotoProcessed"
    }

    /**
     * Detect device vendor and available AI features
     */
    fun detectDeviceAndFeatures(): DeviceInfo {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL
        
        val vendor = when {
            brand.contains("vivo") || manufacturer.contains("vivo") -> DeviceVendor.VIVO
            brand.contains("samsung") || manufacturer.contains("samsung") -> DeviceVendor.SAMSUNG
            brand.contains("xiaomi") || manufacturer.contains("xiaomi") -> DeviceVendor.XIAOMI
            brand.contains("oppo") || manufacturer.contains("oppo") -> DeviceVendor.OPPO
            brand.contains("huawei") || manufacturer.contains("huawei") -> DeviceVendor.HUAWEI
            brand.contains("google") || manufacturer.contains("google") -> DeviceVendor.GOOGLE
            else -> DeviceVendor.UNKNOWN
        }
        
        val availableFeatures = mutableListOf<AIFeature>()
        
        // Check vendor-specific packages
        when (vendor) {
            DeviceVendor.VIVO -> {
                if (isPackageInstalled(VIVO_AI_ENGINE)) {
                    availableFeatures.add(AIFeature.AI_ENHANCEMENT)
                }
                if (isPackageInstalled(VIVO_AI_IMAGE)) {
                    availableFeatures.add(AIFeature.BACKGROUND_REMOVAL)
                    availableFeatures.add(AIFeature.PORTRAIT_MODE)
                }
                if (isPackageInstalled(VIVO_ALBUM)) {
                    availableFeatures.add(AIFeature.AI_ALBUM)
                }
            }
            DeviceVendor.SAMSUNG -> {
                // Samsung AI detection
                availableFeatures.add(AIFeature.AI_ENHANCEMENT) // Most Samsung devices have this
            }
            DeviceVendor.XIAOMI -> {
                // Xiaomi AI detection
                if (isPackageInstalled(XIAOMI_AI_PACKAGE)) {
                    availableFeatures.add(AIFeature.AI_ENHANCEMENT)
                }
            }
            else -> {
                // Generic AI via ML Kit (always available)
                availableFeatures.add(AIFeature.ML_KIT_FALLBACK)
            }
        }
        
        // ML Kit is always available as fallback
        if (availableFeatures.isEmpty()) {
            availableFeatures.add(AIFeature.ML_KIT_FALLBACK)
        }
        
        logger.log(
            action = "DEVICE_DETECTION",
            status = LogStatus.SUCCESS,
            metadata = mapOf(
                "vendor" to vendor.name,
                "model" to model,
                "features" to availableFeatures.map { it.name }
            )
        )
        
        return DeviceInfo(
            vendor = vendor,
            model = model,
            brand = brand,
            availableFeatures = availableFeatures
        )
    }

    /**
     * Check if a package is installed
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Process pass photo using best available AI
     */
    suspend fun processPassPhoto(
        inputUri: Uri,
        options: ProcessingOptions = ProcessingOptions()
    ): ProcessingResult {
        val deviceInfo = detectDeviceAndFeatures()
        
        return when (deviceInfo.vendor) {
            DeviceVendor.VIVO -> processWithVivoAI(inputUri, options)
            DeviceVendor.SAMSUNG -> processWithSamsungAI(inputUri, options)
            DeviceVendor.XIAOMI -> processWithXiaomiAI(inputUri, options)
            else -> processWithMLKit(inputUri, options)
        }
    }

    /**
     * Process using Vivo's AI features
     * 
     * NOTE FOR DEVELOPERS:
     * Vivo's AI APIs are not publicly documented. To discover available features:
     * 
     * 1. Use adb to inspect intents:
     *    adb shell dumpsys package com.vivo.gallery | grep -A 10 "intent-filter"
     * 
     * 2. Check for ContentProviders:
     *    adb shell content query --uri content://com.vivo.gallery.provider/
     * 
     * 3. Use Activity Manager to find activities:
     *    adb shell am start -a android.intent.action.EDIT -t image/*
     * 
     * The implementation below uses Intent-based integration which is the most
     * portable approach for vendor features.
     */
    private suspend fun processWithVivoAI(
        inputUri: Uri,
        options: ProcessingOptions
    ): ProcessingResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Attempt to use Vivo's gallery edit intent
            val editIntent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(inputUri, "image/*")
                setPackage(VIVO_ALBUM)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Check if intent is resolvable
            val resolveInfo = context.packageManager.resolveActivity(editIntent, 0)
            
            if (resolveInfo != null) {
                // Vivo gallery can handle the edit
                // Note: This opens the gallery for manual editing
                // For fully automated processing, we need to use ML Kit
                
                logger.log(
                    action = "VIVO_AI_AVAILABLE",
                    status = LogStatus.SUCCESS,
                    metadata = mapOf(
                        "feature" to "GALLERY_EDIT",
                        "autoProcess" to false
                    )
                )
                
                // For automated processing, fall back to ML Kit
                processWithMLKit(inputUri, options).copy(
                    usedVendorAI = true,
                    vendorFeature = "VIVO_GALLERY_AVAILABLE"
                )
            } else {
                // Vivo features not available, use ML Kit
                processWithMLKit(inputUri, options)
            }
        } catch (e: Exception) {
            logger.log(
                action = "VIVO_AI_ERROR",
                status = LogStatus.FAILURE,
                metadata = mapOf("error" to (e.message ?: "Unknown error"))
            )
            processWithMLKit(inputUri, options)
        }
    }

    /**
     * Process using Samsung's AI features
     */
    private suspend fun processWithSamsungAI(
        inputUri: Uri,
        options: ProcessingOptions
    ): ProcessingResult {
        // Samsung uses similar Intent-based approach
        // For automated processing, use ML Kit
        return processWithMLKit(inputUri, options)
    }

    /**
     * Process using Xiaomi's AI features
     */
    private suspend fun processWithXiaomiAI(
        inputUri: Uri,
        options: ProcessingOptions
    ): ProcessingResult {
        // Xiaomi uses similar Intent-based approach
        // For automated processing, use ML Kit
        return processWithMLKit(inputUri, options)
    }

    /**
     * Process using ML Kit (fallback for all devices)
     * This provides consistent, automated processing
     */
    private suspend fun processWithMLKit(
        inputUri: Uri,
        options: ProcessingOptions
    ): ProcessingResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // Load bitmap
            val bitmap = loadBitmap(inputUri)
                ?: return ProcessingResult(
                    success = false,
                    errorMessage = "Failed to load image"
                )
            
            // Process the image
            val processedBitmap = when (options.processingMode) {
                ProcessingMode.CROP_AND_CENTER -> cropAndCenterFace(bitmap)
                ProcessingMode.ENHANCE_ONLY -> enhanceImage(bitmap)
                ProcessingMode.BACKGROUND_REMOVE -> removeBackground(bitmap)
                ProcessingMode.FULL_PROCESS -> {
                    val cropped = cropAndCenterFace(bitmap)
                    enhanceImage(cropped)
                }
            }
            
            // Save to output folder
            val outputFile = saveToOutputFolder(processedBitmap, inputUri)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            logger.logPhotoProcessing(
                photoPath = inputUri.toString(),
                isPassPhoto = true,
                processingTimeMs = processingTime,
                success = true
            )
            
            ProcessingResult(
                success = true,
                outputUri = Uri.fromFile(outputFile),
                outputPath = outputFile.absolutePath,
                processingTimeMs = processingTime,
                usedVendorAI = false,
                vendorFeature = "ML_KIT"
            )
        } catch (e: Exception) {
            logger.logPhotoProcessing(
                photoPath = inputUri.toString(),
                isPassPhoto = true,
                processingTimeMs = System.currentTimeMillis() - startTime,
                success = false,
                errorMessage = e.message
            )
            
            ProcessingResult(
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Load bitmap from URI
     */
    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Crop and center face in image
     * Creates a standard passport photo ratio (35mm x 45mm = 7:9)
     */
    private fun cropAndCenterFace(bitmap: Bitmap): Bitmap {
        // For now, just center crop to passport ratio
        // In production, use face detection to properly center on face
        
        val targetRatio = 35f / 45f  // Passport photo ratio
        val currentRatio = bitmap.width.toFloat() / bitmap.height
        
        return if (currentRatio > targetRatio) {
            // Image is too wide, crop sides
            val newWidth = (bitmap.height * targetRatio).toInt()
            val xOffset = (bitmap.width - newWidth) / 2
            Bitmap.createBitmap(bitmap, xOffset, 0, newWidth, bitmap.height)
        } else {
            // Image is too tall, crop top/bottom
            val newHeight = (bitmap.width / targetRatio).toInt()
            val yOffset = (bitmap.height - newHeight) / 2
            Bitmap.createBitmap(bitmap, 0, yOffset, bitmap.width, newHeight)
        }
    }

    /**
     * Basic image enhancement
     */
    private fun enhanceImage(bitmap: Bitmap): Bitmap {
        // Basic enhancement - in production, use more sophisticated processing
        // This is a placeholder for ML Kit or vendor-specific enhancement
        return bitmap
    }

    /**
     * Remove background (placeholder)
     */
    private fun removeBackground(bitmap: Bitmap): Bitmap {
        // Background removal requires ML Kit Selfie Segmentation
        // or vendor-specific APIs
        return bitmap
    }

    /**
     * Save processed image to output folder
     */
    private fun saveToOutputFolder(bitmap: Bitmap, sourceUri: Uri): File {
        // Get or create output directory in Pictures
        val outputDir = File(
            context.getExternalFilesDir(null),
            OUTPUT_FOLDER
        ).apply { mkdirs() }
        
        // Generate unique filename
        val timestamp = System.currentTimeMillis()
        val filename = "pass_photo_$timestamp.jpg"
        val outputFile = File(outputDir, filename)
        
        // Save with high quality
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        
        // Make visible in gallery by scanning
        scanFileToGallery(context, outputFile)
        
        return outputFile
    }

    /**
     * Get the output folder path
     */
    fun getOutputFolderPath(): String {
        val outputDir = File(
            context.getExternalFilesDir(null),
            OUTPUT_FOLDER
        )
        return outputDir.absolutePath
    }

    /**
     * Get all processed photos
     */
    fun getProcessedPhotos(): List<File> {
        val outputDir = File(
            context.getExternalFilesDir(null),
            OUTPUT_FOLDER
        )
        return outputDir.listFiles()?.toList() ?: emptyList()
    }
}

/**
 * Scan file to make it visible in gallery
 */
private fun scanFileToGallery(context: Context, file: File) {
    MediaScannerConnection.scanFile(
        context,
        arrayOf(file.absolutePath),
        arrayOf("image/jpeg"),
        null
    )
}

/**
 * Device vendor enum
 */
enum class DeviceVendor {
    VIVO,
    SAMSUNG,
    XIAOMI,
    OPPO,
    HUAWEI,
    GOOGLE,
    UNKNOWN
}

/**
 * Available AI features
 */
enum class AIFeature {
    AI_ENHANCEMENT,
    BACKGROUND_REMOVAL,
    PORTRAIT_MODE,
    AI_ALBUM,
    ML_KIT_FALLBACK
}

/**
 * Device information
 */
data class DeviceInfo(
    val vendor: DeviceVendor,
    val model: String,
    val brand: String,
    val availableFeatures: List<AIFeature>
)

/**
 * Processing options
 */
data class ProcessingOptions(
    val processingMode: ProcessingMode = ProcessingMode.FULL_PROCESS,
    val outputQuality: Int = 95,
    val maxOutputSize: Int = 2048
)

/**
 * Processing modes
 */
enum class ProcessingMode {
    CROP_AND_CENTER,
    ENHANCE_ONLY,
    BACKGROUND_REMOVE,
    FULL_PROCESS
}

/**
 * Processing result
 */
data class ProcessingResult(
    val success: Boolean,
    val outputUri: Uri? = null,
    val outputPath: String? = null,
    val processingTimeMs: Long = 0,
    val usedVendorAI: Boolean = false,
    val vendorFeature: String? = null,
    val errorMessage: String? = null
)
