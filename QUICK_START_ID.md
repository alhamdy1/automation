# Quick Start Guide - Pass Photo Processor

> Panduan cepat untuk memulai proyek dalam bahasa Indonesia

## 🎯 Apa ini?

Aplikasi Android yang **otomatis memproses foto pas/paspor** dari galeri Anda dengan **keamanan tingkat tinggi**.

## ⚡ Quick Setup (5 Menit)

### 1. Install Software
```bash
# Pastikan sudah terinstall:
- Android Studio Hedgehog atau lebih baru
- JDK 17
- Git
```

### 2. Clone & Build
```bash
# Clone repository
git clone https://github.com/alhamdy1/automation.git
cd automation

# Build
./gradlew assembleDebug
```

### 3. Install ke Ponsel
```bash
# Enable USB Debugging di ponsel
# Colok USB ke komputer

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. Jalankan
1. Buka aplikasi di ponsel
2. Allow permissions
3. Tap "Start Monitoring"
4. Selesai! Aplikasi akan otomatis detect foto pas

## 🔄 Cara Kerja

```
Foto Baru di Galeri
       ↓
AI Deteksi Wajah (ML Kit)
       ↓
Foto Pas? → NO → Skip
       ↓
      YES
       ↓
AI Processing
       ↓
Save ke Pictures/PassPhotoProcessor/
       ↓
Notifikasi Selesai
```

## 📱 Cara Pakai

### Mode Automatic (Recommended)
1. Start aplikasi
2. Ambil/download foto pas
3. Aplikasi otomatis proses
4. Cek hasil di galeri

### Mode Manual
1. Tap "Select Photo"
2. Pilih foto
3. Tap "Process"
4. Lihat hasil

## 🔑 Fitur Utama

### 1. Auto Detection
- Deteksi foto pas vs foto biasa
- Hanya proses foto yang memenuhi kriteria
- Confidence score ≥ 80%

### 2. Battery Efficient
- Event-driven (bukan polling)
- ContentObserver untuk real-time detection
- Processing hanya saat ada foto baru

### 3. High Security
- AES-256 encryption
- Certificate pinning
- Root/debugger detection
- Tamper protection

## 📋 Kriteria Foto Pas

✅ **Diterima:**
- 1 wajah saja
- Menghadap depan (rotasi < 15°)
- Ukuran wajah 35-85% tinggi gambar
- Posisi tengah (±15%)
- Mata terbuka (> 70%)
- Ekspresi netral

❌ **Ditolak:**
- Foto grup (multi-person)
- Wajah miring/samping
- Foto selfie dengan filter
- Blur atau gelap

## 🛠️ Requirements

### Minimum
- **Android**: 8.0 (API 26) atau lebih tinggi
- **RAM**: 4GB
- **Storage**: 2GB free space
- **Development**: 
  - Android Studio Hedgehog+
  - JDK 17
  - Android SDK 34

### Recommended
- Android 10+ untuk best performance
- Device fisik (bukan emulator)
- Vivo device untuk Vivo AI features

## 📁 Struktur Folder

```
automation/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/passphoto/processor/
│   │   │   │   ├── ai/
│   │   │   │   │   ├── PassPhotoDetector.kt      # AI classification
│   │   │   │   │   └── VendorAIProcessor.kt      # Vendor AI integration
│   │   │   │   ├── security/
│   │   │   │   │   ├── KeystoreManager.kt        # Secure storage
│   │   │   │   │   ├── CertificatePinningManager.kt  # Network security
│   │   │   │   │   └── TamperDetection.kt        # Anti-tamper
│   │   │   │   ├── service/
│   │   │   │   │   ├── PhotoMonitorService.kt    # Background monitoring
│   │   │   │   │   └── PhotoMonitorManager.kt    # Monitor coordinator
│   │   │   │   ├── logging/
│   │   │   │   │   └── EncryptedLogger.kt        # Secure logging
│   │   │   │   └── ui/
│   │   │   │       └── MainActivity.kt           # Main UI
│   │   │   └── res/                              # Resources
│   │   └── test/                                 # Unit tests
│   └── build.gradle                              # App dependencies
├── build.gradle                                  # Project config
├── README.md                                     # Documentation (EN)
├── PANDUAN_LENGKAP.md                            # Full guide (ID)
└── QUICK_START_ID.md                             # This file
```

## 🔧 Common Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run tests
./gradlew test

# Check devices
adb devices

# View logs
adb logcat | grep "PassPhotoProcessor"

# Clear app data
adb shell pm clear com.passphoto.processor

# Uninstall
adb uninstall com.passphoto.processor

# Grant permissions manually
adb shell pm grant com.passphoto.processor android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.passphoto.processor android.permission.POST_NOTIFICATIONS
```

