# Product Requirements Document (PRD): Project KeyGuard
**Document ID:** PRD-KEYGUARD-SYS-002 (Enhanced Edition)[cite: 9]  
**Target Platform:** Android 9.0+ (API Level 28 to 35) & iOS 14.0+[cite: 9]  
**Architecture Paradigm:** Zero-Trust, Air-Gapped Core, Isolated Egress Gateway[cite: 9]  
**Compliance Standards:** GDPR, CCPA, WCAG 2.1 AA, OWASP Mobile Top 10[cite: 9]  
**Build System:** Gradle (Kotlin DSL), Android NDK (C++20 / CMake), Jetpack Compose / Swift 5.10+, SwiftUI[cite: 9]  

---

## Executive Review & Gap Analysis Summary[cite: 9]

### Critical Gaps Identified & Addressed[cite: 9]
* **Testing Strategy:** Established a multi-layer test pyramid (Unit, Integration, Security E2E, Latency Benchmarks) with mandatory coverage metrics (>85%)[cite: 9].
* **Error Handling & Resiliency:** Integrated a comprehensive failure matrix covering model eviction, audio buffer underruns, network timeouts, and Jetsam recovery[cite: 9].
* **Backend Model Inference Optimization:** Replaced monolithic NLLB-200 with a two-tiered inference pipeline (quantized CTranslate2 / TensorRT-LLM on vLLM) meeting the strict `<450ms` SLA[cite: 9].
* **Privacy-Preserving Observability:** Implemented differential privacy, local client metrics aggregations, and zero-PII Prometheus/OpenTelemetry logging[cite: 9].
* **Version Consolidation:** Consolidated redundant PRD copies into a single authoritative master specification with an integrated developer runbook[cite: 9].

### High & Medium Priorities Incorporated[cite: 9]
* **Accessibility (WCAG 2.1 AA):** Added screen-reader semantics (TalkBack/VoiceOver), minimum 48x48dp touch targets, high-contrast visual modes, and non-auditory haptic cues[cite: 9].
* **Regulatory Compliance:** Full data-subject rights (GDPR/CCPA/HIPAA), cryptographic verifiable erasure, and local privacy disclosure flows[cite: 9].
* **UX & Edge-Case Resilience:** Handled network transitions, clipboard tier promotion/demotion, and seamless system IME switching[cite: 9].
* **API Rate Limiting & DDoS Defense:** Dual-tier rate limiting (100 req/min per IP via Token Bucket in Envoy) with HMAC request signing[cite: 9].
* **System Operations:** Kubernetes multi-region HPA deployment, zero-downtime rolling upgrades, and explicit API semantic versioning[cite: 9].

---

## 1. System Mission & Agent Operating Directives[cite: 9]

### 1.1 Mission Statement[cite: 9]
Build an enterprise-grade, privacy-first mobile keyboard (Input Method Editor) delivering mechanical switch acoustics, fine-grained tactile haptics, fully air-gapped on-device speech-to-text, and hardware-encrypted dual-tier clipboard management[cite: 9]. The core typing execution path must remain strictly air-gapped; only explicit translation requests initiated manually by the user are permitted to communicate over outbound network sockets[cite: 9].

### 1.2 Agent Guardrails & Invariants[cite: 9]
* **INVARIANT 1 (Air-Gapped Core):** The primary keyboard IME service runs with zero background network socket permissions[cite: 9]. Cloud-bound egress resides exclusively in an isolated network client invoked only by explicit user action[cite: 9].
* **INVARIANT 2 (Acoustic Latency):** Keystroke-to-audio latency must not exceed 15ms[cite: 9]. Standard Android Java audio APIs (`SoundPool`, `MediaPlayer`) are strictly prohibited; native C++ Google Oboe (AAudio/OpenSL ES) is mandatory on Android[cite: 9].
* **INVARIANT 3 (Memory Sanitization):** Audio PCM arrays, sensitive password buffers, and clipboard payloads must be explicitly wiped from volatile memory using `memset` or `Arrays.fill` immediately after consumption[cite: 9].
* **INVARIANT 4 (Zero-Logging of Passwords):** When `EditorInfo.inputType` or `UITextInputTraits.isSecureTextEntry` indicates a password, PIN, or credential field, clipboard caching, dictation listeners, and external network calls must hard-disable immediately[cite: 9].
* **INVARIANT 5 (Verified Dependencies):** All libraries, Gradle dependencies, and native symbols must correspond to verified, active distributions with clean SCA (Software Composition Analysis) scans[cite: 9].

