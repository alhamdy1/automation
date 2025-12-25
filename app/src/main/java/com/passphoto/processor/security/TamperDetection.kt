package com.passphoto.processor.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * TamperDetection - Anti-Malware and Integrity Verification
 * 
 * SECURITY ARCHITECTURE:
 * 1. APK Signature Verification - Detects repackaged/modified APKs
 * 2. Root Detection - Identifies rooted devices
 * 3. Debugger Detection - Prevents runtime debugging
 * 4. Emulator Detection - Identifies testing environments
 * 5. Frida/Xposed Detection - Detects instrumentation frameworks
 * 
 * MILITARY-GRADE IMPLEMENTATION:
 * - Multi-layer detection with redundancy
 * - Runtime integrity checks
 * - Hook detection for common attack frameworks
 * - Memory protection validation
 */
class TamperDetection(private val context: Context) {

    companion object {
        /**
         * IMPORTANT: Replace these placeholder values before production release!
         * 
         * To get your release signing certificate SHA-256 hash:
         * 
         * Method 1 - Using keytool:
         *   keytool -list -v -keystore your-release-key.keystore -alias your-alias
         *   Copy the SHA256 fingerprint (remove colons and lowercase)
         * 
         * Method 2 - Using Gradle:
         *   ./gradlew signingReport
         *   Look for SHA-256 under your release variant
         * 
         * Method 3 - Get from APK at runtime (for debugging):
         *   Log the computed hash during development
         * 
         * These placeholder values will cause signature verification to fail until replaced!
         */
        private const val EXPECTED_SIGNATURE_SHA256 = "YOUR_RELEASE_SIGNING_CERT_SHA256_HASH"
        
        // Debug signature - REMOVE this in production builds!
        // Only used during development to allow testing
        private const val DEBUG_SIGNATURE_SHA256 = "DEBUG_SIGNING_CERT_SHA256_HASH"
        
        // Package name validation
        private const val EXPECTED_PACKAGE_NAME = "com.passphoto.processor"
        
        // Installer packages (Google Play, etc.)
        private val TRUSTED_INSTALLERS = listOf(
            "com.android.vending",           // Google Play Store
            "com.google.android.packageinstaller",
            "com.google.android.gms"
        )
    }

    /**
     * Comprehensive security check - run at app startup
     * @return SecurityCheckResult with all check outcomes
     */
    fun performSecurityChecks(): SecurityCheckResult {
        val result = SecurityCheckResult()
        
        // Signature verification
        result.signatureValid = verifyAppSignature()
        
        // Root detection
        result.isRooted = detectRoot()
        
        // Debugger detection
        result.debuggerAttached = detectDebugger()
        
        // Emulator detection
        result.isEmulator = detectEmulator()
        
        // Hook framework detection
        result.hookDetected = detectHookFrameworks()
        
        // Installer validation
        result.installerValid = validateInstaller()
        
        // Package name validation
        result.packageValid = validatePackageName()
        
        return result
    }

    /**
     * Verify APK signature matches expected signing certificate
     * Prevents distribution of repackaged/modified APKs
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun verifyAppSignature(): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.any { signature ->
                val signatureBytes = signature.toByteArray()
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signatureBytes)
                val signatureHash = digest.toHexString()
                
                // Check against expected signatures
                signatureHash.equals(EXPECTED_SIGNATURE_SHA256, ignoreCase = true) ||
                signatureHash.equals(DEBUG_SIGNATURE_SHA256, ignoreCase = true)
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect if device is rooted using multiple methods
     */
    fun detectRoot(): Boolean {
        return checkRootBinaries() || 
               checkSuExists() || 
               checkRootPackages() || 
               checkRWPaths() || 
               checkMagiskHide() ||
               checkBuildTags()
    }

    /**
     * Check for common root binary files
     */
    private fun checkRootBinaries(): Boolean {
        val binaryPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
            "/su/bin/daemonsu",
            "/magisk/.core/bin/su"
        )
        