## ❓ Troubleshooting Cepat

### Build Failed
```bash
# Refresh dependencies
./gradlew --refresh-dependencies

# Clean build
./gradlew clean build
```

### Device Not Found
```bash
# Restart ADB
adb kill-server
adb start-server
adb devices
```

### App Crashes
```bash
# View crash logs
adb logcat | grep "AndroidRuntime"

# Clear data dan reinstall
adb shell pm clear com.passphoto.processor
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Service Stops
1. Settings → Apps → Pass Photo Processor
2. Battery → Don't optimize
3. Autostart → Enable (Xiaomi/Vivo/Oppo)

## 📚 Dokumentasi Lengkap

Untuk penjelasan detail, lihat:
- **PANDUAN_LENGKAP.md** - Panduan lengkap dalam Bahasa Indonesia
- **README.md** - Technical documentation (English)
- **CONTRIBUTING.md** - Contribution guidelines

## 🔐 Security Features

1. **Data Encryption**
   - AES-256-GCM for API tokens
   - Hardware-backed keystore
   - SQLCipher for database

2. **Network Security**
   - Certificate pinning
   - TLS 1.3 minimum
   - No cleartext traffic

3. **Anti-Tamper**
   - Root detection
   - Debugger detection
   - APK signature verification
   - Hook framework detection

4. **Privacy**
   - Scoped storage (minimal permissions)
   - No data backup allowed
   - Encrypted activity logs

## 🎯 Use Cases

### 1. Foto Paspor
- Upload ke website imigrasi
- Cetak sendiri
- Simpan digital

### 2. Foto KTP/SIM
- Perpanjangan dokumen
- Lamaran kerja
- Registrasi online

### 3. Foto ID Card
- Kartu mahasiswa
- Employee ID
- Membership card

## 💡 Tips

### Untuk Hasil Terbaik
1. Gunakan foto dengan pencahayaan baik
2. Background polos (putih/biru)
3. Wajah menghadap depan
4. Tidak memakai kacamata hitam
5. Ekspresi netral (tidak tersenyum)

### Untuk Performance
1. Disable battery optimization
2. Enable autostart
3. Close apps lain saat processing
4. Gunakan WiFi untuk download model AI

### Untuk Development
1. Test di device fisik (bukan emulator)
2. Enable debug logging untuk development
3. Disable ProGuard untuk debug build
4. Use Android Profiler untuk monitor performance

## 🚀 Next Steps

Setelah aplikasi berjalan:

1. **Eksplorasi Kode**
   - Baca source code untuk memahami architecture
   - Lihat implementasi security features
   - Study AI detection logic

2. **Customize**
   - Ubah UI/UX sesuai kebutuhan
   - Tambah fitur baru
   - Integrate dengan API sendiri

3. **Testing**
   - Test dengan berbagai jenis foto
   - Test di berbagai device
   - Test battery usage

4. **Deploy**
   - Generate release keystore
   - Configure ProGuard
   - Upload ke Play Store

## 📞 Support

- **Issues**: GitHub Issues untuk bug reports
- **Questions**: GitHub Discussions
- **Security**: Email private untuk security issues

## 📄 License

MIT License - Lihat LICENSE file untuk detail

---

**Made with ❤️ for automatic passport photo processing**

*Last updated: December 2024*
