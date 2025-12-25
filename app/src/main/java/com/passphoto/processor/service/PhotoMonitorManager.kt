package com.passphoto.processor.service

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.*
import com.passphoto.processor.ai.PassPhotoDetector
import com.passphoto.processor.ai.ProcessingOptions
import com.passphoto.processor.ai.VendorAIProcessor
import com.passphoto.processor.logging.EncryptedLogger
import com.passphoto.processor.logging.LogStatus
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * PhotoMonitorManager - Battery-Efficient Gallery Monitoring
 * 
 * BATTERY OPTIMIZATION ARCHITECTURE:
 * 1. NO polling loops - uses ContentObserver (event-driven)
 * 2. WorkManager for deferred processing (batching)
 * 3. Doze mode compatible
 * 4. Job scheduling with constraints
 * 
 * HOW IT WORKS:
 * 1. ContentObserver monitors MediaStore for new images
 * 2. When new image detected, schedule WorkManager job
 * 3. WorkManager handles job with battery/network constraints
 * 4. Processing happens in batches during optimal conditions
 * 
 * POWER CONSUMPTION:
 * - Idle: Near-zero (event-driven, no polling)
 * - Processing: Burst mode only when needed
 * - Background: Respects Doze mode and App Standby
 */
class PhotoMonitorManager(private val context: Context) {

    companion object {
        private const val WORK_NAME_IMMEDIATE = "photo_processing_immediate"
        private const val WORK_NAME_PERIODIC = "photo_processing_periodic"
        private const val KEY_PHOTO_URI = "photo_uri"
        private const val DEBOUNCE_DELAY_MS = 2000L  // Debounce rapid changes
    }

    private val logger = EncryptedLogger(context)
    private var contentObserver: PhotoContentObserver? = null
    private val workManager = WorkManager.getInstance(context)
    private val pendingPhotos = mutableSetOf<String>()
    private val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastProcessedTimestamp = 0L

    /**
     * Start monitoring gallery for new photos
     * Uses ContentObserver - no polling, very battery efficient
     */
    fun startMonitoring() {
        if (contentObserver != null) {
            logger.log(
                action = "MONITORING_ALREADY_STARTED",
                status = LogStatus.SKIPPED
            )
            return
        }

        contentObserver = PhotoContentObserver(Handler(Looper.getMainLooper()))
        
        // Register for both internal and external storage
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver!!
        )
        
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.INTERNAL_CONTENT_URI,
            true,
            contentObserver!!
        )

        logger.log(
            action = "MONITORING_STARTED",
            status = LogStatus.SUCCESS,
            metadata = mapOf("method" to "ContentObserver")
        )

        // Also schedule periodic check as backup (very infrequent)
        schedulePeriodicCheck()
    }

    /**
     * Stop monitoring gallery
     */
    fun stopMonitoring() {
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
        
        processingScope.cancel()
        workManager.cancelUniqueWork(WORK_NAME_IMMEDIATE)
        
        logger.log(
            action = "MONITORING_STOPPED",
            status = LogStatus.SUCCESS
        )
    }

    /**
     * ContentObserver for MediaStore changes
     * This is the most battery-efficient way to monitor gallery
     */
    private inner class PhotoContentObserver(handler: Handler) : ContentObserver(handler) {
        
        private var debounceJob: Job? = null
        
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            
            uri?.let { photoUri ->
                // Debounce rapid changes (e.g., burst photos)
                debounceJob?.cancel()
                debounceJob = processingScope.launch {
                    delay(DEBOUNCE_DELAY_MS)
                    handleNewPhoto(photoUri)
                }
            }
        }

        override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
            super.onChange(selfChange, uris, flags)
            
            // Handle batch changes efficiently
            debounceJob?.cancel()
            debounceJob = processingScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                uris.forEach { uri ->
                    handleNewPhoto(uri)
                }
                // Process batch after collecting
                if (pendingPhotos.isNotEmpty()) {
                    scheduleProcessing()
                }
            }
        }
    }

    /**
     * Handle new photo detection
     */
    private suspend fun handleNewPhoto(uri: Uri) {
        // Get actual photo info
        val photoInfo = getPhotoInfo(uri)
        
        photoInfo?.let { info ->
            // Check if it's actually new (not already processed)
            if (info.dateAdded > lastProcessedTimestamp) {
                synchronized(pendingPhotos) {
                    pendingPhotos.add(info.id)
                }
                
                logger.log(
                    action = "NEW_PHOTO_DETECTED",
                    status = LogStatus.SUCCESS,
                    metadata = mapOf(
                        "photoId" to info.id,
                        "timestamp" to info.dateAdded
                    )
                )
                
                // Schedule processing
                scheduleImmediateProcessing(uri)
            }
        }
    }

    /**
     * Get photo information from MediaStore
     */
    private fun getPhotoInfo(uri: Uri): PhotoInfo? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.SIZE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    PhotoInfo(
                        id = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)),
                        dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)),
                        path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)),
                        size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Schedule immediate processing using WorkManager
     * Battery-efficient with constraints
     */
    private fun scheduleImmediateProcessing(photoUri: Uri) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)  // Don't process on low battery
            .build()

        val workRequest = OneTimeWorkRequestBuilder<PhotoProcessingWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_PHOTO_URI to photoUri.toString()))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest
        )
    }

    /**
     * Schedule batch processing for pending photos
     */
    private fun scheduleProcessing() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<BatchPhotoProcessingWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(workRequest)
    }

    /**
     * Schedule periodic check as backup (infrequent, battery-friendly)
     */
    private fun schedulePeriodicCheck() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresCharging(true)  // Only when charging
            .build()

        val workRequest = PeriodicWorkRequestBuilder<PeriodicScanWorker>(
            15, TimeUnit.MINUTES,  // Minimum interval
            5, TimeUnit.MINUTES    // Flex interval
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Query for new photos since last check
     */
    fun queryNewPhotosSince(timestamp: Long): List<Uri> {
        val photos = mutableListOf<Uri>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(timestamp.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                photos.add(uri)
            }
        }
        
        return photos
    }

    /**
     * Update last processed timestamp
     */
    fun updateLastProcessedTimestamp(timestamp: Long) {
        lastProcessedTimestamp = timestamp
    }
}