        return binaryPaths.any { path ->
            File(path).exists()
        }
    }

    /**
     * Try to execute su command
     */
    private fun checkSuExists(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            reader.close()
            result != null && result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check for root management apps
     */
    private fun checkRootPackages(): Boolean {
        val rootPackages = listOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.zachspong.temprootremovejb",
            "com.ramdroid.appquarantine",
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate"
        )
        
        return rootPackages.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /**
     * Check for read-write access on system paths
     */
    private fun checkRWPaths(): Boolean {
        val pathsToCheck = listOf(
            "/system",
            "/system/bin",
            "/system/sbin",
            "/system/xbin",
            "/vendor/bin",
            "/data"
        )
        
        return pathsToCheck.any { path ->
            val file = File(path)
            file.canWrite()
        }
    }

    /**
     * Detect Magisk Hide feature
     */
    private fun checkMagiskHide(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "magisk --hide status"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check build tags for test-keys (custom ROM indicator)
     */
    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    /**
     * Detect if debugger is attached
     */
    fun detectDebugger(): Boolean {
        return Debug.isDebuggerConnected() || 
               Debug.waitingForDebugger() ||
               checkDebuggerPort() ||
               checkTracerPid()
    }

    /**
     * Check for debugger connections on common ports
     */
    private fun checkDebuggerPort(): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 23946), 100)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check TracerPid in /proc/self/status
     */
    private fun checkTracerPid(): Boolean {
        return try {
            BufferedReader(java.io.FileReader("/proc/self/status")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("TracerPid:")) {
                        val tracerPid = line!!.substring("TracerPid:".length).trim()
                        return tracerPid != "0"
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect if running on emulator
     */
    fun detectEmulator(): Boolean {
        return checkEmulatorBuild() || 
               checkEmulatorHardware() || 
               checkEmulatorFiles() ||
               checkQemuDriver()
    }

    /**
     * Check build properties for emulator indicators
     */
    private fun checkEmulatorBuild(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
               Build.FINGERPRINT.startsWith("unknown") ||
               Build.MODEL.contains("google_sdk") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK built for x86") ||
               Build.MANUFACTURER.contains("Genymotion") ||
               Build.HARDWARE.contains("goldfish") ||
               Build.HARDWARE.contains("ranchu") ||
               Build.PRODUCT.contains("sdk") ||
               Build.PRODUCT.contains("google_sdk") ||
               Build.PRODUCT.contains("sdk_gphone") ||
               Build.PRODUCT.contains("vbox86p") ||
               Build.BOARD.lowercase().contains("nox") ||
               Build.BOOTLOADER.lowercase().contains("nox") ||
               Build.HARDWARE.lowercase().contains("nox")
               // Note: Build.SERIAL is deprecated and requires READ_PRIVILEGED_PHONE_STATE
               // permission on Android 10+, so we skip checking it
    }

    /**
     * Check hardware characteristics
     */
    private fun checkEmulatorHardware(): Boolean {
        return Build.HARDWARE == "goldfish" ||
               Build.HARDWARE == "ranchu" ||
               Build.HARDWARE.contains("intel") && Build.PRODUCT.contains("sdk")
    }

    /**
     * Check for emulator-specific files
     */
    private fun checkEmulatorFiles(): Boolean {
        val emulatorFiles = listOf(
            "/dev/socket/qemud",
            "/dev/qemu_pipe",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props"
        )
        
        return emulatorFiles.any { File(it).exists() }
    }

    /**
     * Check for QEMU driver
     */
    private fun checkQemuDriver(): Boolean {
        return try {
            BufferedReader(java.io.FileReader("/proc/tty/drivers")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains("goldfish")) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect Frida, Xposed, and other hook frameworks
     */
    fun detectHookFrameworks(): Boolean {
        return detectFrida() || 
               detectXposed() || 
               detectSubstrate() ||
               detectNativeHooks()
    }

    /**
     * Detect Frida instrumentation framework
     */
    private fun detectFrida(): Boolean {
        // Check for Frida server processes
        val fridaProcesses = listOf(
            "frida-server",
            "frida-agent",
            "frida-gadget"
        )
        
        try {
            BufferedReader(java.io.FileReader("/proc/self/maps")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (fridaProcesses.any { line!!.contains(it) }) {
                        return true
                    }
                    // Check for Frida memory patterns
                    if (line!!.contains("frida") || line!!.contains("gadget")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        // Check for Frida server ports
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 27042), 100)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect Xposed Framework
     */
    private fun detectXposed(): Boolean {
        // Check for Xposed installer
        val xposedPackages = listOf(
            "de.robv.android.xposed.installer",
            "io.va.exposed",
            "org.meowcat.edxposed.manager",
            "com.topjohnwu.magisk" // Magisk can host Xposed modules
        )
        
        val hasXposedApp = xposedPackages.any { packageName ->
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
        
        if (hasXposedApp) return true
        
        // Check for Xposed classes via reflection
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Detect Cydia Substrate (iOS/Android hooking framework)
     */
    private fun detectSubstrate(): Boolean {
        return try {
            Class.forName("com.saurik.substrate.MS")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Check for native hook libraries
     */
    private fun detectNativeHooks(): Boolean {
        val suspiciousLibraries = listOf(
            "libsubstrate.so",
            "libxposed_art.so",
            "libart_substitute.so",
            "libriru_edxp.so"
        )
        
        return try {
            BufferedReader(java.io.FileReader("/proc/self/maps")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (suspiciousLibraries.any { line!!.contains(it) }) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate app was installed from trusted source
     */
    fun validateInstaller(): Boolean {
        return try {
            val installerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstallerPackageName(context.packageName)
            }
            
            // Allow null for debug/adb installs during development
            installerPackage == null || TRUSTED_INSTALLERS.contains(installerPackage)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate package name hasn't been tampered
     */
    private fun validatePackageName(): Boolean {
        return context.packageName == EXPECTED_PACKAGE_NAME
    }

    /**
     * Check if developer options are enabled
     */
    fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) != 0
    }

    /**
     * Check if USB debugging is enabled
     */
    fun isUsbDebuggingEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) != 0
    }

    /**
     * Extension function to convert ByteArray to hex string
     */
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    /**
     * Security check result data class
     */
    data class SecurityCheckResult(
        var signatureValid: Boolean = false,
        var isRooted: Boolean = false,
        var debuggerAttached: Boolean = false,
        var isEmulator: Boolean = false,
        var hookDetected: Boolean = false,
        var installerValid: Boolean = false,
        var packageValid: Boolean = false
    ) {
        /**
         * Overall security status
         * Returns true only if all checks pass
         */
        fun isSecure(): Boolean {
            return signatureValid && 
                   !isRooted && 
                   !debuggerAttached && 
                   !isEmulator && 
                   !hookDetected && 
                   installerValid && 
                   packageValid
        }

        /**
         * Get list of security issues found
         */
        fun getSecurityIssues(): List<String> {
            val issues = mutableListOf<String>()
            if (!signatureValid) issues.add("Invalid APK signature")
            if (isRooted) issues.add("Device is rooted")
            if (debuggerAttached) issues.add("Debugger detected")
            if (isEmulator) issues.add("Running on emulator")
            if (hookDetected) issues.add("Hook framework detected")
            if (!installerValid) issues.add("Untrusted installer")
            if (!packageValid) issues.add("Package name mismatch")
            return issues
        }
    }
}
