package com.passphoto.processor.security

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for TamperDetection data classes and SecurityCheckResult
 */
class TamperDetectionTest {

    @Test
    fun `SecurityCheckResult isSecure returns true when all checks pass`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = true,
            isRooted = false,
            debuggerAttached = false,
            isEmulator = false,
            hookDetected = false,
            installerValid = true,
            packageValid = true
        )
        
        assertTrue("All checks pass should return secure", result.isSecure())
    }

    @Test
    fun `SecurityCheckResult isSecure returns false when signature is invalid`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = false,  // Invalid
            isRooted = false,
            debuggerAttached = false,
            isEmulator = false,
            hookDetected = false,
            installerValid = true,
            packageValid = true
        )
        
        assertFalse("Invalid signature should return not secure", result.isSecure())
    }

    @Test
    fun `SecurityCheckResult isSecure returns false when device is rooted`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = true,
            isRooted = true,  // Rooted
            debuggerAttached = false,
            isEmulator = false,
            hookDetected = false,
            installerValid = true,
            packageValid = true
        )
        
        assertFalse("Rooted device should return not secure", result.isSecure())
    }

    @Test
    fun `SecurityCheckResult isSecure returns false when debugger attached`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = true,
            isRooted = false,
            debuggerAttached = true,  // Debugger
            isEmulator = false,
            hookDetected = false,
            installerValid = true,
            packageValid = true
        )
        
        assertFalse("Debugger attached should return not secure", result.isSecure())
    }

    @Test
    fun `SecurityCheckResult isSecure returns false on emulator`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = true,
            isRooted = false,
            debuggerAttached = false,
            isEmulator = true,  // Emulator
            hookDetected = false,
            installerValid = true,
            packageValid = true
        )
        
        assertFalse("Emulator should return not secure", result.isSecure())
    }

    @Test
    fun `SecurityCheckResult isSecure returns false when hook detected`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = true,
            isRooted = false,
            debuggerAttached = false,
            isEmulator = false,
            hookDetected = true,  // Hook detected
            installerValid = true,
            packageValid = true
        )
        
        assertFalse("Hook detection should return not secure", result.isSecure())
    }

    @Test
    fun `SecurityCheckResult getSecurityIssues returns all issues`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = false,
            isRooted = true,
            debuggerAttached = true,
            isEmulator = true,
            hookDetected = true,
            installerValid = false,
            packageValid = false
        )
        
        val issues = result.getSecurityIssues()
        
        assertEquals("Should have 7 security issues", 7, issues.size)
        assertTrue(issues.contains("Invalid APK signature"))
        assertTrue(issues.contains("Device is rooted"))
        assertTrue(issues.contains("Debugger detected"))
        assertTrue(issues.contains("Running on emulator"))
        assertTrue(issues.contains("Hook framework detected"))
        assertTrue(issues.contains("Untrusted installer"))
        assertTrue(issues.contains("Package name mismatch"))
    }

    @Test
    fun `SecurityCheckResult getSecurityIssues returns empty list when secure`() {
        val result = TamperDetection.SecurityCheckResult(
            signatureValid = true,
            isRooted = false,
            debuggerAttached = false,
            isEmulator = false,
            hookDetected = false,
            installerValid = true,
            packageValid = true
        )
        
        val issues = result.getSecurityIssues()
        
        assertTrue("Secure result should have no issues", issues.isEmpty())
    }

    @Test
    fun `SecurityCheckResult default values are not secure`() {
        val result = TamperDetection.SecurityCheckResult()
        
        // Default values should indicate insecure state (signatureValid = false by default)
        assertFalse("Default result should not be secure", result.isSecure())
    }
}
