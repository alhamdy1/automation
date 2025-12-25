package com.passphoto.processor.service

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PhotoMonitorManager data classes
 */
class PhotoMonitorManagerTest {

    @Test
    fun `PhotoInfo stores data correctly`() {
        val photoInfo = PhotoInfo(
            id = "12345",
            dateAdded = 1704067200L,
            path = "/storage/emulated/0/DCIM/photo.jpg",
            size = 2048000L
        )
        
        assertEquals("12345", photoInfo.id)
        assertEquals(1704067200L, photoInfo.dateAdded)
        assertEquals("/storage/emulated/0/DCIM/photo.jpg", photoInfo.path)
        assertEquals(2048000L, photoInfo.size)
    }

    @Test
    fun `PhotoInfo equality works correctly`() {
        val photo1 = PhotoInfo(
            id = "123",
            dateAdded = 1000L,
            path = "/path/to/photo.jpg",
            size = 1024L
        )
        
        val photo2 = PhotoInfo(
            id = "123",
            dateAdded = 1000L,
            path = "/path/to/photo.jpg",
            size = 1024L
        )
        
        assertEquals(photo1, photo2)
    }

    @Test
    fun `PhotoInfo copy works correctly`() {
        val original = PhotoInfo(
            id = "123",
            dateAdded = 1000L,
            path = "/original/path.jpg",
            size = 1024L
        )
        
        val copied = original.copy(path = "/new/path.jpg")
        
        assertEquals("123", copied.id)
        assertEquals("/new/path.jpg", copied.path)
    }
}
