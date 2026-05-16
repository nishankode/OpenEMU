#pragma once

#include <cstdint>
#include <string>
#include <vector>

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
    void pause();
    void resume();
    void release();

private:
    mCore* core_ = nullptr;
    std::vector<std::uint32_t> videoBuffer_;
    bool paused_ = true;
    bool romLoaded_ = false;
};

} // namespace linkroom
