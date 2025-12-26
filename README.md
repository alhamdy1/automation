# Automated Pass Photo Processor

## Military-Grade Security Android Application

Aplikasi Android untuk memproses foto paspor secara otomatis dengan keamanan tingkat militer.

---

## 📚 Documentation / Dokumentasi

**🇮🇩 Bahasa Indonesia:**
- **[PANDUAN_LENGKAP.md](PANDUAN_LENGKAP.md)** - Panduan lengkap setup dan penggunaan
- **[QUICK_START_ID.md](QUICK_START_ID.md)** - Panduan cepat memulai (5 menit)
- **[FLOWCHART_ID.md](FLOWCHART_ID.md)** - Diagram alur sistem lengkap
- **[SUMMARY_ID.md](SUMMARY_ID.md)** - Ringkasan dokumentasi

**🇬🇧 English:**
- This README provides technical documentation

---

## 📋 Table of Contents

1. [Security Architecture](#security-architecture)
2. [Background Monitoring](#background-monitoring)
3. [AI Filtering Logic](#ai-filtering-logic)
4. [Anti-Tamper Detection](#anti-tamper-detection)
5. [Vivo Device Integration](#vivo-device-integration)
6. [Installation & Setup](#installation--setup)

---

## 🔐 Security Architecture

### 1. Secure API Token Storage (KeystoreManager)

```kotlin
// Lokasi: app/src/main/java/com/passphoto/processor/security/KeystoreManager.kt

// METODE: Android Keystore + EncryptedSharedPreferences
// ENKRIPSI: AES-256-GCM
// HARDWARE: StrongBox (jika tersedia)
```

**Cara Kerja:**
1. Kunci enkripsi disimpan di Android Keystore (hardware-backed)
2. Data dienkripsi dengan AES-256-GCM sebelum disimpan
3. Kunci tidak pernah meninggalkan secure enclave
4. Otomatis menggunakan StrongBox HSM jika perangkat mendukung

**Penggunaan:**
```kotlin
val keystoreManager = KeystoreManager(context)

// Simpan token API
keystoreManager.storeApiToken("your_api_token_here")

// Ambil token API
val token = keystoreManager.retrieveApiToken()

// Hapus token (logout/security event)
keystoreManager.deleteApiToken()

// Cek hardware security
val hasHSM = keystoreManager.isHardwareSecurityAvailable()
```

### 2. Certificate Pinning (CertificatePinningManager)

```kotlin
// Lokasi: app/src/main/java/com/passphoto/processor/security/CertificatePinningManager.kt
```

**Cara Mencegah Man-in-the-Middle Attack:**
1. Pin ke hash public key server (bukan sertifikat)
2. Multiple backup pins untuk rotasi sertifikat
3. TLS 1.3 minimum
4. Cipher suite yang kuat saja

**Cara Generate Certificate Pin:**
```bash
# Dapatkan pin dari server
openssl s_client -servername yourdomain.com -connect yourdomain.com:443 | \
    openssl x509 -pubkey -noout | \
    openssl pkey -pubin -outform der | \
    openssl dgst -sha256 -binary | openssl enc -base64
```

**Konfigurasi di network_security_config.xml:**
```xml
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="true">api.yourserver.com</domain>
    <pin-set expiration="2025-12-31">
        <pin digest="SHA-256">YOUR_PRIMARY_PIN=</pin>
        <pin digest="SHA-256">YOUR_BACKUP_PIN=</pin>
    </pin-set>
</domain-config>
```

### 3. Scoped Storage

**Permissions yang Digunakan (Minimal):**
```xml
<!-- Android 13+ (Scoped Storage) -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- Android 12 dan dibawah -->
<uses-permission 
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
```

**Keuntungan Scoped Storage:**
- Tidak perlu akses penuh ke storage
- Hanya bisa akses foto via MediaStore
- Output disimpan di app-specific directory
- Tidak bisa membaca file aplikasi lain

---

## ⚡ Background Monitoring (Battery-Efficient)

### Arsitektur Event-Driven (NO POLLING!)

```kotlin
// Lokasi: app/src/main/java/com/passphoto/processor/service/PhotoMonitorManager.kt
```

**Mengapa ContentObserver, Bukan Polling:**

| Metode | Battery Usage | Responsiveness |
|--------|--------------|----------------|
| Polling Loop (setiap 30s) | TINGGI | Medium |
| AlarmManager (setiap 15m) | SEDANG | Rendah |
| **ContentObserver** | **MINIMAL** | **Real-time** |

**Cara Kerja:**

```kotlin
// 1. Register ContentObserver ke MediaStore
context.contentResolver.registerContentObserver(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
    true,
    contentObserver
)

// 2. Observer menerima event ketika ada foto baru
override fun onChange(selfChange: Boolean, uri: Uri?) {
    // Dipanggil HANYA ketika ada perubahan
    handleNewPhoto(uri)
}

// 3. WorkManager untuk processing (dengan constraints)
val constraints = Constraints.Builder()
    .setRequiresBatteryNotLow(true)  // Tidak proses saat battery rendah
    .build()
```

**Konsumsi Baterai:**
- Idle: ~0% (event-driven)
- Processing: Burst mode, hanya saat ada foto baru
- Doze mode compatible

---

## 🤖 AI Filtering Logic

### Pass Photo vs Regular Photo Classification

```kotlin
// Lokasi: app/src/main/java/com/passphoto/processor/ai/PassPhotoDetector.kt
```

**Kriteria Pass Photo:**

| Kriteria | Threshold | Alasan |
|----------|-----------|--------|
| Jumlah Wajah | = 1 | Pass foto hanya 1 orang |
| Ukuran Wajah | 35-85% tinggi gambar | Standar pas foto |
| Posisi Wajah | Tengah (±15%) | Wajah harus centered |
| Rotasi Kepala | < 15° | Harus menghadap depan |
| Mata | > 70% terbuka | Mata harus terbuka |
| Ekspresi | < 30% tersenyum | Ekspresi netral |

**Workflow:**

```
┌────────────────┐
│   New Photo    │
└───────┬────────┘
        ▼
┌────────────────┐
│ Face Detection │ (ML Kit - Offline)
│ (On-Device)    │
└───────┬────────┘
        ▼
┌────────────────┐
│ Pass Photo?    │──── NO ──► Skip (tidak diproses)
│ Classification │
└───────┬────────┘
        │ YES
        ▼
┌────────────────┐
│ AI Processing  │ (Vendor AI atau ML Kit)
└───────┬────────┘
        ▼
┌────────────────┐
│ Save to Folder │
└────────────────┘
```

**Confidence Score:**
```kotlin
val confidence = (sizeScore * 0.2f + 
                  centerScore * 0.25f + 
                  rotationScore * 0.25f + 
                  eyesScore * 0.15f + 
                  expressionScore * 0.15f)

// Diterima sebagai pass photo jika confidence >= 80%
```

---

## 🛡️ Anti-Tamper Detection

### Multi-Layer Security Checks

```kotlin
// Lokasi: app/src/main/java/com/passphoto/processor/security/TamperDetection.kt
```

**Detection Layers:**

1. **APK Signature Verification**
   - Memastikan APK tidak di-repack
   - Membandingkan hash sertifikat

2. **Root Detection**
   - Cek binary su
   - Cek root management apps (Magisk, SuperSU)
   - Cek RW paths
   - Cek build tags

3. **Debugger Detection**
   - `Debug.isDebuggerConnected()`
   - Check TracerPid
   - Check debugger ports

4. **Emulator Detection**
   - Build properties
   - Hardware characteristics
   - QEMU drivers

5. **Hook Framework Detection**
   - Frida detection
   - Xposed detection
   - Substrate detection
   - Native hook libraries

**Penggunaan:**
```kotlin
val tamperDetection = TamperDetection(context)
val result = tamperDetection.performSecurityChecks()

if (!result.isSecure()) {
    val issues = result.getSecurityIssues()
    // Handle security violation
}
```

---

## 📱 Vivo Device Integration

### Cara Mengecek dan Menyesuaikan untuk Vivo

```kotlin
// Lokasi: app/src/main/java/com/passphoto/processor/ai/VendorAIProcessor.kt
```

**Step 1: Cek Device Vendor**
```bash
# Via ADB
adb shell getprop ro.product.brand
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
```

**Step 2: Cek Package Vivo yang Tersedia**
```bash
# List semua package Vivo
adb shell pm list packages | grep vivo

# Package yang umum:
# com.vivo.ai.engine - AI Engine
# com.vivo.aiimage - AI Image Processing
# com.vivo.gallery - Gallery app
# com.vivo.camera - Camera app
```

**Step 3: Cek Intent yang Didukung**
```bash
# Dump info package
adb shell dumpsys package com.vivo.gallery | grep -A 10 "intent-filter"

# Test intent edit
adb shell am start -a android.intent.action.EDIT -t image/* -n com.vivo.gallery/.activity.EditActivity
```

**Step 4: Konfigurasi di Kode**
```kotlin
// File: VendorAIProcessor.kt

companion object {
    // Sesuaikan dengan package yang ada di device Vivo Anda
    private const val VIVO_AI_ENGINE = "com.vivo.ai.engine"
    private const val VIVO_AI_IMAGE = "com.vivo.aiimage"
    private const val VIVO_ALBUM = "com.vivo.gallery"
}

// Detect device
fun detectDeviceAndFeatures(): DeviceInfo {
    val brand = Build.BRAND.lowercase()
    // Otomatis mendeteksi Vivo
}
```

**Catatan untuk Vivo:**
- AI features Vivo adalah proprietary
- Tidak semua model Vivo memiliki AI features
- Gunakan ML Kit sebagai fallback (selalu available)
- Test di device fisik untuk hasil akurat

---

## 🚀 Installation & Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### Build
```bash
# Clone repository
git clone <repository-url>
cd automation

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

### Configure for Production

1. **Update Certificate Pins** di `network_security_config.xml`
2. **Update Signing Certificate Hash** di `TamperDetection.kt`
3. **Configure API endpoints** di `CertificatePinningManager.kt`
4. **Generate release keystore** dan sign APK

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

---

## 📝 Encrypted Logging

### Log Format
```kotlin
data class LogEntry(
    val timestamp: String,          // ISO 8601
    val timestampMillis: Long,      // Unix timestamp
    val action: String,             // Aksi yang dilakukan
    val status: LogStatus,          // SUCCESS, FAILURE, SECURITY
    val level: String,              // DEBUG, INFO, WARNING, ERROR
    val metadata: Map<String, Any>?, // Data tambahan
    val previousHash: String?,      // Hash entry sebelumnya (tamper detection)
    val hash: String?               // Hash entry ini
)
```

### Chain Integrity
- Setiap log entry memiliki hash
- Hash menyertakan hash entry sebelumnya
- Dapat mendeteksi jika log dimodifikasi

---

## 📊 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    PASS PHOTO PROCESSOR                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │   UI Layer      │  │  Service Layer  │  │  Security Layer │  │
│  │   (MainActivity)│  │ (MonitorService)│  │ (TamperDetect)  │  │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘  │
│           │                    │                     │          │
│           ▼                    ▼                     ▼          │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                      Core Layer                              ││
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       ││
│  │  │ PhotoMonitor │  │ PassPhotoAI  │  │ VendorAI     │       ││
│  │  │ Manager      │  │ Detector     │  │ Processor    │       ││
│  │  │ (Observer)   │  │ (ML Kit)     │  │ (Vivo/etc)   │       ││
│  │  └──────────────┘  └──────────────┘  └──────────────┘       ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Storage Layer                             ││
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       ││
│  │  │ Keystore     │  │ Encrypted    │  │ Scoped       │       ││
│  │  │ Manager      │  │ Logger       │  │ Storage      │       ││
│  │  │ (AES-256)    │  │ (AES-GCM)    │  │ (MediaStore) │       ││
│  │  └──────────────┘  └──────────────┘  └──────────────┘       ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔒 Security Checklist

- [x] API token encrypted with AES-256-GCM
- [x] Hardware-backed key storage (Keystore/StrongBox)
- [x] Certificate Pinning enabled
- [x] TLS 1.3 minimum
- [x] No cleartext traffic allowed
- [x] Scoped Storage (minimal permissions)
- [x] APK signature verification
- [x] Root detection
- [x] Debugger detection
- [x] Emulator detection
- [x] Hook framework detection
- [x] Encrypted activity logs
- [x] Tamper-evident log chain
- [x] No backup allowed (data extraction rules)
- [x] ProGuard obfuscation enabled

---

## 📄 License

This project is licensed under the MIT License