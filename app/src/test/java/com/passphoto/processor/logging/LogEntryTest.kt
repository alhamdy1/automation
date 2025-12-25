package com.passphoto.processor.logging

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for LogEntry and LogStatus data classes
 */
class LogEntryTest {

    @Test
    fun `LogEntry can be created with all fields`() {
        val logEntry = LogEntry(
            timestamp = "2024-01-01T12:00:00.000+0000",
            timestampMillis = 1704110400000L,
            action = "TEST_ACTION",
            status = LogStatus.SUCCESS,
            level = "INFO",
            metadata = mapOf("key" to "value"),
            previousHash = "abc123",
            hash = "def456"
        )
        
        assertEquals("TEST_ACTION", logEntry.action)
        assertEquals(LogStatus.SUCCESS, logEntry.status)
        assertEquals("INFO", logEntry.level)
        assertEquals("value", logEntry.metadata?.get("key"))
        assertEquals("abc123", logEntry.previousHash)
        assertEquals("def456", logEntry.hash)
    }

    @Test
    fun `LogEntry hash can be modified`() {
        val logEntry = LogEntry(
            timestamp = "2024-01-01T12:00:00.000+0000",
            timestampMillis = 1704110400000L,
            action = "TEST",
            status = LogStatus.SUCCESS,
            level = "INFO"
        )
        
        assertNull(logEntry.hash)
        logEntry.hash = "new_hash"
        assertEquals("new_hash", logEntry.hash)
    }

    @Test
    fun `LogStatus enum has correct values`() {
        assertEquals(5, LogStatus.values().size)
        assertTrue(LogStatus.values().contains(LogStatus.SUCCESS))
        assertTrue(LogStatus.values().contains(LogStatus.FAILURE))
        assertTrue(LogStatus.values().contains(LogStatus.PENDING))
        assertTrue(LogStatus.values().contains(LogStatus.SECURITY))
        assertTrue(LogStatus.values().contains(LogStatus.SKIPPED))
    }

    @Test
    fun `LogStatistics can be created correctly`() {
        val stats = LogStatistics(
            totalEntries = 100,
            successCount = 80,
            failureCount = 15,
            securityEvents = 5,
            oldestEntry = "2024-01-01T00:00:00.000+0000",
            newestEntry = "2024-01-31T23:59:59.000+0000"
        )
        
        assertEquals(100, stats.totalEntries)
        assertEquals(80, stats.successCount)
        assertEquals(15, stats.failureCount)
        assertEquals(5, stats.securityEvents)
    }

    @Test
    fun `LogExport can be created correctly`() {
        val entries = listOf(
            LogEntry(
                timestamp = "2024-01-01T12:00:00.000+0000",
                timestampMillis = 1704110400000L,
                action = "TEST",
                status = LogStatus.SUCCESS,
                level = "INFO"
            )
        )
        
        val export = LogExport(
            exportTimestamp = "2024-01-02T00:00:00.000+0000",
            totalEntries = entries.size,
            entries = entries
        )
        
        assertEquals(1, export.totalEntries)
        assertEquals(1, export.entries.size)
        assertEquals("TEST", export.entries[0].action)
    }
}
