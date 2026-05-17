#pragma once

#include <cstdint>
#include <string>
#include <vector>

struct ANativeWindow;
struct mCore;

#include "save_paths.h"

namespace linkroom {

enum class RomLoadStatus {
    Success,
    InvalidRom,
    FileNotFound,
    MgbaLoadFailure,
    UnexpectedNativeError
};

struct RomLoadResult {
    RomLoadStatus status = RomLoadStatus::UnexpectedNativeError;
    std::string message;

    bool isSuccess() const {
        return status == RomLoadStatus::Success;
    }
};

class MgbaCoreAdapter {
public:
    MgbaCoreAdapter() = default;
    ~MgbaCoreAdapter();

    MgbaCoreAdapter(const MgbaCoreAdapter&) = delete;
    MgbaCoreAdapter& operator=(const MgbaCoreAdapter&) = delete;

    bool isCoreAvailable() const;
    std::string statusMessage() const;
    std::string linkedCoreStatus() const;
    RomLoadResult loadAndBootGba(const std::string& romPath, const SavePaths& savePaths);
    bool runFrame();
    bool renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight);
    int readAudio(std::int16_t* output, int maxSamples);
    void setInputMask(std::uint32_t inputMask);
    void setFastForward(bool enabled);
    std::string saveStateToFile(int slot, const std::string& statePath);
    std::string loadStateFromFile(int slot, const std::string& statePath);
    std::string flushBatterySave();
    void pause();
    void resume();
    void release();
    bool hasLoadedRom() const;
    bool isPaused() const;
    std::string saveStatus() const;
    std::string audioStatus() const;
    bool isFastForwardEnabled() const;
    std::string fastForwardStatus() const;

private:
    void configureAudio();
    void drainAudio();
    void pushAudioSamples(const std::int16_t* samples, size_t sampleCount);
    void resetAudioBuffer();

    mCore* core_ = nullptr;
    std::vector<std::uint32_t> videoBuffer_;
    std::vector<std::int16_t> audioRingBuffer_;
    std::string gameRootDirectory_;
    std::string batterySavePath_;
    std::string batteryDirectory_;
    std::string saveStatus_ = "save: no ROM loaded";
    std::string audioStatus_ = "audio: no ROM loaded";
    size_t audioReadIndex_ = 0;
    size_t audioWriteIndex_ = 0;
    size_t audioBufferedSamples_ = 0;
    size_t audioOverruns_ = 0;
    size_t audioUnderruns_ = 0;
    std::uint32_t inputMask_ = 0;
    bool audioConfigured_ = false;
    bool fastForwardEnabled_ = false;
    bool paused_ = true;
    bool romLoaded_ = false;
};

} // namespace linkroom
