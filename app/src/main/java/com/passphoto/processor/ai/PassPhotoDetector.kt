package com.passphoto.processor.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.passphoto.processor.logging.EncryptedLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * PassPhotoDetector - On-Device AI Inference for Pass Photo Detection
 * 
 * AI ARCHITECTURE:
 * 1. ML Kit Face Detection (100% offline, on-device)
 * 2. Multi-criteria classification for pass photo vs regular photo
 * 3. No cloud dependencies - all processing local
 * 
 * CLASSIFICATION CRITERIA FOR PASS PHOTO:
 * - Single face detected
 * - Face centered in image (within tolerance)
 * - Face size ratio appropriate (40-80% of image height)
 * - Head rotation minimal (frontal face)
 * - Eyes open
 * - Neutral expression (no smiling)
 * - Good lighting (even exposure)
 */
class PassPhotoDetector(
    private val context: Context,
    private val logger: EncryptedLogger
) {

    companion object {
        // Pass photo classification thresholds
        private const val MIN_FACE_SIZE_RATIO = 0.35f  // Face should be at least 35% of image
        private const val MAX_FACE_SIZE_RATIO = 0.85f  // Face should be at most 85% of image
        private const val CENTER_TOLERANCE_RATIO = 0.15f  // Face center within 15% of image center
        private const val MAX_HEAD_ROTATION = 15f  // Maximum head rotation in degrees
        private const val MIN_EYE_OPEN_PROBABILITY = 0.7f  // Eyes should be open
        private const val MAX_SMILE_PROBABILITY = 0.3f  // Should not be smiling
        private const val MIN_CONFIDENCE = 0.8f  // Overall detection confidence
    }

    // Configure face detector for high accuracy
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)  // Not needed, saves processing
        .setMinFaceSize(0.1f)  // Detect faces at least 10% of image
        .enableTracking()
        .build()

    private val faceDetector: FaceDetector = FaceDetection.getClient(faceDetectorOptions)

    /**
     * Analyze photo to determine if it's a pass photo
     * @param uri URI of the photo to analyze
     * @return PassPhotoResult with classification details
     */
    suspend fun analyzePhoto(uri: Uri): PassPhotoResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val bitmap = loadBitmap(uri)
                ?: return PassPhotoResult(
                    isPassPhoto = false,
                    confidence = 0f,
                    reason = "Failed to load image"
                )

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = detectFaces(inputImage)
            
            val result = classifyAsPassPhoto(faces, bitmap.width, bitmap.height)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            // Log the inference result
            logger.logAIInference(
                modelName = "MLKit_FaceDetection",
                confidence = result.confidence,
                classification = if (result.isPassPhoto) "PASS_PHOTO" else "NOT_PASS_PHOTO",
                inferenceTimeMs = processingTime
            )
            
            result.copy(processingTimeMs = processingTime)
        } catch (e: Exception) {
            PassPhotoResult(
                isPassPhoto = false,
                confidence = 0f,
                reason = "Analysis error: ${e.message}"
            )
        }
    }

    /**
     * Analyze photo from file path
     */
    suspend fun analyzePhoto(filePath: String): PassPhotoResult {
        return analyzePhoto(Uri.parse("file://$filePath"))
    }

    /**
     * Analyze bitmap directly
     */
    suspend fun analyzePhoto(bitmap: Bitmap): PassPhotoResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = detectFaces(inputImage)
            
            val result = classifyAsPassPhoto(faces, bitmap.width, bitmap.height)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            logger.logAIInference(
                modelName = "MLKit_FaceDetection",
                confidence = result.confidence,
                classification = if (result.isPassPhoto) "PASS_PHOTO" else "NOT_PASS_PHOTO",
                inferenceTimeMs = processingTime
            )
            
            result.copy(processingTimeMs = processingTime)
        } catch (e: Exception) {
            PassPhotoResult(
                isPassPhoto = false,
                confidence = 0f,
                reason = "Analysis error: ${e.message}"
            )
        }
    }

    /**
     * Load bitmap from URI with memory-efficient sampling
     */
    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // First, get image dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                // Calculate sample size for memory efficiency
                val maxDimension = 1024
                var sampleSize = 1
                while (options.outWidth / sampleSize > maxDimension || 
                       options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                
                // Reopen stream and decode with sampling
                context.contentResolver.openInputStream(uri)?.use { newStream ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(newStream, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Detect faces using ML Kit (suspending wrapper)
     */
    private suspend fun detectFaces(inputImage: InputImage): List<Face> {
        return suspendCancellableCoroutine { continuation ->
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    continuation.resume(faces)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
                .addOnCanceledListener {
                    continuation.cancel()
                }
        }
    }

    /**
     * Classify if the image is a pass photo based on detected faces
     */
    private fun classifyAsPassPhoto(faces: List<Face>, imageWidth: Int, imageHeight: Int): PassPhotoResult {
        // Criterion 1: Exactly one face
        if (faces.isEmpty()) {
            return PassPhotoResult(
                isPassPhoto = false,
                confidence = 0f,
                reason = "No face detected",
                faceCount = 0
            )
        }
        
        if (faces.size > 1) {
            return PassPhotoResult(
                isPassPhoto = false,
                confidence = 0f,
                reason = "Multiple faces detected (${faces.size})",
                faceCount = faces.size
            )
        }

        val face = faces[0]
        val boundingBox = face.boundingBox
        
        // Criterion 2: Face size ratio
        val faceHeightRatio = boundingBox.height().toFloat() / imageHeight
        val faceWidthRatio = boundingBox.width().toFloat() / imageWidth
        
        if (faceHeightRatio < MIN_FACE_SIZE_RATIO || faceHeightRatio > MAX_FACE_SIZE_RATIO) {
            return PassPhotoResult(
                isPassPhoto = false,
                confidence = 0.3f,
                reason = "Face size not appropriate for pass photo (ratio: ${"%.2f".format(faceHeightRatio)})",
                faceCount = 1,
                faceDetails = extractFaceDetails(face, imageWidth, imageHeight)
            )
        }

        // Criterion 3: Face centered
        val faceCenterX = boundingBox.centerX().toFloat()
        val faceCenterY = boundingBox.centerY().toFloat()
        val imageCenterX = imageWidth / 2f
        val imageCenterY = imageHeight / 2f
        
        val horizontalOffset = abs(faceCenterX - imageCenterX) / imageWidth
        val verticalOffset = abs(faceCenterY - imageCenterY) / imageHeight
        
        // Face should be roughly centered, but can be slightly higher (common in pass photos)
        if (horizontalOffset > CENTER_TOLERANCE_RATIO) {
            return PassPhotoResult(
                isPassPhoto = false,
                confidence = 0.4f,
                reason = "Face not centered horizontally",
                faceCount = 1,
                faceDetails = extractFaceDetails(face, imageWidth, imageHeight)
            )
        }

        // Criterion 4: Head rotation (frontal face)
        val headEulerY = face.headEulerAngleY  // Y-axis rotation (left/right)
        val headEulerZ = face.headEulerAngleZ  // Z-axis rotation (tilt)
        
        if (abs(headEulerY) > MAX_HEAD_ROTATION || abs(headEulerZ) > MAX_HEAD_ROTATION) {
            return PassPhotoResult(
                isPassPhoto = false,
                confidence = 0.5f,
                reason = "Head not facing forward (rotation: Y=${"%.1f".format(headEulerY)}°, Z=${"%.1f".format(headEulerZ)}°)",
                faceCount = 1,
                faceDetails = extractFaceDetails(face, imageWidth, imageHeight)
            )
        }

        // Criterion 5: Eyes open
        val leftEyeOpenProbability = face.leftEyeOpenProbability ?: 1f
        val rightEyeOpenProbability = face.rightEyeOpenProbability ?: 1f
        
        if (leftEyeOpenProbability < MIN_EYE_OPEN_PROBABILITY || 
            rightEyeOpenProbability < MIN_EYE_OPEN_PROBABILITY) {
            return PassPhotoResult(
                isPassPhoto = false,
                confidence = 0.6f,
                reason = "Eyes appear closed",
                faceCount = 1,
                faceDetails = extractFaceDetails(face, imageWidth, imageHeight)
            )
        }

        // Criterion 6: Not smiling (neutral expression)
        val smilingProbability = face.smilingProbability ?: 0f
        
        if (smilingProbability > MAX_SMILE_PROBABILITY) {
            return PassPhotoResult(
                isPassPhoto = false,
                confidence = 0.7f,
                reason = "Smiling detected (probability: ${"%.2f".format(smilingProbability)})",
                faceCount = 1,
                faceDetails = extractFaceDetails(face, imageWidth, imageHeight)
            )
        }

        // Calculate overall confidence based on how well criteria are met
        val confidence = calculateConfidence(
            faceHeightRatio, 
            horizontalOffset, 
            verticalOffset,
            headEulerY, 
            headEulerZ,
            leftEyeOpenProbability,
            rightEyeOpenProbability,
            smilingProbability
        )

        // It's a pass photo if confidence is high enough
        val isPassPhoto = confidence >= MIN_CONFIDENCE
        
        return PassPhotoResult(
            isPassPhoto = isPassPhoto,
            confidence = confidence,
            reason = if (isPassPhoto) "Valid pass photo detected" else "Does not meet all pass photo criteria",
            faceCount = 1,
            faceDetails = extractFaceDetails(face, imageWidth, imageHeight)
        )
    }

    /**
     * Calculate overall confidence score
     */
    private fun calculateConfidence(
        faceHeightRatio: Float,
        horizontalOffset: Float,
        verticalOffset: Float,
        headEulerY: Float,
        headEulerZ: Float,
        leftEyeOpen: Float,
        rightEyeOpen: Float,
        smiling: Float
    ): Float {
        // Weight each criterion
        val sizeScore = when {
            faceHeightRatio in 0.5f..0.7f -> 1f  // Ideal range
            faceHeightRatio in MIN_FACE_SIZE_RATIO..MAX_FACE_SIZE_RATIO -> 0.8f
            else -> 0.3f
        }
        
        val centerScore = 1f - (horizontalOffset / CENTER_TOLERANCE_RATIO).coerceIn(0f, 1f)
        
        val rotationScore = 1f - (maxOf(abs(headEulerY), abs(headEulerZ)) / MAX_HEAD_ROTATION).coerceIn(0f, 1f)
        
        val eyesScore = ((leftEyeOpen + rightEyeOpen) / 2f).coerceIn(0f, 1f)
        
        val expressionScore = 1f - smiling
        
        // Weighted average
        return (sizeScore * 0.2f + 
                centerScore * 0.25f + 
                rotationScore * 0.25f + 
                eyesScore * 0.15f + 
                expressionScore * 0.15f)
    }

    /**
     * Extract detailed face information
     */
    private fun extractFaceDetails(face: Face, imageWidth: Int, imageHeight: Int): FaceDetails {
        val boundingBox = face.boundingBox
        return FaceDetails(
            boundingBox = BoundingBoxInfo(
                left = boundingBox.left,
                top = boundingBox.top,
                right = boundingBox.right,
                bottom = boundingBox.bottom,
                widthRatio = boundingBox.width().toFloat() / imageWidth,
                heightRatio = boundingBox.height().toFloat() / imageHeight
            ),
            headRotation = HeadRotation(
                eulerAngleX = face.headEulerAngleX,
                eulerAngleY = face.headEulerAngleY,
                eulerAngleZ = face.headEulerAngleZ
            ),
            leftEyeOpenProbability = face.leftEyeOpenProbability,
            rightEyeOpenProbability = face.rightEyeOpenProbability,
            smilingProbability = face.smilingProbability
        )
    }

    /**
     * Quick check - is there any face in the image?
     * Use this for fast pre-filtering
     */
    suspend fun containsFace(uri: Uri): Boolean {
        return try {
            val bitmap = loadBitmap(uri) ?: return false
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val faces = detectFaces(inputImage)
            faces.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Close detector and release resources
     */
    fun close() {
        faceDetector.close()
    }
}

/**
 * Result of pass photo analysis
 */
data class PassPhotoResult(
    val isPassPhoto: Boolean,
    val confidence: Float,
    val reason: String,
    val faceCount: Int = 0,
    val faceDetails: FaceDetails? = null,
    val processingTimeMs: Long = 0
)

/**
 * Detailed face information
 */
data class FaceDetails(
    val boundingBox: BoundingBoxInfo,
    val headRotation: HeadRotation,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?
)

/**
 * Bounding box information
 */
data class BoundingBoxInfo(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val widthRatio: Float,
    val heightRatio: Float
)

/**
 * Head rotation angles
 */
data class HeadRotation(
    val eulerAngleX: Float,  // Pitch
    val eulerAngleY: Float,  // Yaw
    val eulerAngleZ: Float   // Roll
)