/**
 * Photo information data class
 */
data class PhotoInfo(
    val id: String,
    val dateAdded: Long,
    val path: String,
    val size: Long
)

/**
 * WorkManager Worker for processing individual photos
 */
class PhotoProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val photoUriString = inputData.getString("photo_uri") ?: return Result.failure()
        val photoUri = Uri.parse(photoUriString)
        
        val logger = EncryptedLogger(applicationContext)
        val passPhotoDetector = PassPhotoDetector(applicationContext, logger)
        val vendorProcessor = VendorAIProcessor(applicationContext, logger)
        
        return try {
            // Step 1: Analyze if it's a pass photo
            val analysisResult = passPhotoDetector.analyzePhoto(photoUri)
            
            if (analysisResult.isPassPhoto) {
                // Step 2: Process with best available AI
                val processingResult = vendorProcessor.processPassPhoto(photoUri)
                
                if (processingResult.success) {
                    logger.logPhotoProcessing(
                        photoPath = photoUri.toString(),
                        isPassPhoto = true,
                        processingTimeMs = analysisResult.processingTimeMs + processingResult.processingTimeMs,
                        success = true
                    )
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                // Not a pass photo, skip processing
                logger.log(
                    action = "PHOTO_SKIPPED",
                    status = LogStatus.SKIPPED,
                    metadata = mapOf(
                        "reason" to analysisResult.reason,
                        "confidence" to analysisResult.confidence
                    )
                )
                Result.success()
            }
        } catch (e: Exception) {
            logger.log(
                action = "PROCESSING_ERROR",
                status = LogStatus.FAILURE,
                metadata = mapOf("error" to (e.message ?: "Unknown"))
            )
            Result.retry()
        } finally {
            passPhotoDetector.close()
        }
    }
}

/**
 * WorkManager Worker for batch processing
 */
class BatchPhotoProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val logger = EncryptedLogger(applicationContext)
        val monitorManager = PhotoMonitorManager(applicationContext)
        val passPhotoDetector = PassPhotoDetector(applicationContext, logger)
        val vendorProcessor = VendorAIProcessor(applicationContext, logger)
        
        return try {
            // Get last processed timestamp from preferences
            val prefs = applicationContext.getSharedPreferences("photo_monitor", Context.MODE_PRIVATE)
            val lastTimestamp = prefs.getLong("last_processed", 0L)
            
            // Query new photos
            val newPhotos = monitorManager.queryNewPhotosSince(lastTimestamp)
            
            var processedCount = 0
            var skippedCount = 0
            
            for (photoUri in newPhotos) {
                val analysisResult = passPhotoDetector.analyzePhoto(photoUri)
                
                if (analysisResult.isPassPhoto) {
                    vendorProcessor.processPassPhoto(photoUri)
                    processedCount++
                } else {
                    skippedCount++
                }
            }
            
            // Update timestamp
            prefs.edit().putLong("last_processed", System.currentTimeMillis() / 1000).apply()
            
            logger.log(
                action = "BATCH_PROCESSING_COMPLETE",
                status = LogStatus.SUCCESS,
                metadata = mapOf(
                    "totalPhotos" to newPhotos.size,
                    "processed" to processedCount,
                    "skipped" to skippedCount
                )
            )
            
            Result.success()
        } catch (e: Exception) {
            logger.log(
                action = "BATCH_PROCESSING_ERROR",
                status = LogStatus.FAILURE,
                metadata = mapOf("error" to (e.message ?: "Unknown"))
            )
            Result.retry()
        } finally {
            passPhotoDetector.close()
        }
    }
}

/**
 * Periodic scan worker (backup, runs infrequently)
 */
class PeriodicScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Delegate to batch processing
        val batchWorker = BatchPhotoProcessingWorker(applicationContext, WorkerParameters::class.java.newInstance() as WorkerParameters)
        return batchWorker.doWork()
    }
}
