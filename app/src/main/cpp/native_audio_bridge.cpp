#include <jni.h>
#include <android/log.h>
#include <memory>
#include <mutex>
#include <cmath>
#include "MechanicalSoundEngine.hpp"

#define LOG_TAG "NativeAudioBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::unique_ptr<keyguard::audio::MechanicalSoundEngine> gEngine;
std::mutex gEngineMutex;

/**
 * Generates a default synthetic mechanical switch click (12ms tactile transient impulse)
 * ensuring immediate sound availability prior to loading external assets.
 */
std::vector<int16_t> generateDefaultClick() {
    constexpr int sampleRate = 44100;
    constexpr float durationMs = 12.0f;
    const size_t sampleCount = static_cast<size_t>((durationMs / 1000.0f) * sampleRate);

    std::vector<int16_t> buffer(sampleCount);
    for (size_t i = 0; i < sampleCount; ++i) {
        float t = static_cast<float>(i) / sampleRate;
        // Damped high-frequency mechanical resonance (~2.4 kHz) with sharp initial transient
        float decay = std::exp(-t * 500.0f);
        float wave = std::sin(2.0f * M_PI * 2400.0f * t) * decay;
        // Scale to 16-bit PCM range with headroom
        buffer[i] = static_cast<int16_t>(wave * 24000.0f);
    }
    return buffer;
}

jboolean initEngineInternal() {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gEngine) {
        gEngine = std::make_unique<keyguard::audio::MechanicalSoundEngine>();
    }

    bool started = gEngine->init();
    if (started) {
        // Pre-load default crisp mechanical click impulse
        std::vector<int16_t> defaultClick = generateDefaultClick();
        gEngine->loadSample(defaultClick.data(), defaultClick.size());
        LOGI("Native Audio Engine initialized with default mechanical click sample.");
    } else {
        LOGE("Failed to initialize Native Audio Engine stream.");
    }

    return static_cast<jboolean>(started);
}

void triggerClickInternal() {
    // Fast-path: pointer dereference and lock-free atomic trigger
    if (gEngine) {
        gEngine->triggerClick();
    }
}

jboolean loadSampleInternal(JNIEnv* env, jshortArray sampleData) {
    if (env == nullptr || sampleData == nullptr) {
        LOGE("nativeLoadSample called with null parameters.");
        return JNI_FALSE;
    }

    jsize length = env->GetArrayLength(sampleData);
    if (length <= 0) {
        LOGE("nativeLoadSample called with empty array.");
        return JNI_FALSE;
    }

    jshort* elements = env->GetShortArrayElements(sampleData, nullptr);
    if (elements == nullptr) {
        LOGE("Failed to obtain short array elements from JNI.");
        return JNI_FALSE;
    }

    {
        std::lock_guard<std::mutex> lock(gEngineMutex);
        if (gEngine) {
            gEngine->loadSample(reinterpret_cast<const int16_t*>(elements), static_cast<size_t>(length));
        }
    }

    env->ReleaseShortArrayElements(sampleData, elements, JNI_ABORT);
    return JNI_TRUE;
}

void teardownInternal() {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gEngine) {
        gEngine->teardown();
        gEngine.reset();
        LOGI("Native Audio Engine torn down and released.");
    }
}

} // namespace

extern "C" {

// ============================================================================
// JNI bindings for NativeSoundBridge
// ============================================================================

JNIEXPORT jboolean JNICALL
Java_com_keyguard_ime_audio_NativeSoundBridge_nativeInit(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return initEngineInternal();
}

JNIEXPORT void JNICALL
Java_com_keyguard_ime_audio_NativeSoundBridge_nativeTriggerClick(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    triggerClickInternal();
}

JNIEXPORT jboolean JNICALL
Java_com_keyguard_ime_audio_NativeSoundBridge_nativeLoadSample(
    JNIEnv* env,
    jobject /*thiz*/,
    jshortArray sampleData) {
    return loadSampleInternal(env, sampleData);
}

JNIEXPORT void JNICALL
Java_com_keyguard_ime_audio_NativeSoundBridge_nativeTeardown(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    teardownInternal();
}

// ============================================================================
// JNI bindings for NativeAudioEngine
// ============================================================================

JNIEXPORT jboolean JNICALL
Java_com_keyguard_ime_audio_NativeAudioEngine_nativeInit(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    return initEngineInternal();
}

JNIEXPORT void JNICALL
Java_com_keyguard_ime_audio_NativeAudioEngine_nativeTriggerClick(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    triggerClickInternal();
}

JNIEXPORT jboolean JNICALL
Java_com_keyguard_ime_audio_NativeAudioEngine_nativeLoadSample(
    JNIEnv* env,
    jobject /*thiz*/,
    jshortArray sampleData) {
    return loadSampleInternal(env, sampleData);
}

JNIEXPORT void JNICALL
Java_com_keyguard_ime_audio_NativeAudioEngine_nativeTeardown(
    JNIEnv* /*env*/,
    jobject /*thiz*/) {
    teardownInternal();
}

} // extern "C"
