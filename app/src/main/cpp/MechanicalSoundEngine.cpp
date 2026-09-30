#include "MechanicalSoundEngine.hpp"
#include <android/log.h>
#include <algorithm>
#include <cstring>

#define LOG_TAG "MechanicalSoundEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace keyguard::audio {

MechanicalSoundEngine::MechanicalSoundEngine() {
    mSampleBuffer.reserve(kDefaultSampleRate / 4); // Pre-allocate ~250ms buffer
}

MechanicalSoundEngine::~MechanicalSoundEngine() {
    teardown();
}

bool MechanicalSoundEngine::init() {
    if (mIsRunning.load(std::memory_order_acquire)) {
        LOGI("Audio engine already initialized and running.");
        return true;
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::I16)
        ->setChannelCount(oboe::ChannelCount::Mono)
        ->setSampleRate(kDefaultSampleRate)
        ->setDataCallback(this)
        ->setErrorCallback(this);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGW("Failed to open stream in Exclusive mode (%s). Attempting Shared mode fallback.",
             oboe::convertToText(result));
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            LOGE("Failed to open audio stream in fallback mode: %s", oboe::convertToText(result));
            return false;
        }
    }

    // Set buffer size to twice the burst size for optimal low-latency underrun protection
    if (mStream->getFramesPerBurst() > 0) {
        mStream->setBufferSizeInFrames(mStream->getFramesPerBurst() * 2);
    }

    result = mStream->start();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe audio stream: %s", oboe::convertToText(result));
        mStream->close();
        mStream.reset();
        return false;
    }

    mIsRunning.store(true, std::memory_order_release);
    LOGI("MechanicalSoundEngine started successfully (SampleRate: %d, Burst: %d, Exclusive: %s)",
         mStream->getSampleRate(),
         mStream->getFramesPerBurst(),
         (mStream->getSharingMode() == oboe::SharingMode::Exclusive) ? "YES" : "NO");

    return true;
}

void MechanicalSoundEngine::teardown() {
    mIsRunning.store(false, std::memory_order_release);
    mReadHead.store(kPlaybackIdle, std::memory_order_release);

    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }

    sanitizeBuffers();
    LOGI("MechanicalSoundEngine torn down and audio buffers sanitized.");
}

bool MechanicalSoundEngine::isRunning() const noexcept {
    return mIsRunning.load(std::memory_order_acquire);
}

void MechanicalSoundEngine::triggerClick() noexcept {
    // Lock-free, wait-free trigger: resetting read head to start of contiguous buffer
    if (!mSampleBuffer.empty()) {
        mReadHead.store(0, std::memory_order_release);
    }
}

void MechanicalSoundEngine::loadSample(const int16_t* data, size_t sampleCount) {
    if (data == nullptr || sampleCount == 0) {
        return;
    }

    // Temporarily disarm read head while updating the buffer
    mReadHead.store(kPlaybackIdle, std::memory_order_release);

    mSampleBuffer.assign(data, data + sampleCount);
    LOGI("Loaded new mechanical switch audio sample (%zu frames, %zu bytes)",
         sampleCount, sampleCount * sizeof(int16_t));
}

oboe::DataCallbackResult MechanicalSoundEngine::onAudioReady(
    oboe::AudioStream* /*audioStream*/,
    void* audioData,
    int32_t numFrames) {

    auto* outputBuffer = static_cast<int16_t*>(audioData);
    size_t head = mReadHead.load(std::memory_order_acquire);
    const size_t totalSamples = mSampleBuffer.size();

    // Fast-path: playback idle or completed, fill with silence
    if (head >= totalSamples) {
        std::memset(outputBuffer, 0, numFrames * sizeof(int16_t));
        return oboe::DataCallbackResult::Continue;
    }

    const size_t remainingSamples = totalSamples - head;
    const size_t framesToCopy = std::min(static_cast<size_t>(numFrames), remainingSamples);

    // Contiguous lock-free memory copy
    std::memcpy(outputBuffer, mSampleBuffer.data() + head, framesToCopy * sizeof(int16_t));

    // Zero out any remaining frames in this callback slice
    if (framesToCopy < static_cast<size_t>(numFrames)) {
        std::memset(outputBuffer + framesToCopy, 0, (numFrames - framesToCopy) * sizeof(int16_t));
    }

    // Advance atomic read head
    mReadHead.store(head + framesToCopy, std::memory_order_release);

    return oboe::DataCallbackResult::Continue;
}

void MechanicalSoundEngine::onErrorAfterClose(
    oboe::AudioStream* /*audioStream*/,
    oboe::Result error) {
    if (error == oboe::Result::ErrorDisconnected) {
        LOGI("Audio route disconnected (e.g. headset plugged/unplugged). Re-initializing stream...");
        mIsRunning.store(false, std::memory_order_release);
        init();
    } else {
        LOGE("Unrecoverable Oboe audio stream error occurred: %s", oboe::convertToText(error));
    }
}

void MechanicalSoundEngine::sanitizeBuffers() noexcept {
    if (!mSampleBuffer.empty()) {
        // Explicit volatile wipe to prevent compiler optimization of sensitive audio data
        volatile int16_t* ptr = mSampleBuffer.data();
        for (size_t i = 0; i < mSampleBuffer.size(); ++i) {
            ptr[i] = 0;
        }
        mSampleBuffer.clear();
        mSampleBuffer.shrink_to_fit();
    }
}

} // namespace keyguard::audio
