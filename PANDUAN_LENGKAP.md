# Panduan Lengkap: Pass Photo Processor

## 📖 Daftar Isi
1. [Penjelasan Proyek](#penjelasan-proyek)
2. [Alur Kerja Aplikasi](#alur-kerja-aplikasi)
3. [Persiapan yang Dibutuhkan](#persiapan-yang-dibutuhkan)
4. [Langkah-langkah Setup](#langkah-langkah-setup)
5. [Cara Menggunakan](#cara-menggunakan)
6. [Arsitektur Sistem](#arsitektur-sistem)
7. [Troubleshooting](#troubleshooting)

---

## 📱 Penjelasan Proyek

### Apa itu Pass Photo Processor?

Pass Photo Processor adalah aplikasi Android yang **secara otomatis memproses foto pas/paspor** yang ada di galeri ponsel Anda. Aplikasi ini dirancang dengan **keamanan tingkat militer** untuk melindungi data dan privasi pengguna.

### Fitur Utama

1. **Monitoring Otomatis**
   - Memantau galeri foto secara real-time
   - Mendeteksi foto baru tanpa menguras baterai
   - Bekerja di background tanpa mengganggu aktivitas lain

2. **Deteksi Foto Pas dengan AI**
   - Membedakan foto pas dengan foto biasa
   - Hanya memproses foto yang memenuhi kriteria pas foto
   - Menggunakan teknologi Machine Learning on-device (offline)

3. **Keamanan Tingkat Tinggi**
   - Enkripsi data dengan AES-256
   - Proteksi dari hacking dan reverse engineering
   - Certificate pinning untuk komunikasi API
   - Deteksi root, debugger, dan emulator

4. **Hemat Baterai**
   - Tidak menggunakan polling loop
   - Event-driven architecture
   - Processing hanya saat ada foto baru

---

## 🔄 Alur Kerja Aplikasi

### Diagram Alur Lengkap

```
┌─────────────────────────────────────────────────────────────────┐
│                    MULAI APLIKASI                               │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  CEK KEAMANAN                                                   │
│  • Cek signature APK (tidak di-repack?)                         │
│  • Cek root detection (device di-root?)                         │
│  • Cek debugger (ada yang debug?)                               │
│  • Cek emulator (jalan di emulator?)                            │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
                    Aman? ──NO──► BLOKIR APLIKASI
                            │
                           YES
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  MINTA PERMISSION                                               │
│  • READ_MEDIA_IMAGES (akses foto)                               │
│  • FOREGROUND_SERVICE (service background)                      │
│  • POST_NOTIFICATIONS (notifikasi)                              │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  START MONITORING SERVICE                                       │
│  • Register ContentObserver ke MediaStore                       │
│  • Dengarkan perubahan di galeri                                │
│  • Mode: Event-driven (hemat baterai)                           │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
        ┌───────────────────────────────────────┐
        │   APLIKASI BERJALAN DI BACKGROUND     │
        │   (Menunggu foto baru...)             │
        └───────────────┬───────────────────────┘
                        ▼
        ┌───────────────────────────────────────┐
        │   EVENT: ADA FOTO BARU DI GALERI      │
        └───────────────┬───────────────────────┘
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│  BACA METADATA FOTO                                             │
│  • Baca URI foto dari MediaStore                                │
│  • Ambil informasi: path, timestamp, size                       │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  DETEKSI WAJAH (ML Kit - On-Device)                             │
│  • Load foto ke memory                                          │
│  • Jalankan face detection                                      │
│  • Analisis: jumlah wajah, posisi, ukuran                       │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  KLASIFIKASI: APAKAH INI FOTO PAS?                              │
│                                                                 │
│  KRITERIA FOTO PAS:                                             │
│  ✓ Jumlah wajah = 1 orang                                       │
│  ✓ Ukuran wajah 35-85% tinggi gambar                            │
│  ✓ Posisi wajah di tengah (±15%)                                │
│  ✓ Rotasi kepala < 15°                                          │
│  ✓ Mata terbuka > 70%                                           │
│  ✓ Ekspresi netral (senyum < 30%)                               │
│                                                                 │
│  Confidence Score ≥ 80%?                                        │
└───────────────┬─────────────────────┬───────────────────────────┘
                │                     │
               YES                   NO
                │                     │
                ▼                     ▼
    ┌───────────────────┐   ┌─────────────────┐
    │  PROSES FOTO      │   │  SKIP FOTO      │
    │  (AI Processing)  │   │  (Tidak diproses)│
    └────────┬──────────┘   └─────────────────┘
             ▼
┌─────────────────────────────────────────────────────────────────┐
│  AI PROCESSING                                                  │
│  • Remove background (opsional)                                 │
│  • Enhance quality                                              │
│  • Adjust brightness/contrast                                   │
│  • Standard passport photo format                               │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  SIMPAN HASIL                                                   │
│  • Save ke folder: Pictures/PassPhotoProcessor/                 │
│  • Format nama: processed_[timestamp].jpg                       │
│  • Metadata: original filename, process date                    │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  LOG AKTIVITAS (Encrypted)                                      │
│  • Catat aktivitas ke log terenkripsi                           │
│  • Timestamp, status, metadata                                  │
│  • Tamper-evident chain (hash chain)                            │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│  KIRIM NOTIFIKASI                                               │
│  • "Foto pas berhasil diproses"                                 │
│  • Tap untuk lihat hasil                                        │
└───────────────────────────┬─────────────────────────────────────┘
                            ▼
                    ┌───────────────┐
                    │   SELESAI     │
                    │ (Loop kembali │
                    │ menunggu foto │
                    │    baru)      │
                    └───────────────┘
```

### Penjelasan Tiap Tahapan

#### 1️⃣ **Tahap Startup dan Keamanan**
Saat aplikasi pertama kali dibuka:
- Melakukan security checks untuk memastikan tidak ada ancaman
- Memeriksa apakah device di-root (berpotensi tidak aman)
- Mendeteksi debugger atau emulator (mencegah reverse engineering)
- Verifikasi signature APK (memastikan tidak dimodifikasi)

#### 2️⃣ **Tahap Permission**
Aplikasi meminta izin:
- **READ_MEDIA_IMAGES**: Untuk membaca foto di galeri
- **FOREGROUND_SERVICE**: Untuk menjalankan service di background
- **POST_NOTIFICATIONS**: Untuk menampilkan notifikasi hasil

#### 3️⃣ **Tahap Monitoring**
Aplikasi mulai memantau galeri:
- Menggunakan **ContentObserver** (bukan polling, jadi hemat baterai)
- Akan otomatis mendeteksi saat ada foto baru ditambahkan
- Berjalan di background service yang efisien

#### 4️⃣ **Tahap Deteksi AI**
Ketika ada foto baru:
- **ML Kit Face Detection** menganalisis foto
- Menghitung confidence score berdasarkan kriteria pas foto
- Hanya foto dengan score ≥ 80% yang akan diproses

#### 5️⃣ **Tahap Processing**
Foto yang terdeteksi sebagai pas foto:
- Diproses dengan AI untuk enhance quality
- Bisa remove background (jika ada vendor AI)
- Format disesuaikan dengan standar passport photo

#### 6️⃣ **Tahap Penyimpanan**
Hasil foto:
- Disimpan di folder khusus di galeri
- Metadata dicatat untuk tracking
- Activity log dienkripsi dan disimpan

---

## 🛠️ Persiapan yang Dibutuhkan

### A. Hardware Requirements

#### 1. **Komputer untuk Development**
   - **RAM**: Minimum 8GB, Rekomendasi 16GB
   - **Storage**: Minimum 20GB ruang kosong
   - **Processor**: Intel i5/AMD Ryzen 5 atau lebih baik
   - **OS**: Windows 10/11, macOS 10.14+, atau Linux (Ubuntu 20.04+)

#### 2. **Android Device (Ponsel)**
   - **Android Version**: Minimum Android 8.0 (API 26)
   - **RAM**: Minimum 4GB
   - **Storage**: Minimum 2GB ruang kosong
   - **Rekomendasi**: Device fisik (bukan emulator) untuk testing yang akurat
   - **Optional**: Vivo device jika ingin menggunakan Vivo AI features

### B. Software Requirements

#### 1. **Android Studio**
   - **Versi**: Android Studio Hedgehog (2023.1.1) atau yang lebih baru
   - **Download**: https://developer.android.com/studio
   - **Size**: ~1GB installer, ~5GB setelah instalasi dengan SDK

#### 2. **Java Development Kit (JDK)**
   - **Versi**: JDK 17 (Required!)
   - **Download**: https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html
   - **Alternatif**: OpenJDK 17 dari https://adoptium.net/

#### 3. **Android SDK**
   - **Target SDK**: Android 14 (API 34)
   - **Minimum SDK**: Android 8.0 (API 26)
   - **Build Tools**: 34.0.0
   - *(Akan otomatis di-download oleh Android Studio)*

#### 4. **Git**
   - **Untuk clone repository**
   - **Download**: https://git-scm.com/downloads

#### 5. **ADB (Android Debug Bridge)**
   - **Untuk testing di device fisik**
   - *(Sudah termasuk dalam Android SDK)*

### C. Account dan Credentials

#### 1. **Google Account**
   - Untuk download Android Studio dan SDK
   - Untuk akses Play Console (jika publish app)

#### 2. **GitHub Account** (Optional)
   - Jika ingin contribute atau fork repository

#### 3. **API Keys** (Jika Menggunakan Vendor AI)
   - API key dari vendor AI yang digunakan
   - Certificate pins untuk production server

### D. Pengetahuan yang Diperlukan

#### 1. **Basic Programming**
   - **Kotlin**: Bahasa utama aplikasi ini
   - **Java**: Untuk memahami Android SDK
   - **OOP Concepts**: Class, inheritance, interface

#### 2. **Android Development**
   - Activity dan Service lifecycle
   - Permission handling
   - Background processing
   - ContentProvider dan ContentObserver

#### 3. **Security Concepts** (Optional tapi Recommended)
   - Encryption (AES, RSA)
   - Certificate pinning
   - Key management
   - Secure coding practices

#### 4. **Machine Learning Basics** (Optional)
   - Face detection concepts
   - ML Kit usage
   - TensorFlow Lite basics

---

## 🚀 Langkah-langkah Setup

### Langkah 1: Install Software

#### A. Install JDK 17

**Windows:**
```bash
# Download JDK 17 dari Oracle atau Adoptium
# Jalankan installer
# Set JAVA_HOME environment variable:
# Control Panel → System → Advanced → Environment Variables
# Tambahkan:
JAVA_HOME = C:\Program Files\Java\jdk-17

# Tambahkan ke PATH:
%JAVA_HOME%\bin
```

**Mac:**
```bash
# Install via Homebrew
brew install openjdk@17

# Set JAVA_HOME di ~/.zshrc atau ~/.bash_profile
export JAVA_HOME=/usr/local/opt/openjdk@17
export PATH=$JAVA_HOME/bin:$PATH
```

**Linux:**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-17-jdk

# Verify
java -version
```

#### B. Install Android Studio

1. Download dari https://developer.android.com/studio
2. Jalankan installer
3. Pada setup wizard:
   - Pilih "Standard" installation
   - Accept all licenses
   - Tunggu download SDK dan tools (~3GB)

4. Setelah selesai, buka Android Studio
5. Install additional SDK:
   - Tools → SDK Manager
   - SDK Platforms: Android 14.0 (API 34)
   - SDK Tools: Android SDK Build-Tools 34.0.0

#### C. Install Git

**Windows:**
```bash
# Download dari https://git-scm.com/download/win
# Jalankan installer dengan default options
```

**Mac:**
```bash
# Git biasanya sudah terinstall
# Atau install via Homebrew:
brew install git
```

**Linux:**
```bash
sudo apt install git
```

### Langkah 2: Clone Repository

```bash
# Buat folder untuk project
mkdir ~/AndroidProjects
cd ~/AndroidProjects

# Clone repository
git clone https://github.com/alhamdy1/automation.git

# Masuk ke folder project
cd automation
```

### Langkah 3: Setup Android Device

#### A. Enable Developer Options di Ponsel

1. Buka **Settings** di ponsel Android
2. Scroll ke bawah, pilih **About Phone**
3. Tap **Build Number** 7 kali
4. Developer Options akan muncul di Settings

#### B. Enable USB Debugging

1. Kembali ke **Settings**
2. Pilih **Developer Options**
3. Enable **USB Debugging**
4. Enable **Install via USB** (jika ada)

#### C. Connect Device ke Komputer

```bash
# Colok ponsel ke komputer dengan USB cable
# Pastikan mode: "File Transfer" atau "MTP"

# Verify device terdeteksi
adb devices

# Output yang diharapkan:
# List of devices attached
# ABC123XYZ    device
```

### Langkah 4: Open Project di Android Studio

1. Buka Android Studio
2. **File → Open**
3. Navigate ke folder `automation`
4. Click **OK**
5. Tunggu Gradle sync (2-5 menit pertama kali)

### Langkah 5: Configure Project

#### A. Cek Gradle Settings

File `gradle.properties` sudah dikonfigurasi dengan baik, tapi pastikan:

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
```

#### B. Verify Dependencies

Buka `app/build.gradle` dan pastikan semua dependencies bisa di-resolve.

Jika ada error, lakukan:
```bash
# Di terminal Android Studio
./gradlew --refresh-dependencies
```

### Langkah 6: Build Project

#### A. Build Debug APK

```bash
# Via Terminal
./gradlew assembleDebug

# Atau via Android Studio:
# Build → Build Bundle(s) / APK(s) → Build APK(s)
```

**Output:**
```
BUILD SUCCESSFUL in 1m 23s
APK location: app/build/outputs/apk/debug/app-debug.apk
```

#### B. Install ke Device

```bash
# Via Terminal
adb install app/build/outputs/apk/debug/app-debug.apk

# Atau via Android Studio:
# Run → Run 'app' (Shift+F10)
```

### Langkah 7: Configure Security Features

#### A. Generate Keystore untuk Release

```bash
# Generate keystore
keytool -genkey -v -keystore my-release-key.keystore \
  -alias my-key-alias \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# Masukkan password dan informasi yang diminta
```

#### B. Update Certificate Pins (Untuk Production)

Jika menggunakan API server sendiri:

```bash
# Generate certificate pin
openssl s_client -servername yourdomain.com -connect yourdomain.com:443 | \
    openssl x509 -pubkey -noout | \
    openssl pkey -pubin -outform der | \
    openssl dgst -sha256 -binary | openssl enc -base64
```

Update file `app/src/main/res/xml/network_security_config.xml`:
```xml
<pin digest="SHA-256">YOUR_PIN_HERE=</pin>
```

#### C. Update Signing Certificate Hash

Setelah generate keystore, get certificate hash:

```bash
# Get SHA-256 dari keystore
keytool -list -v -keystore my-release-key.keystore \
  -alias my-key-alias | grep SHA256
```

Update di `TamperDetection.kt`:
```kotlin
private const val EXPECTED_SIGNATURE = "YOUR_SHA256_HERE"
```

### Langkah 8: Test Aplikasi

#### A. Grant Permissions

Setelah install, buka aplikasi:
1. Tap **Allow** untuk permission READ_MEDIA_IMAGES
2. Tap **Allow** untuk permission POST_NOTIFICATIONS

#### B. Start Monitoring

1. Tap tombol **Start Monitoring** di aplikasi
2. Service akan berjalan di background

#### C. Test dengan Foto

1. Ambil atau download foto pas (contoh: selfie formal, background putih)
2. Save foto ke galeri
3. Tunggu beberapa detik
4. Notifikasi akan muncul jika foto terdeteksi sebagai pas foto
5. Cek folder `Pictures/PassPhotoProcessor/` untuk hasil

---

## 📱 Cara Menggunakan

### Mode 1: Automatic Monitoring (Recommended)

**Langkah-langkah:**

1. **Buka Aplikasi**
   - Tap icon aplikasi di launcher

2. **Start Service**
   - Tap tombol "Start Monitoring"
   - Service akan berjalan di background
   - Icon notifikasi muncul di status bar

3. **Normal Usage**
   - Gunakan ponsel seperti biasa
   - Ambil foto atau download foto pas
   - Aplikasi akan otomatis mendeteksi dan memproses

4. **Cek Hasil**
   - Buka galeri
   - Masuk ke album "PassPhotoProcessor"
   - Lihat foto yang sudah diproses

5. **Stop Service** (Optional)
   - Buka aplikasi
   - Tap tombol "Stop Monitoring"
   - Service akan berhenti

### Mode 2: Manual Processing

**Langkah-langkah:**

1. **Select Photo**
   - Tap tombol "Select Photo" di aplikasi
   - Pilih foto dari galeri

2. **Process**
   - Aplikasi akan menganalisis foto
   - Jika terdeteksi sebagai pas foto, akan diproses
   - Jika bukan pas foto, akan ditolak

3. **View Result**
   - Hasil akan ditampilkan di aplikasi
   - Save ke galeri dengan tap "Save"

### Tips Penggunaan

✅ **DO's:**
- Gunakan foto dengan wajah jelas dan menghadap depan
- Background polos (putih/biru) untuk hasil terbaik
- Pencahayaan yang cukup
- Foto dalam resolusi minimal 600x800 pixels

❌ **DON'Ts:**
- Jangan gunakan foto grup (multi-person)
- Jangan gunakan foto dengan wajah miring
- Jangan gunakan foto selfie dengan filter
- Jangan gunakan foto blur atau gelap

---

## 🏗️ Arsitektur Sistem

### Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI LAYER                            │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ MainActivity│  │  Fragments │  │  Adapters  │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ ViewModels │  │   LiveData │  │   States   │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │  Use Cases │  │ Repositories│  │   Models   │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────┴────────────────────────────────────┐
│                       DATA LAYER                             │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │  Services  │  │  Managers  │  │  Storage   │            │
│  │            │  │            │  │            │            │
│  │ • Monitor  │  │ • Security │  │ • Keystore │            │
│  │ • Worker   │  │ • AI       │  │ • Database │            │
│  │ • Foreground│  │ • Network  │  │ • Files    │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└─────────────────────────────────────────────────────────────┘
```

### Component Details

#### 1. **UI Layer**
- **MainActivity**: Entry point aplikasi, handle permissions
- **Fragments**: Different screens (monitoring, logs, settings)
- **Adapters**: RecyclerView untuk display lists

#### 2. **Presentation Layer**
- **ViewModels**: Business logic untuk UI
- **LiveData**: Observable data untuk UI updates
- **States**: UI state management

#### 3. **Domain Layer**
- **Use Cases**: Specific business operations
- **Repositories**: Data access abstraction
- **Models**: Domain entities

#### 4. **Data Layer**

**Services:**
- `PhotoMonitorService`: Background monitoring service
- `PhotoProcessWorker`: WorkManager for processing

**Managers:**
- `PhotoMonitorManager`: Coordinate monitoring
- `KeystoreManager`: Secure token storage
- `CertificatePinningManager`: Network security
- `TamperDetection`: Security checks
- `PassPhotoDetector`: AI classification
- `VendorAIProcessor`: Vendor-specific AI
- `EncryptedLogger`: Secure logging

**Storage:**
- Android Keystore: Hardware-backed key storage
- EncryptedSharedPreferences: Encrypted preferences
- Room Database: SQLCipher encrypted database
- MediaStore: Photo storage via scoped storage

### Data Flow

```
User Action → ViewModel → UseCase → Repository → Service/Manager → Storage
                ↑                                                      ↓
                └──────────────── LiveData/Flow ──────────────────────┘
```

### Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                         │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   SECURITY LAYER                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Tamper     │  │  Certificate │  │   Keystore   │      │
│  │  Detection   │  │   Pinning    │  │   Manager    │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                  ENCRYPTION LAYER                            │
│  ┌──────────────────────────────────────────────────┐       │
│  │  AES-256-GCM Encryption                          │       │
│  │  • API Tokens                                    │       │
│  │  • Activity Logs                                 │       │
│  │  • Sensitive Data                                │       │
│  └──────────────────────────────────────────────────┘       │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   STORAGE LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Android    │  │  Encrypted   │  │   Scoped     │      │
│  │   Keystore   │  │   Database   │  │   Storage    │      │
│  │ (Hardware)   │  │ (SQLCipher)  │  │ (MediaStore) │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

---

## ❓ Troubleshooting

### Problem 1: Build Failed - SDK Not Found

**Error:**
```
SDK location not found
```

**Solution:**
```bash
# Buat file local.properties di root project
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# Path default:
# Windows: C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
# Mac: /Users/YourName/Library/Android/sdk
# Linux: /home/yourname/Android/Sdk
```

### Problem 2: Gradle Sync Failed

**Error:**
```
Gradle sync failed: Connection timeout
```

**Solution:**
```bash
# 1. Check internet connection
# 2. Use VPN jika Gradle repository di-block
# 3. Atau tambahkan mirror di build.gradle:

repositories {
    maven { url 'https://maven.aliyun.com/repository/google' }
    maven { url 'https://maven.aliyun.com/repository/jcenter' }
    google()
    mavenCentral()
}
```

### Problem 3: Device Not Detected

**Error:**
```
adb devices → List of devices attached
(empty)
```

**Solution:**
```bash
# 1. Install/Update USB drivers (Windows)
# Download dari manufacturer website

# 2. Restart ADB server
adb kill-server
adb start-server
adb devices

# 3. Check USB debugging is enabled di ponsel

# 4. Try different USB port or cable

# 5. Authorize computer di ponsel
# (Pop-up muncul saat pertama kali connect)
```

### Problem 4: Permission Denied Error

**Error:**
```
java.lang.SecurityException: Permission denied
```

**Solution:**
```bash
# 1. Check AndroidManifest.xml sudah declare permission
# 2. Request permission di runtime
# 3. Grant permission manual:

adb shell pm grant com.passphoto.processor android.permission.READ_MEDIA_IMAGES
adb shell pm grant com.passphoto.processor android.permission.POST_NOTIFICATIONS
```

### Problem 5: App Crashes on Start

**Error:**
```
App keeps stopping
```

**Solution:**
```bash
# 1. Check logcat untuk error detail
adb logcat | grep "com.passphoto.processor"

# 2. Clear app data
adb shell pm clear com.passphoto.processor

# 3. Uninstall dan reinstall
adb uninstall com.passphoto.processor
adb install app/build/outputs/apk/debug/app-debug.apk

# 4. Check minimum Android version (min API 26)
```

### Problem 6: Face Detection Not Working

**Problem:**
- Foto tidak terdeteksi sebagai pas foto

**Solution:**
1. **Check foto quality:**
   - Resolusi minimal 600x800
   - Format JPEG atau PNG
   - Tidak blur

2. **Check kriteria:**
   - Hanya 1 wajah
   - Wajah menghadap depan
   - Ukuran wajah cukup besar

3. **Test dengan foto sample:**
   - Download passport photo template
   - Test dengan foto yang pasti memenuhi kriteria

4. **Check logs:**
   ```bash
   adb logcat | grep "PassPhotoDetector"
   ```

### Problem 7: Service Stops Automatically

**Problem:**
- Background service berhenti sendiri

**Solution:**
1. **Disable battery optimization:**
   - Settings → Apps → Pass Photo Processor
   - Battery → Battery optimization → Don't optimize

2. **Enable autostart** (Xiaomi, Vivo, Oppo):
   - Settings → Autostart
   - Enable untuk Pass Photo Processor

3. **Check Doze mode:**
   ```bash
   # Disable doze untuk testing
   adb shell dumpsys deviceidle whitelist +com.passphoto.processor
   ```

### Problem 8: Certificate Pinning Failed

**Error:**
```
Certificate pinning failure
```

**Solution:**
```bash
# 1. Generate correct pin
openssl s_client -servername yourdomain.com -connect yourdomain.com:443 | \
    openssl x509 -pubkey -noout | \
    openssl pkey -pubin -outform der | \
    openssl dgst -sha256 -binary | openssl enc -base64

# 2. Update di network_security_config.xml

# 3. Untuk testing, disable pinning sementara
# (Jangan disable di production!)
```

### Problem 9: Out of Memory Error

**Error:**
```
java.lang.OutOfMemoryError
```

**Solution:**
```kotlin
// 1. Load foto dengan sample size
val options = BitmapFactory.Options().apply {
    inSampleSize = 2  // Load dengan 1/2 size
    inJustDecodeBounds = false
}

// 2. Increase heap size di AndroidManifest.xml
<application
    android:largeHeap="true">

// 3. Process foto dalam background thread
// 4. Release bitmap setelah digunakan
bitmap.recycle()
```

### Problem 10: ProGuard Obfuscation Issues

**Error:**
```
ClassNotFoundException after release build
```

**Solution:**
```proguard
# Tambahkan di proguard-rules.pro

# Keep security classes
-keep class com.passphoto.processor.security.** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }

# Keep models
-keep class com.passphoto.processor.**.model.** { *; }
```

---

## 📞 Bantuan Lebih Lanjut

### Resources

1. **Documentation**
   - Android Developer: https://developer.android.com
   - Kotlin: https://kotlinlang.org
   - ML Kit: https://developers.google.com/ml-kit

2. **Community**
   - Stack Overflow: Tag `android` dan `kotlin`
   - Android Developers subreddit: r/androiddev
   - Kotlin Slack: https://kotlinlang.slack.com

3. **Repository Issues**
   - GitHub Issues untuk bug reports
   - GitHub Discussions untuk questions

### Contact

Jika menemukan security vulnerability:
- **JANGAN** buat public issue
- Email langsung ke maintainer

---

## 📝 Checklist Setup

Gunakan checklist ini untuk memastikan setup lengkap:

### Software Installation
- [ ] JDK 17 installed dan JAVA_HOME set
- [ ] Android Studio installed
- [ ] Android SDK 34 installed
- [ ] Git installed
- [ ] ADB working (test dengan `adb devices`)

### Project Setup
- [ ] Repository cloned
- [ ] Project opened di Android Studio
- [ ] Gradle sync successful
- [ ] Dependencies resolved

### Device Setup
- [ ] USB debugging enabled
- [ ] Device connected dan detected
- [ ] Authorization granted

### Build & Install
- [ ] Debug build successful
- [ ] App installed ke device
- [ ] Permissions granted
- [ ] App runs without crash

### Testing
- [ ] Service starts successfully
- [ ] ContentObserver working
- [ ] Face detection working
- [ ] Photo processing working
- [ ] Hasil foto saved correctly

### Security Configuration (Production)
- [ ] Keystore generated
- [ ] Certificate pins updated
- [ ] Signature hash updated
- [ ] ProGuard rules configured
- [ ] Release build tested

---

## 🎯 Next Steps

Setelah setup selesai, Anda bisa:

1. **Customize UI**
   - Ubah warna dan tema
   - Tambah fitur tambahan
   - Improve UX

2. **Integrate Vendor AI**
   - Setup Vivo AI jika ada
   - Atau integrate vendor AI lain
   - Test dengan different devices

3. **Add Features**
   - Batch processing
   - Cloud backup
   - Share functionality
   - Photo editing tools

4. **Optimize Performance**
   - Reduce battery usage
   - Improve detection accuracy
   - Faster processing

5. **Production Deploy**
   - Setup release keystore
   - Configure ProGuard
   - Test on multiple devices
   - Prepare for Play Store

---

## ✅ Summary

**Proyek ini adalah:**
Aplikasi Android untuk auto-process foto pas dengan keamanan tingkat tinggi

**Yang Anda butuhkan:**
- Komputer dengan Android Studio
- Ponsel Android 8.0+
- JDK 17
- Pengetahuan dasar Kotlin/Android

**Langkah utama:**
1. Install software (Android Studio, JDK)
2. Clone repository
3. Setup device
4. Build & install
5. Test aplikasi

**Hasil:**
Aplikasi yang bisa otomatis detect dan process foto pas di galeri ponsel

---

*Dokumen ini dibuat untuk membantu pemula memahami dan setup Pass Photo Processor project*