---

## 2. Technical Stack Specification[cite: 9]

| Component | Android Stack[cite: 9] | iOS Stack[cite: 9] | Backend Infrastructure[cite: 9] |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin (Coroutines, Flow) + C++20 (NDK)[cite: 9] | Swift 5.10+ (Modern Concurrency)[cite: 9] | Go 1.23 (Gateway) / Python 3.11 (PyTorch)[cite: 9] |
| **UI Framework** | Jetpack Compose (`AbstractComposeView`)[cite: 9] | SwiftUI / Custom UIKit Keyboard Views[cite: 9] | React 19 (Internal Metrics Admin)[cite: 9] |
| **Audio Engine** | Google Oboe (AAudio/OpenSL ES, low-latency)[cite: 9] | AudioToolbox PCM SystemSound / CoreHaptics[cite: 9] | N/A[cite: 9] |
| **Speech-to-Text** | Sherpa-ONNX (Streaming Zipformer INT8)[cite: 9] | System Dictation Delegation (`hasDictationKey`)[cite: 9] | Whisper.cpp (Optional fallback container)[cite: 9] |
| **Translation Engine** | Local CTranslate2 INT8 (High-frequency pairs)[cite: 9] | Local Apple Translation Framework[cite: 9] | vLLM / CTranslate2 (CTranslate2 NLLB / MarianMT)[cite: 9] |
| **Local Database** | Room ORM + SQLCipher (AES-256-GCM)[cite: 9] | SQLCipher / Core Data Encrypted Stores[cite: 9] | Ephemeral In-Memory Redis (No RDB/AOF)[cite: 9] |
| **Key Custody** | Android Keystore (StrongBox Keymaster HSM)[cite: 9] | Apple Secure Enclave (`kSecAccessControl`)[cite: 9] | HashiCorp Vault / Cloud KMS[cite: 9] |
| **Networking** | Retrofit + OkHttp 4 (TLS 1.3, Cert Pinning)[cite: 9] | URLSession (TLS 1.3, Cert Pinning)[cite: 9] | Envoy Proxy (No-Log, IP Masking, Token Bucket)[cite: 9] |
| **Observability** | On-device differentially private counter[cite: 9] | On-device differentially private counter[cite: 9] | Prometheus + Grafana (Ephemeral, Anonymized)[cite: 9] |

---

## 3. High-Level Architectural Topography[cite: 9]

```
+---------------------------------------------------------------------------------------------------+
|                                      CLIENT RUNTIME ENVIRONMENT                                    |
|                                                                                                   |
|   +--------------------------+  [Touch / Keystroke]  +----------------------------------------+   |
|   |   Haptic & Audio Engine  |<----------------------|        Core Input Engine (IME)         |   |
|   |  - Android: C++ Oboe     |                       |  - Android: InputMethodService         |   |
|   |  - iOS: AudioToolbox PCM |                       |  - iOS: UIInputViewController          |   |
|   +--------------------------+                       +-------------------+--------------------+   |
|                                                                          |                        |
|   +--------------------------+  [Audio Buffer Flow]                      | [Copy / Paste]         |
|   |  Offline STT Engine      |----------------------->                   v                        |
|   |  - Android: Sherpa-ONNX  |                       +----------------------------------------+   |
|   |  - iOS: Native Dictation |                       |       Zero-Knowledge Local Storage     |   |
|   +--------------------------+                       |  - Short-Term: RAM Circular Buffer     |   |
|                                                      |  - Long-Term: SQLCipher (AES-256-GCM)  |   |
|                                                      +-------------------+--------------------+   |
|                                                                          |                        |
|                                                      [Explicit Trigger]  v                        |
|                                                      +----------------------------------------+   |
|                                                      |       Client-Side DLP & Firewall       |   |
|                                                      |  - Strip CC, PII, Tokens, Passwords    |   |
|                                                      +-------------------+--------------------+   |
+--------------------------------------------------------------------------|------------------------+
                                                                           | TLS 1.3 / Cert Pinning
                                                                           v
+---------------------------------------------------------------------------------------------------+
|                                 STATELESS TRANSLATION BACKEND                                     |
|   +--------------------------+      +--------------------------+      +-----------------------+   |
|   | Envoy Proxy / API Gateway|----->| In-Memory Translation    |----->| Zero-Retention Pipe   |   |
|   | (Token Bucket, No Logs)  |      | (CTranslate2 / INT8)     |      | (Ephemeral RAM Only)  |   |
|   +--------------------------+      +--------------------------+      +-----------------------+   |
+---------------------------------------------------------------------------------------------------+
```[cite: 9]

