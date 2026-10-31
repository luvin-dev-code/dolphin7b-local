<div align="center">
  <h1>Dolphine 7B Local</h1>
  <p><strong>Native On-Device LLM Inference Client for Android</strong></p>

  <p>
    <a href="#system-requirements"><img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+" /></a>
    <a href="#system-requirements"><img src="https://img.shields.io/badge/ABI-arm64--v8a-0091BD?style=for-the-badge&logo=arm&logoColor=white" alt="ARM64" /></a>
    <a href="#architecture--core-capabilities"><img src="https://img.shields.io/badge/Backend-llama.cpp%20JNI-00599C?style=for-the-badge&logo=cplusplus&logoColor=white" alt="llama.cpp" /></a>
    <a href="#architecture--core-capabilities"><img src="https://img.shields.io/badge/Persistence-SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white" alt="SQLite" /></a>
  </p>

  <p>
    <a href="#overview">Overview</a> &bull;
    <a href="#architecture--core-capabilities">Architecture</a> &bull;
    <a href="#system-requirements">System Requirements</a> &bull;
    <a href="#deployment--model-provisioning">Model Provisioning</a> &bull;
    <a href="#runtime-configuration--memory-tuning">Memory Tuning</a>
  </p>
</div>

---

## Overview

**Dolphine 7B Local** is a standalone Android client engineered to execute 7-billion-parameter Large Language Models **100% offline**. Built atop a native C++ **`llama.cpp` (`arm64-v8a`)** JNI execution layer, the application performs local inference directly on mobile silicon without external API calls or cloud telemetry. 

Conversation states are automatically committed to an on-device SQLite database, while an integrated **Inference & RAM Settings** controller provides low-level control over CPU thread allocation, context window sizing, and token generation limits to prevent Out-Of-Memory (OOM) terminations across varying mobile memory tiers.

---

## Architecture & Core Capabilities

* **Zero-Network Native Inference:** Executes GGUF-quantized models entirely on-device via `libllama-android.so` compiled for 64-bit ARM (`arm64-v8a`), leveraging `mmap` for zero-copy weight loading.
* **Transactional SQLite Session Persistence:** Automatically generates session titles from initial user prompts, records modification timestamps, and enforces confirmation safeguards prior to session deletion.
* **Runtime Hardware & KV-Cache Governance:** Exposes real-time engine parameters pinned directly to the navigation drawer footer, allowing dynamic adjustment of **CPU Threads (`1–8`)**, **Context Window (`256–2048` tokens)**, and **Max Reply Tokens (`32–512` tokens)**.

---

## System Requirements

**Vverify that your device** meets the hardware constraints required to sustain a 7B parameter model in memory:

| Subsystem | Minimum Baseline | Recommended Target |
| :--- | :--- | :--- |
| **Operating System** | Android 8.0 (Oreo / API Level 26) | Android 11.0 (API 30) – Android 15 (API 35) |
| **Instruction Set (ABI)** | **`arm64-v8a` (64-bit AArch64)** | Octa-core `arm64-v8a` (Snapdragon 7/8 series, Dimensity, Exynos 64-bit) |
| **System Memory (RAM)** | **6 GB Physical RAM** (+ 4 GB Virtual RAM / ZRAM Swap) | **8 GB – 12 GB+ Physical RAM** (+ 6–8 GB Virtual RAM / Swap) |
| **Internal Storage** | **5 GB** unallocated storage space | **8 GB+** unallocated space on UFS 3.1 / UFS 4.0 flash storage |

> [!CAUTION]
> **Binary Compatibility Notice:** The native inference library (`libllama-android.so`) is compiled exclusively for the **`arm64-v8a`** target ABI. Legacy 32-bit ARM (`armeabi-v7a`) devices and unassisted `x86`/`x86_64` emulators are unsupported and will fail at library initialization.

---

## Deployment & Model Provisioning

To maintain a minimal binary footprint, the **release APK does not bundle the ~4 GB model weights**. Model provisioning must be completed manually on the target device prior to initial inference.

### 1. Acquire Quantized GGUF Weights
Download a 7B GGUF model artifact from HuggingFace (e.g., *Dolphin 2.x Mistral 7B GGUF* or any compatible Llama/Mistral GGUF derivative) matched to your device's physical memory ceiling:

| Device Memory Tier | Recommended Quantization | Approximate Footprint |
| :--- | :--- | :--- |
| **6 GB – 8 GB Physical RAM** | `Q4_0` or `Q4_K_S` | ~3.80 GB – 4.10 GB |
| **8 GB – 12 GB+ Physical RAM** | `Q4_K_M` | ~4.37 GB |

### 2. Standardize File Naming
Rename the downloaded model artifact to the exact filename expected by the native loader:

```text
dolphin-7b.gguf
```

### 3. Provision to Root Internal Storage
Transfer `dolphin-7b.gguf` to the root directory of the device's primary shared storage (`/sdcard/`):

```text
/sdcard/dolphin-7b.gguf
```

> [!NOTE]
> Depending on the Android File Manager implementation, the `/sdcard/` symlink resolves to `/storage/emulated/0/dolphin-7b.gguf`. Ensure the file is placed directly in the root directory and not nested inside `Download/` or sub-directories.

### 4. Grant Filesystem Permissions
Upon initial execution, the application requires explicit filesystem access to map `/sdcard/dolphin-7b.gguf` into virtual memory:

* **Android 11+ (API 30+):** When redirected to system settings, enable **All Files Access (`MANAGE_EXTERNAL_STORAGE`)** for **Dolphine 7B Local** to permit direct POSIX `mmap` access by the C++ backend.
* **Android 8.0 – 10 (API 26–29):** Approve the standard runtime prompt for **Read/Write External Storage**.

---

## Runtime Configuration & Memory Tuning

Access the hardware configuration panel by opening the primary navigation drawer (**top-left menu icon**) and selecting **Inference & RAM Settings** anchored at the bottom of the drawer. 

Tune the parameters below according to your hardware profile to balance token generation throughput against memory pressure:

| Engine Parameter | 6 GB RAM Configuration | 8 GB – 12 GB+ RAM Configuration | Architectural Impact |
| :--- | :--- | :--- | :--- |
| **CPU Threads** | `2` – `4` Threads | `4` – `6` Threads | Aligns compute threads with physical *Performance Cores* to prevent thermal throttling and scheduler contention. |
| **Context Window** | `256` – `512` Tokens | `512` – `2048` Tokens | Restricting context to `256`/`512` reduces KV-Cache memory allocation by up to ~75%, mitigating OOM crashes. |
| **Max Reply Length** | `64` – `128` Tokens | `128` – `256` Tokens | Defines the upper bound of tokens generated per single inference turn. |
