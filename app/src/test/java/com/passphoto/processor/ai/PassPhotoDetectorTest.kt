package com.passphoto.processor.ai

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for AI data classes
 */
class PassPhotoDetectorTest {

    @Test
    fun `PassPhotoResult with pass photo has correct values`() {
        val result = PassPhotoResult(
            isPassPhoto = true,
            confidence = 0.95f,
            reason = "Valid pass photo detected",
            faceCount = 1,
            processingTimeMs = 150
        )
        
        assertTrue(result.isPassPhoto)
        assertEquals(0.95f, result.confidence, 0.001f)
        assertEquals(1, result.faceCount)
        assertEquals(150L, result.processingTimeMs)
    }

    @Test
    fun `PassPhotoResult with no face detected`() {
        val result = PassPhotoResult(
            isPassPhoto = false,
            confidence = 0f,
            reason = "No face detected",
            faceCount = 0
        )
        
        assertFalse(result.isPassPhoto)
        assertEquals(0f, result.confidence, 0.001f)
        assertEquals("No face detected", result.reason)
        assertEquals(0, result.faceCount)
    }

    @Test
    fun `PassPhotoResult with multiple faces`() {
        val result = PassPhotoResult(
            isPassPhoto = false,
            confidence = 0f,
            reason = "Multiple faces detected (3)",
            faceCount = 3
        )
        
        assertFalse(result.isPassPhoto)
        assertEquals(3, result.faceCount)
    }

    @Test
    fun `PassPhotoResult copy works correctly`() {
        val original = PassPhotoResult(
            isPassPhoto = true,
            confidence = 0.9f,
            reason = "Valid",
            faceCount = 1,
            processingTimeMs = 100
        )
        
        val copied = original.copy(processingTimeMs = 200)
        
        assertEquals(200L, copied.processingTimeMs)
        assertEquals(original.confidence, copied.confidence, 0.001f)
        assertEquals(original.isPassPhoto, copied.isPassPhoto)
    }

    @Test
    fun `BoundingBoxInfo has correct structure`() {
        val bbox = BoundingBoxInfo(
            left = 100,
            top = 50,
            right = 300,
            bottom = 350,
            widthRatio = 0.5f,
            heightRatio = 0.6f
        )
        
        assertEquals(100, bbox.left)
        assertEquals(50, bbox.top)
        assertEquals(300, bbox.right)
        assertEquals(350, bbox.bottom)
        assertEquals(0.5f, bbox.widthRatio, 0.001f)
        assertEquals(0.6f, bbox.heightRatio, 0.001f)
    }

    @Test
    fun `HeadRotation angles stored correctly`() {
        val rotation = HeadRotation(
            eulerAngleX = 5f,
            eulerAngleY = -10f,
            eulerAngleZ = 2f
        )
        
        assertEquals(5f, rotation.eulerAngleX, 0.001f)
        assertEquals(-10f, rotation.eulerAngleY, 0.001f)
        assertEquals(2f, rotation.eulerAngleZ, 0.001f)
    }

    @Test
    fun `FaceDetails contains all information`() {
        val bbox = BoundingBoxInfo(0, 0, 100, 100, 0.5f, 0.5f)
        val rotation = HeadRotation(0f, 0f, 0f)
        
        val details = FaceDetails(
            boundingBox = bbox,
            headRotation = rotation,
            leftEyeOpenProbability = 0.95f,
            rightEyeOpenProbability = 0.92f,
            smilingProbability = 0.1f
        )
        
        assertNotNull(details.boundingBox)
        assertNotNull(details.headRotation)
        assertEquals(0.95f, details.leftEyeOpenProbability!!, 0.001f)
        assertEquals(0.92f, details.rightEyeOpenProbability!!, 0.001f)
        assertEquals(0.1f, details.smilingProbability!!, 0.001f)
    }
}