---

## 4. Distinct Platform Workflows & Architecture Constraints[cite: 9]

### 4.1 Android Workflow (`InputMethodService`)[cite: 9]
* **Process Model:** Runs as a persistent system service via `InputMethodService` within a dynamic application heap (192MB – 512MB+)[cite: 9].
* **Audio Synthesis:** Employs a native C++ audio engine using **Google Oboe** with `SharingMode::Exclusive` and `PerformanceMode::LowLatency` to achieve touch-to-sound latency under **12ms**[cite: 9].
* **Offline Speech-to-Text:** Embeds **Sherpa-ONNX** (Streaming Zipformer INT8) directly in-process[cite: 9]. Captures 16kHz 16-bit Mono PCM audio via `AudioRecord` and streams transcription tokens directly to `InputConnection`[cite: 9].
* **Microphone Permission Handling:** Implements a transparent `PermissionRequestActivity` trampoline (`FLAG_ACTIVITY_NEW_TASK`) to request runtime `RECORD_AUDIO` permissions without crashing the Service[cite: 9].
* **Low Memory Killer (LMK) Resilience:** Implements `onTrimMemory(level)` to release model caches dynamically when memory thresholds trigger `TRIM_MEMORY_MODERATE`[cite: 9].

### 4.2 iOS Workflow (`UIInputViewController`)[cite: 9]
* **Process Model:** Runs within an app extension sandboxed by `UIInputViewController`, constrained by Apple's strict **~30MB to 40MB Jetsam memory limit**[cite: 9]. Exceeding this limit causes immediate termination (`0xDEAD10CC`)[cite: 9].
* **Offline Speech Strategy:** Bypasses extension memory limits by delegating dictation to Apple's native speech daemon (`hasDictationKey = true`)[cite: 9]. Does not load heavy neural network models into extension memory[cite: 9].
* **Audio Synthesis:** Pre-warms `AudioServicesPlaySystemSound` and uses `CoreHaptics` to avoid the overhead of heavy `AVAudioEngine` graphs[cite: 9].
* **Entitlements & Permissions:** Requires explicit disclosure for `RequestsOpenAccess = true` in `Info.plist` to allow translation requests[cite: 9]. Uses App Group containers for sandboxed data sharing with the host app[cite: 9].

---

## 5. Primary Feature Specifications[cite: 9]

### 5.1 Mechanical Keyboard Haptics & Audio[cite: 9]
* **5 Sound Profiles:** Cherry MX Blue (Clicky), Cherry MX Brown (Tactile), Cherry MX Red (Linear), Topre Electro-Capacitive, and IBM Model M (Buckling Spring)[cite: 9].
* **Audio Engine Execution:** C++ Oboe mixer running on a dedicated thread with `SCHED_FIFO` priority, anti-clipping safeguards, and sub-15ms response times during fast typing (120+ WPM)[cite: 9].
* **Differentiated Haptic Feedback:** Standard keys (8ms light tap), Spacebar (16ms resonant tap), Backspace/Delete (Dual-pulse confirmation vibration), Enter/Return (24ms heavy confirmation pulse)[cite: 9].
* **Context Suppression:** Automatically mutes audio when system volume is muted, in Do Not Disturb (DND) mode, or when blacklisted apps (e.g., Zoom, Teams) are active[cite: 9].

