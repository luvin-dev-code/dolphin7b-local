# 🐬 Dolphine 7B Local — Offline Android AI Chat

**Dolphine 7B Local** adalah aplikasi chat AI Android yang berjalan **100% secara lokal (offline)** di perangkat Anda menggunakan engine native C++ **`llama.cpp` (`arm64-v8a`)**. Seluruh sesi percakapan disimpan secara otomatis ke database lokal (SQLite) dan dilengkapi dengan pengaturan **Inference & RAM Settings** (CPU Threads, Context Window, dan Max Reply Tokens) untuk menyesuaikan performa sesuai kapasitas RAM perangkat Anda.

---

## 📋 Persyaratan Sistem & Perangkat (System Requirements)

Bagi pengguna yang mengunduh dan menginstal file **APK** dari halaman **GitHub Releases**, pastikan perangkat Android Anda memenuhi persyaratan berikut agar model AI 7B dapat berjalan dengan lancar dan tidak mengalami *Out of Memory (Force Close)*:

### 1. Spesifikasi Perangkat (Hardware & OS)
| Komponen | Persyaratan Minimum | Rekomendasi Optimal |
| :--- | :--- | :--- |
| **Sistem Operasi (OS)** | Android 8.0 (Oreo / API Level 26) ke atas | Android 11.0 (API 30) – Android 15 (API 35) |
| **Arsitektur CPU (ABI)** | **Wajib 64-bit ARM (`arm64-v8a` / AArch64)** | Octa-core `arm64-v8a` (Snapdragon seri 7/8, Dimensity, Exynos 64-bit) |
| **Kapasitas RAM** | **Minimal 6 GB RAM fisik** (+ 4 GB Virtual RAM / RAM Plus aktif) | **8 GB – 12 GB+ RAM fisik** (+ 6–8 GB Virtual RAM / Swap) |
| **Ruang Penyimpanan Kosong** | Minimal **5 GB ruang kosong** di penyimpanan internal | **8 GB+ ruang kosong** (UFS 3.1 / UFS 4.0 mempercepat *loading* model) |

> ⚠️ **Penting:** Aplikasi ini menggunakan pustaka native `libllama-android.so` yang dikompilasi khusus untuk arsitektur **`arm64-v8a`**. Perangkat 32-bit lama (`armeabi-v7a`) atau emulator x86 tanpa translasi ARM64 tidak didukung.

---

## 📦 Persyaratan File Model AI (`dolphin-7b.gguf`)

Aplikasi **APK tidak menyertakan file model berukuran ~4 GB di dalam installer** agar ukuran APK tetap ringan. Anda **wajib** menyiapkan file model berformat **`.gguf`** secara manual di penyimpanan internal ponsel Anda:

1. **Unduh Model Dolphin 7B (Format GGUF)**  
   Unduh salah satu varian kuantisasi GGUF dari HuggingFace (misalnya *Dolphin 2.x Mistral 7B GGUF* atau model berbasis Llama/Mistral GGUF lainnya):
   - **Rekomendasi untuk RAM 6 GB – 8 GB:** Kuantisasi **`Q4_0`** atau **`Q4_K_S`** (~3.8 GB – 4.1 GB)
   - **Rekomendasi untuk RAM 8 GB – 12 GB+:** Kuantisasi **`Q4_K_M`** (~4.37 GB)
2. **Ubah Nama File Model (Wajib Persis)**  
   Ubah nama file model yang sudah diunduh menjadi:
   ```text
   dolphin-7b.gguf
   ```
3. **Pindahkan ke Folder Utama Penyimpanan Internal (`/sdcard/`)**  
   Letakkan file `dolphin-7b.gguf` tepat di luar folder (root *Internal Shared Storage*), sehingga jalurnya adalah:
   ```text
   /sdcard/dolphin-7b.gguf
   ```
   *(atau setara dengan `/storage/emulated/0/dolphin-7b.gguf` pada aplikasi File Manager Android).*

---

## 🔐 Persyaratan Izin Aplikasi (Permissions)

Saat pertama kali membuka aplikasi **Dolphine 7B Local**:
- **Android 11 ke atas (API 30+):** Aplikasi akan meminta izin **All Files Access (`MANAGE_EXTERNAL_STORAGE`)**. Aktifkan toggle izin untuk **Dolphine 7B Local** agar engine C++ `llama.cpp` dapat membaca file `/sdcard/dolphin-7b.gguf` secara langsung melalui *memory-mapping (`mmap`)*.
- **Android 8.0 – Android 10 (API 26–29):** Izinkan akses **Read/Write External Storage** pada dialog pop-up yang muncul.

---

## ⚙️ Panduan Pengaturan Performa & RAM (Inference & RAM Settings)

Buka menu samping (**ikon garis 3 di kiri atas**) lalu tekan tombol **Inference & RAM Settings** yang berada permanen di bagian paling bawah panel:

- **Perangkat dengan RAM 6 GB:**
  - **CPU Threads:** `2` – `4` Threads
  - **Context Window:** `256` atau `512` Tokens (menghemat KV Cache RAM hingga ~75%)
  - **Max Reply Length:** `64` – `128` Tokens
- **Perangkat dengan RAM 8 GB ke atas:**
  - **CPU Threads:** `4` – `6` Threads (sesuaikan dengan jumlah *Performance Cores* CPU Anda)
  - **Context Window:** `512` – `2048` Tokens
  - **Max Reply Length:** `128` – `256` Tokens

---

## 🇬🇧 English Quick Summary (Requirements for APK Users)

If you download the **Release APK**, please ensure the following before running the app:
1. **Device Requirements:**
   - **OS:** Android 8.0 (API 26) or newer.
   - **ABI:** 64-bit ARM (`arm64-v8a`) processor required.
   - **RAM:** Minimum **6 GB RAM** (8 GB+ physical RAM + Virtual RAM/Swap strongly recommended for 7B parameters).
   - **Free Storage:** At least **5 GB** of free internal storage space.
2. **Model File Setup (`/sdcard/dolphin-7b.gguf`):**
   - Download a quantized 7B `.gguf` model (recommended: `Q4_0` or `Q4_K_M`, ~4.1 GB).
   - Rename the file strictly to **`dolphin-7b.gguf`**.
   - Place it in the root directory of your internal storage: **`/sdcard/dolphin-7b.gguf`** (`/storage/emulated/0/dolphin-7b.gguf`).
3. **Storage Permission:**
   - Grant **All Files Access (`MANAGE_EXTERNAL_STORAGE`)** when prompted on first launch so the native `llama.cpp` engine can load `/sdcard/dolphin-7b.gguf`.
4. **Features Included:**
   - **100% Offline Local Inference** powered by `llama.cpp` (`arm64-v8a` JNI).
   - **Automatic Local SQLite Chat Session History** (auto-titled from your first prompt with last-updated timestamp and confirmation dialog before deleting sessions).
   - **Customizable Inference & RAM Settings** pinned at the bottom of the navigation drawer to tune CPU Threads (`1–8`), Context Window (`256–2048` tokens), and Max Reply Tokens (`32–512` tokens).
