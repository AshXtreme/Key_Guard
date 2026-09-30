#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <limits>
#include <memory>
#include <vector>

namespace keyguard::audio {

/**
 * MechanicalSoundEngine
 *
 * Real-time, low-latency C++ audio engine built directly on Google Oboe.
 * Implements a lock-free sample playback ring buffer designed to meet
 * KeyGuard's <15ms touch-to-audio latency SLA without Java GC pauses.
 */
class MechanicalSoundEngine : public oboe::AudioStreamDataCallback,
                              public oboe::AudioStreamErrorCallback {
public:
    static constexpr size_t kPlaybackIdle = std::numeric_limits<size_t>::max();
    static constexpr int32_t kDefaultSampleRate = 44100;
    static constexpr int32_t kDefaultChannelCount = 1; // Mono

    MechanicalSoundEngine();
    virtual ~MechanicalSoundEngine();

    // Lifecycle
    bool init();
    void teardown();
    bool isRunning() const noexcept;

    // Real-time lock-free playback trigger
    void triggerClick() noexcept;

    // Load 16-bit 44.1kHz mono PCM samples
    void loadSample(const int16_t* data, size_t sampleCount);

    // Oboe Callbacks
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* audioStream,
        void* audioData,
        int32_t numFrames) override;

    void onErrorAfterClose(
        oboe::AudioStream* audioStream,
        oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::vector<int16_t> mSampleBuffer;
    std::atomic<size_t> mReadHead{kPlaybackIdle};
    std::atomic<bool> mIsRunning{false};

    void sanitizeBuffers() noexcept;
};

} // namespace keyguard::audio
