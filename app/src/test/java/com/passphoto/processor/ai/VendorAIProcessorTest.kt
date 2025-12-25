package com.passphoto.processor.ai

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for VendorAIProcessor data classes
 */
class VendorAIProcessorTest {

    @Test
    fun `DeviceVendor enum has all expected vendors`() {
        val vendors = DeviceVendor.values()
        
        assertEquals(7, vendors.size)
        assertTrue(vendors.contains(DeviceVendor.VIVO))
        assertTrue(vendors.contains(DeviceVendor.SAMSUNG))
        assertTrue(vendors.contains(DeviceVendor.XIAOMI))
        assertTrue(vendors.contains(DeviceVendor.OPPO))
        assertTrue(vendors.contains(DeviceVendor.HUAWEI))
        assertTrue(vendors.contains(DeviceVendor.GOOGLE))
        assertTrue(vendors.contains(DeviceVendor.UNKNOWN))
    }

    @Test
    fun `AIFeature enum has all expected features`() {
        val features = AIFeature.values()
        
        assertEquals(5, features.size)
        assertTrue(features.contains(AIFeature.AI_ENHANCEMENT))
        assertTrue(features.contains(AIFeature.BACKGROUND_REMOVAL))
        assertTrue(features.contains(AIFeature.PORTRAIT_MODE))
        assertTrue(features.contains(AIFeature.AI_ALBUM))
        assertTrue(features.contains(AIFeature.ML_KIT_FALLBACK))
    }

    @Test
    fun `ProcessingMode enum has correct modes`() {
        val modes = ProcessingMode.values()
        
        assertEquals(4, modes.size)
        assertTrue(modes.contains(ProcessingMode.CROP_AND_CENTER))
        assertTrue(modes.contains(ProcessingMode.ENHANCE_ONLY))
        assertTrue(modes.contains(ProcessingMode.BACKGROUND_REMOVE))
        assertTrue(modes.contains(ProcessingMode.FULL_PROCESS))
    }

    @Test
    fun `DeviceInfo can be created correctly`() {
        val deviceInfo = DeviceInfo(
            vendor = DeviceVendor.VIVO,
            model = "V21",
            brand = "vivo",
            availableFeatures = listOf(AIFeature.AI_ENHANCEMENT, AIFeature.ML_KIT_FALLBACK)
        )
        
        assertEquals(DeviceVendor.VIVO, deviceInfo.vendor)
        assertEquals("V21", deviceInfo.model)
        assertEquals("vivo", deviceInfo.brand)
        assertEquals(2, deviceInfo.availableFeatures.size)
    }

    @Test
    fun `ProcessingOptions has correct defaults`() {
        val options = ProcessingOptions()
        
        assertEquals(ProcessingMode.FULL_PROCESS, options.processingMode)
        assertEquals(95, options.outputQuality)
        assertEquals(2048, options.maxOutputSize)
    }

    @Test
    fun `ProcessingOptions can be customized`() {
        val options = ProcessingOptions(
            processingMode = ProcessingMode.CROP_AND_CENTER,
            outputQuality = 80,
            maxOutputSize = 1024
        )
        
        assertEquals(ProcessingMode.CROP_AND_CENTER, options.processingMode)
        assertEquals(80, options.outputQuality)
        assertEquals(1024, options.maxOutputSize)
    }

    @Test
    fun `ProcessingResult success case`() {
        val result = ProcessingResult(
            success = true,
            outputPath = "/storage/processed/photo.jpg",
            processingTimeMs = 500,
            usedVendorAI = true,
            vendorFeature = "VIVO_AI"
        )
        
        assertTrue(result.success)
        assertEquals("/storage/processed/photo.jpg", result.outputPath)
        assertEquals(500L, result.processingTimeMs)
        assertTrue(result.usedVendorAI)
        assertEquals("VIVO_AI", result.vendorFeature)
        assertNull(result.errorMessage)
    }

    @Test
    fun `ProcessingResult failure case`() {
        val result = ProcessingResult(
            success = false,
            errorMessage = "Failed to load image"
        )
        
        assertFalse(result.success)
        assertNull(result.outputPath)
        assertEquals("Failed to load image", result.errorMessage)
    }
}