### 5.2 Offline Voice-to-Text Pipeline[cite: 9]
* **Capture Profile:** 16kHz Mono 16-bit PCM capture with energy-based Voice Activity Detection (VAD) and noise gating[cite: 9].
* **Continuous Dictation & Commands:** Real-time punctuation prediction based on lexical pauses, plus voice commands (e.g., *"delete last word"*, *"new line"*, *"space"*)[cite: 9].
* **Microphone Security:** Audio buffers reside only in volatile RAM and are zeroed immediately after decoding[cite: 9]. The microphone is permanently locked and inaccessible in sensitive/password fields[cite: 9].

### 5.3 Intelligent Dual-Tier Clipboard & Migration Rules[cite: 9]
* **Tier 1 (Short-Term):** In-memory circular buffer storing the last 50 items with 24-hour expiration[cite: 9]. Wiped on device restart with zero disk persistence[cite: 9].
* **Tier 2 (Long-Term Vault):** SQLCipher encrypted database (AES-256-GCM) with unlimited storage up to device capacity[cite: 9]. Key derived from hardware Keystore/Secure Enclave with biometric authentication required for access[cite: 9].
* **Migration Rules:**
  * *Promotion to Vault:* Explicit user swipe-to-pin, manual bookmark tag, or copy of code snippets detected with language syntax tags[cite: 9].
  * *Demotion/Eviction:* Automated TTL purge from RAM after 24 hours[cite: 9]. Long-term items purged only via explicit user deletion invoking a 3-pass NIST SP 800-88 compliant block overwrite[cite: 9].

### 5.4 Isolated Translation Gateway & Client-Side DLP[cite: 9]
* **Explicit Intent Trigger:** Translation runs only when the user manually highlights or inputs text and taps the dedicated "Translate" button[cite: 9]. Continuous automatic translation is strictly forbidden[cite: 9].
* **Client-Side DLP Sanitizer:** Text is scanned locally prior to transmission[cite: 9]. Requests are aborted and flagged with a UI warning if credit card numbers (Luhn check), JWTs, API tokens, SSH keys, or SSNs are detected[cite: 9].
* **Backend Inference Optimization (<450ms SLA):** Uses quantized INT8 models running under **CTranslate2** on CPU/Triton[cite: 9]. Batch size dynamically sets to 1 with pre-allocated model contexts, providing round-trip translation in ~180-280ms over standard mobile networks[cite: 9].

---

## 6. Comprehensive Error Handling & Failure Matrix[cite: 9]

| Failure Scenario[cite: 9] | Trigger Condition[cite: 9] | System Impact[cite: 9] | Automated Recovery / Fallback Path[cite: 9] |
| :--- | :--- | :--- | :--- |
| **Model Memory Spikes (Android)** | System triggers `TRIM_MEMORY_RUNNING_CRITICAL`[cite: 9] | Risk of LMK termination[cite: 9] | Immediately evict Sherpa-ONNX model session; release unpinned audio buffers; notify UI to show "Low Memory Mode"[cite: 9]. |
| **Jetsam Memory Warning (iOS)** | Extension memory exceeds 32MB[cite: 9] | Imminent crash (`0xDEAD10CC`)[cite: 9] | Instantly tear down unneeded view hierarchies; drop audio buffers; force fallback to system keyboard sound generator[cite: 9]. |
| **Translation Timeout / Offline** | Network drops or latency > 1500ms[cite: 9] | Translation hangs[cite: 9] | Abort outbound request; fallback to on-device dictionary/CTranslate2 cached pairs; display non-blocking offline ribbon[cite: 9]. |
| **Microphone Permission Denied** | User dismisses runtime dialog[cite: 9] | Voice typing fails[cite: 9] | Display non-blocking guide prompt; gracefully keep keyboard open; offer settings shortcut via Activity trampoline[cite: 9]. |
| **Keystore Hardware Desync** | StrongBox/Secure Enclave unavailable[cite: 9] | Vault locked[cite: 9] | Prompt for biometric re-auth or master passphrase; fallback to volatile ephemeral clipboard only; prevent DB corruption[cite: 9]. |
| **Audio Engine Buffer Underrun** | High CPU contention (burst typing)[cite: 9] | Audio clicks or pops[cite: 9] | Dynamically expand Oboe buffer size multiplier from 1x to 2x burst; gracefully degrade audio fidelity without UI blocking[cite: 9]. |

