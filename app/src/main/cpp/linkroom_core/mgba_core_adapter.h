#pragma once

#include <cstdint>
#include <string>
#include <vector>

struct ANativeWindow;
struct mCore;

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
    RomLoadResult loadAndBootGba(const std::string& romPath);
    bool runFrame();
    bool renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight);
    void setInputMask(std::uint32_t inputMask);
    void pause();
    void resume();
    void release();
    bool hasLoadedRom() const;
    bool isPaused() const;

private:
    mCore* core_ = nullptr;
    std::vector<std::uint32_t> videoBuffer_;
    std::uint32_t inputMask_ = 0;
    bool paused_ = true;
    bool romLoaded_ = false;
};

} // namespace linkroom
