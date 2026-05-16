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
    void setInputMask(std::uint32_t inputMask);
    std::string flushBatterySave();
    void pause();
    void resume();
    void release();
    bool hasLoadedRom() const;
    bool isPaused() const;
    std::string saveStatus() const;

private:
    mCore* core_ = nullptr;
    std::vector<std::uint32_t> videoBuffer_;
    std::string gameRootDirectory_;
    std::string batterySavePath_;
    std::string batteryDirectory_;
    std::string saveStatus_ = "save: no ROM loaded";
    std::uint32_t inputMask_ = 0;
    bool paused_ = true;
    bool romLoaded_ = false;
};

} // namespace linkroom