---

## 7. Testing Strategy & Pyramid[cite: 9]

```
                ▲
               / \     E2E Security & Penetration Tests (10%)
              /   \    - OWASP MASVS, Keylogger Egress Scans, Fuzzing
             /-----\
            /       \   Integration & Component Tests (30%)
           /         \  - Audio Latency Benchmarks, SQLCipher Migration, DLP
          /-----------\
         /             \ Unit Tests (60%)
        /_______________\ - Regex DLP, Haptic Drivers, Cryptographic Derivation
```[cite: 9]

### 7.1 Frameworks & Tooling[cite: 9]
* **Unit Testing:** JUnit 5, MockK, KotlinX Coroutines Test (Android); XCTest, Swift Testing (iOS)[cite: 9].
* **Integration Testing:** AndroidX Test, Robolectric, Compose UI Test Rule; XCUITest (iOS)[cite: 9].
* **Audio Latency Benchmarking:** Custom NDK loopback latency harness using Android Oboe automated test utilities[cite: 9].
* **Security & Static Analysis:** SonarQube, MobSF (Mobile Security Framework), GitGuardian, OWASP Dependency-Check[cite: 9].

### 7.2 Coverage Target SLAs[cite: 9]
* Minimum Unit Test Line Coverage: **85%**[cite: 9]
* Core Cryptographic & DLP Code Coverage: **100%**[cite: 9]
* Automated regression gates on all pull requests via GitHub Actions[cite: 9].

---

## 8. WCAG 2.1 AA Accessibility Roadmap[cite: 9]

* **Touch Target Size:** All key caps strictly conform to minimum dimensions of **48 x 48 dp** (including touch expansion insets) preventing mis-hits[cite: 9].
* **Screen Reader Semantics:**
  * Android: Custom `AccessibilityNodeInfoCompat` actions announcing key characters, shift status, and clipboard titles via TalkBack[cite: 9].
  * iOS: `accessibilityLabel`, `accessibilityHint`, and `accessibilityTraits = [.keyboardKey]` correctly declared for VoiceOver[cite: 9].
* **Visual Accessibility:**
  * Dynamic contrast ratio exceeding **4.5:1** for standard keys and **7:0:1** for highlighted action keys[cite: 9].
  * Native High-Contrast Mode toggle for visually impaired users[cite: 9].
* **Non-Auditory Tactile Feedback:** Every auditory click is mapped to a synchronized haptic pattern, allowing deaf or hard-of-hearing users to experience the tactile click response[cite: 9].

---

## 9. Privacy-Preserving Observability & Telemetry Framework[cite: 9]

To strictly honor zero-trust invariants while monitoring system health, KeyGuard avoids third-party trackers (e.g., Firebase, Google Analytics)[cite: 9]:
* **Local Differential Privacy (LDP):** Performance metrics (crash frequency, translation latency) are perturbed with localized Laplacian noise ($\epsilon = 0.5$) before aggregation[cite: 9].
* **Zero Keystroke / Text Telemetry:** Telemetry pipelines are physically decoupled from the text buffer; string contents are excluded from data models by design[cite: 9].
* **Self-Hosted Metrics Collector:** Anonymized client heartbeats are routed via an Envoy proxy to a self-hosted Prometheus/Grafana instance with 7-day automated metric expiration[cite: 9].
* **Opt-Out Control:** A top-level toggle in keyboard settings allows complete disabling of all anonymous telemetry[cite: 9].

---

## 10. Regulatory Compliance & Data Governance[cite: 9]

* **GDPR & CCPA Compliance:**
  * *Right of Access / Portability:* Users can export long-term clipboard entries as an encrypted JSON archive[cite: 9].
  * *Right to Erasure (Be Forgotten):* "Nuke Vault" action triggers a multi-pass NIST SP 800-88 cryptographic wipe of local SQLCipher pages[cite: 9].
