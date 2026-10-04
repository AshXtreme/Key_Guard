# KeyGuard 🛡️
### *Cybersecure, Air-Gapped, Multilingual Android IME*

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_API_28--35-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Native Core](https://img.shields.io/badge/Native_Core-C%2B%2B20_%7C_Oboe_1.9.0-00599C.svg?logo=c%2B%2B&logoColor=white)](https://github.com/google/oboe)
[![On-Device ML](https://img.shields.io/badge/STT-Sherpa--ONNX_Zipformer_INT8-FF6F00.svg)](https://github.com/k2-fsa/sherpa-onnx)
[![Security Standard](https://img.shields.io/badge/Security-OWASP_MASVS_Compliant-red.svg)](https://mas.owasp.org/)
[![Accessibility](https://img.shields.io/badge/Accessibility-WCAG_2.1_AA-purple.svg)](https://www.w3.org/WAI/WCAG21/quickref/)

---

## 1. Project Mission & Overview

**KeyGuard** is an open-source, cybersecure Android Input Method Editor (IME) engineered for zero-trust environments. Modern mobile keyboards systematically harvest user keystrokes, personal communications, and sensitive credentials for ad targeting and cloud telemetry.

KeyGuard redefines mobile typing security through an **air-gapped core architecture**:
- **0 Bytes Exfiltrated:** Pure on-device operation. `android.permission.INTERNET` is excluded from core keyboard processes.
- **Physical Keyboard Fidelity:** Sub-15ms acoustic response powered by Google Oboe and C++20 lock-free ring buffers.
- **NIST SP 800-88 Sanitization:** RAM-only transient buffers wiped with in-place zero-filling immediately upon consumption or field switching.
- **Hardware Cryptography:** Dual-tier clipboard protected by Android Keystore StrongBox HSM and SQLCipher AES-256-GCM.
- **Client-Side DLP:** Real-time pre-flight inspection with mathematical Luhn checksum validation preventing sensitive credential leakage.

---

## 2. Architectural Pillars

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                KEYGUARD SYSTEM TOPOGRAPHY                               │
└─────────────────────────────────────────────────────────────────────────────────────────┘

 [ ZONE 1: TOP COMMAND BAR ]  ──► Switch Profiles | Dictation State | Shield Status (ZERO-NET)
 [ ZONE 2: CONTEXT RIBBON  ]  ──► Dynamic Carousel (Industrial Matte / Glassmorphic) | DLP Warnings
 [ ZONE 3: PRIMARY MATRIX  ]  ──► WCAG 2.1 AA 48x48dp Touch Targets (QWERTY / Numeric / Symbols)
                                             │
                      ┌──────────────────────┴──────────────────────┐
                      ▼                                             ▼
       [ AIR-GAPPED CORE PIPELINE ]                  [ GATED TRANSLATION GATEWAY ]
     (100% On-Device • Zero Network)                (Manual Trigger • DLP Sanitized)
                      │                                             │
      ┌───────────────┼───────────────┐                             ▼
      ▼               ▼               ▼                  [ Client-Side DLP Inspector ]
[ C++20 OBOE ]  [ HAPTIC DRIVER ]  [ SHERPA-ONNX ]       (DlpSanitizer.kt)
 Sub-15ms        Category Tap       Zipformer INT8                  │
 Audio Engine    Actuation          16kHz Mono VAD       ├── Credit Cards (Luhn Mod-10)
      │               │               │                  ├── Private Keys (PEM / SSH)
      ▼               ▼               ▼                  ├── Cloud Tokens (API / Bearer)
[ Hardware     [ Hardware      [ In-Process              └── Identifiers (SSN / Aadhaar)
  DAC/Amp ]      Vibrator ]      Inference ]                        │
                                                                    ├── [ VIOLATION DETECTED ] ──► ABORT & Alert
                                                                    └── [ CLEAN PAYLOAD ]     ──► CTranslate2 INT8
```

### 1. Air-Gapped Core & Anti-Keylogger Isolation
- Operating system keystrokes, audio buffers, and transient prediction text remain strictly within volatile application memory.
- `InputMethodService` continuously monitors `EditorInfo.inputType`. When entering password, PIN, or credential fields (`TYPE_TEXT_VARIATION_PASSWORD`, `IME_FLAG_NO_PERSONALIZED_LEARNING`), KeyGuard locks down:
  - Activates high-contrast visual security banner (`#7F1D1D`).
  - Disables clipboard suggestion ribbons.
  - Hard-disables the microphone and STT engine.
  - Flushes and zeroizes all transient prediction memory.

### 2. Sub-15ms Mechanical Sound Engine
- High-performance native audio bridge implemented in **C++20** utilizing **Google Oboe 1.9.0** via Android Prefab.
- Configured with `SharingMode::Exclusive`, `PerformanceMode::LowLatency`, and `AudioFormat::I16`.
- Atomic read-head circular ring buffer (`std::atomic<size_t>`) guaranteeing sub-15ms touch-to-sound latency with zero garbage collector pauses.
- Profile switching between mechanical switch sound profiles (Cherry MX Blue click, MX Brown bump, MX Red linear, and IBM Model M buckling spring).

### 3. Intelligent Dual-Tier Clipboard
- **Tier 1 (RAM-Only Ring Buffer):** In-memory circular buffer storing up to 50 ephemeral items. Evaluated with 24-hour TTL; wiped on reboot with zero disk footprint. Eviction invokes NIST SP 800-88 compliant `\0` memory overwrite.
- **Tier 2 (Hardware-Encrypted Vault):** SQLCipher database encrypted via **AES-256-GCM**. Database passphrases derive from the **Android Keystore StrongBox HSM** (with standard TEE fallback). Row deletion executes cryptographic zero-fill prior to SQLite record removal.

### 4. On-Device Streaming Speech-to-Text (STT)
- Quantized **Sherpa-ONNX Zipformer INT8** running strictly in-process on CPU threads.
- Continuous 16kHz Mono 16-bit PCM capture via `AudioRecord` with energy-based Voice Activity Detection (VAD) and 300ms hangover smoothing.
- Raw PCM buffers zero-wiped with `Arrays.fill(pcmBuffer, 0)` immediately after acoustic feature extraction.
- Continuous voice command interception:
  - `"delete last word"` / `"backspace"` $\rightarrow$ Deletes surrounding text before the cursor.
  - `"new line"` / `"enter"` $\rightarrow$ Commits newline character.
  - `"space"` $\rightarrow$ Commits single space.
  - `"clear all"` $\rightarrow$ Clears active text target.

### 5. Isolated Translation Gateway & Client-Side DLP
- **Manual Intent Required:** Continuous background translation is strictly prohibited. Translation triggers solely on explicit user action.
- **Deterministic DLP Inspection (`DlpSanitizer`):** Pre-flight scanning inspects every payload before transmission:
  - **Financial Cards:** Regex extraction of 13–19 digit sequences + mathematical **Luhn (Mod 10)** checksum verification.
  - **Cryptographic Keys & PEM Blocks:** OpenSSH, RSA, EC private keys, and X.509 certificates.
  - **Cloud Tokens & Secrets:** AWS access keys (`AKIA...`), GitHub personal access tokens (`ghp_...`), Google API keys (`AIza...`), Slack tokens, Stripe live keys, and JSON Web Tokens (JWT `eyJ...`).
  - **Government Identifiers:** US Social Security Numbers (`AAA-GG-SSSS` with SSA validation) and Indian Aadhaar (12 digits).

---

## 3. Technology Stack & Free/Open-Source Principles

KeyGuard is 100% **Free and Open-Source Software (FOSS)**. No paid APIs, proprietary SDKs, or cloud subscriptions are used.

| Tier | Technology | License | Purpose |
| :--- | :--- | :--- | :--- |
| **Language & Platform** | Kotlin 2.0+ / Android API 28–35 | Apache 2.0 | Core IME service and UI state |
| **Native Sound Engine** | C++20 / CMake 3.22+ / Google Oboe 1.9.0 | Apache 2.0 | Low-latency audio ring buffer |
| **User Interface** | Jetpack Compose (BOM 2024.10) / Material 3 | Apache 2.0 | WCAG 2.1 AA accessible keycaps & ribbons |
| **Encrypted Storage** | SQLCipher Community Edition / AndroidX Room | BSD-style | Hardware-backed local credential vault |
| **Offline Speech** | Sherpa-ONNX (Next-gen Kaldi / k2-fsa) | Apache 2.0 | Local streaming speech recognition |
| **Hardware Root of Trust** | Android Keystore / StrongBox Keymaster | AOSP Native | Hardware-backed cryptographic keys |

---

## 4. Prerequisites & Environment Setup

Ensure your local development environment meets the following specifications:

- **Operating System:** macOS, Linux, or Windows 11 (64-bit)
- **JDK:** OpenJDK 17 or Amazon Corretto 17
- **Android Studio:** Ladybug (2024.2.1+) or newer
- **Android SDK:** Platform API 35 (Android 15)
- **Android NDK:** Version `26.1.10909125` or `30.0.15729638`
- **CMake:** Version `3.22.1` or newer

---

## 5. Build & Installation Instructions

### 1. Clone the Repository
```bash
git clone https://github.com/AshXtreme/Key_Guard.git
cd KeyGuard
```

### 2. Configure Local Environment
Ensure `local.properties` specifies your Android SDK and NDK paths:
```properties
sdk.dir=/Users/<username>/Library/Android/sdk
ndk.dir=/Users/<username>/Library/Android/sdk/ndk/30.0.15729638
```

### 3. Build & Run Test Suite
Execute the full unit test suite (covering DLP sanitizer, Luhn checksums, audio streamer, and SQLCipher migration):
```bash
./gradlew test
```

### 4. Assemble Debug APK
Compile C++ Oboe native libraries and Kotlin Compose sources:
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### 5. Install via ADB
Connect an Android device (API 28–35) with USB Debugging enabled:
```bash
# Install the debug package
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Enable KeyGuard in system Input Method settings
adb shell ime enable com.keyguard.ime/.KeyGuardService

# Set KeyGuard as the active keyboard
adb shell ime set com.keyguard.ime/.KeyGuardService
```

---

## 6. Verification & Security Testing

### 1. Airplane Mode Audio & STT Test
Verify that offline neural speech decoding and mechanical audio operate with zero network connectivity:
```bash
# Enable Airplane Mode
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true

# Grant audio record permission
adb shell pm grant com.keyguard.ime android.permission.RECORD_AUDIO

# Verify network is unreachable
adb shell ping -c 1 8.8.8.8
```
1. Open any messaging or notes application.
2. Tap the microphone icon (`🎤`) in Zone 1.
3. Transcribe speech and speak voice commands (`"new line"`, `"delete last word"`).

### 2. Anti-Keylogger Password Field Lockdown
1. Navigate to a password entry screen (e.g., Settings $\rightarrow$ Security $\rightarrow$ Set Screen Lock).
2. Verify that Zone 1 changes the banner to:
   ```
   🛡️ AIR-GAP SECURE MODE • CACHING DISABLED
   ```
3. Verify that the microphone button displays a lock icon (`🔒`), is non-clickable, and memory buffers are purged.

---

## 7. Security & Compliance Standards

- **OWASP MASVS Compliance:**
  - `MASVS-STORAGE-1`: Sensitive data is never written to unencrypted storage; Tier 1 clipboard lives solely in RAM.
  - `MASVS-CRYPTO-1`: Encryption uses hardware-backed AES-256-GCM via Android Keystore StrongBox HSM.
  - `MASVS-NETWORK-1`: Typing and audio engines do not declare or request `android.permission.INTERNET`.
- **NIST SP 800-88 Sanitization:**
  - All temporary short-term clipboard arrays, raw audio PCM frames, and transient keystroke buffers are overwritten with `\0` in memory prior to garbage collection.
- **WCAG 2.1 AA Compliance:**
  - All interactive keycaps enforce minimum 48 $\times$ 48 dp physical touch targets.
  - Full screen-reader support via Jetpack Compose `SemanticsProperties.contentDescription`.

---

## 8. License

KeyGuard is distributed under the **Apache License, Version 2.0**.  
See [LICENSE](LICENSE) for the full license text.

```
Copyright 2026 KeyGuard Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
