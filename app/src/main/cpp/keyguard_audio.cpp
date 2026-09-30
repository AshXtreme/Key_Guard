#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <concepts>
#include <memory>

#define TAG "KeyGuardAudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Compile-time verification of C++20 concepts and standard library support
template <typename T>
concept AudioStreamBuilderConcept = requires(T builder) {
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
};

static_assert(AudioStreamBuilderConcept<oboe::AudioStreamBuilder>, "Oboe AudioStreamBuilder must satisfy concept");

extern "C" JNIEXPORT jstring JNICALL
Java_com_keyguard_ime_audio_NativeAudioEngine_getOboeVersion(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("KeyGuard Native Audio Engine compiled with C++20. Oboe version: %s", oboe::getVersionText());
    return env->NewStringUTF(oboe::getVersionText());
}