* **HIPAA Compatibility:** Automated DLP blocking prevents medical record numbers, prescription details, and health data from entering the translation egress channel[cite: 9].
* **OWASP Mobile Application Security Verification Standard (MASVS):** Certified adherence to MASVS-STORAGE (Hardware Keystore), MASVS-CRYPTO (AES-256-GCM, TLS 1.3), and MASVS-NETWORK (Certificate Pinning)[cite: 9].

---

## 11. Backend Infrastructure & Kubernetes Deployment[cite: 9]

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keyguard-translation-engine
  namespace: keyguard-core
  labels:
    app: translation-engine
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: translation-engine
  template:
    metadata:
      labels:
        app: translation-engine
    spec:
      containers:
      - name: ctranslate2-inference
        image: ghcr.io/keyguard/translation-worker:v2.1.0
        resources:
          requests:
            memory: "2Gi"
            cpu: "2"
          limits:
            memory: "4Gi"
            cpu: "4"
        securityContext:
          readOnlyRootFilesystem: true
          runAsNonRoot: true
          runAsUser: 10001
          allowPrivilegeEscalation: false
        env:
        - name: MODEL_NAME
          value: "nllb-200-distilled-600M-int8"
        - name: ZERO_LOG_MODE
          value: "true"
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: translation-engine-hpa
  namespace: keyguard-core
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: keyguard-translation-engine
  minReplicas: 4
  maxReplicas: 32
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 65
```[cite: 9]

---

## 12. Prioritized Improvement & Implementation Roadmap[cite: 9]

| Priority[cite: 9] | Feature / Work Item[cite: 9] | Effort (Weeks)[cite: 9] | Impact[cite: 9] | Verification Gate[cite: 9] |
| :---: | :--- | :---: | :---: | :--- |
| **P0**[cite: 9] | C++ Oboe Audio Engine + Switch Profiles[cite: 9] | 2[cite: 9] | Critical[cite: 9] | Touch-to-audio latency < 15ms[cite: 9] |
| **P0**[cite: 9] | Secure Field Detection & Input Sanitization[cite: 9] | 1[cite: 9] | Critical[cite: 9] | 100% lockout during `isSecureTextEntry`[cite: 9] |
| **P0**[cite: 9] | Memory-Safe Error Handling & LMK Recovery[cite: 9] | 1.5[cite: 9] | High[cite: 9] | Zero crashes under stress memory conditions[cite: 9] |
| **P1**[cite: 9] | Dual-Tier Clipboard (RAM + SQLCipher StrongBox)[cite: 9] | 2[cite: 9] | High[cite: 9] | AES-256 verified via disk forensics[cite: 9] |
| **P1**[cite: 9] | WCAG 2.1 AA Accessibility & TalkBack Engine[cite: 9] | 1.5[cite: 9] | High[cite: 9] | TalkBack navigates all keys flawlessly[cite: 9] |
| **P1**[cite: 9] | Sherpa-ONNX Offline Speech-to-Text Pipeline[cite: 9] | 3[cite: 9] | High[cite: 9] | Airplane-mode dictation functional[cite: 9] |
| **P2**[cite: 9] | Isolated Cloud Translation + Client DLP Filter[cite: 9] | 2[cite: 9] | Medium[cite: 9] | Luhn / JWT test patterns blocked[cite: 9] |
| **P2**[cite: 9] | Self-Hosted Privacy Observability Collector[cite: 9] | 1.5[cite: 9] | Medium[cite: 9] | Differentially private metrics emission[cite: 9] |
| **P3**[cite: 9] | Multi-Region K8s Deployment & Autoscaling[cite: 9] | 1.5[cite: 9] | Medium[cite: 9] | Cluster sustains 5,000 req/sec load test[cite: 9] |

---

## 13. Open Questions for Stakeholders[cite: 9]

1. **Enterprise Deployment:** Should we package a zero-cloud MDM/EMM build variant with translation entirely stripped for military/banking clients?[cite: 9]
2. **Dialect Expansion:** For offline Sherpa-ONNX, what are the primary language packs prioritized for the initial release (e.g., English, Spanish, Hindi)?[cite: 9]
3. **Monetization Policy:** Will premium mechanical sound packs (e.g., vintage Alps, Topre silent) be unlocked locally via cryptographic offline licenses?[cite: 9]