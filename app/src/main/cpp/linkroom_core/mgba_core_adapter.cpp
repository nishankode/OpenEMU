#include "mgba_core_adapter.h"

#include <mgba/core/core.h>
#include <mgba/core/input.h>
#include <mgba/core/interface.h>
#include <mgba/internal/gba/input.h>
#include <mgba-util/vfs.h>

#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <sys/stat.h>

#include "video_frame_buffer.h"

namespace linkroom {
namespace {
constexpr const char* kTag = "MgbaCoreAdapter";
constexpr int kBootProbeFrames = 5;
constexpr int kVideoStride = 256;

bool fileExists(const std::string& path) {
    struct stat info {};
    return !path.empty() && stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode);
}
}

MgbaCoreAdapter::~MgbaCoreAdapter() {
    release();
}

bool MgbaCoreAdapter::isCoreAvailable() const {
    static_assert(mPLATFORM_GBA == 0, "Unexpected mGBA platform enum layout.");
    static_assert(GBA_KEY_A == 0, "Unexpected mGBA input enum layout.");
    struct mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        return false;
    }
    core->deinit(core);
    return true;
}

std::string MgbaCoreAdapter::statusMessage() const {
    return linkedCoreStatus();
}

std::string MgbaCoreAdapter::linkedCoreStatus() const {
    return isCoreAvailable()
        ? "mGBA core linked: true (0.10.5, GBA core compiled; ROM boot probe enabled)"
        : "mGBA core linked: false";
}

RomLoadResult MgbaCoreAdapter::loadAndBootGba(const std::string& romPath) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "Loading ROM from private path: %s", romPath.c_str());
    release();

    if (!fileExists(romPath)) {
        return {
            RomLoadStatus::FileNotFound,
            "file not found: copied ROM is missing from app-private storage"
        };
    }

    core_ = mCoreCreate(mPLATFORM_GBA);
    if (core_ == nullptr) {
        return {
            RomLoadStatus::UnexpectedNativeError,
            "unexpected native error: unable to create mGBA GBA core"
        };
    }

    if (!core_->init(core_)) {
        release();
        return {
            RomLoadStatus::UnexpectedNativeError,
            "unexpected native error: unable to initialize mGBA core"
        };
    }

    // mGBA expects config and a video buffer before normal frame execution.
    // Phase 0.2B3 keeps this buffer internal and continues rendering the
    // existing placeholder SurfaceView; real frame presentation comes later.
    mCoreInitConfig(core_, "linkroom");
    videoBuffer_.assign(kVideoStride * VideoFrameBuffer::kGbaHeight, 0);
    core_->setVideoBuffer(core_, reinterpret_cast<color_t*>(videoBuffer_.data()), kVideoStride);

    VFile* rom = VFileOpen(romPath.c_str(), O_RDONLY);
    if (rom == nullptr) {
        const int error = errno;
        release();
        return {
            RomLoadStatus::FileNotFound,
            std::string("file not found: unable to open copied ROM (") + std::strerror(error) + ")"
        };
    }

    if (!core_->isROM(rom)) {
        rom->close(rom);
        release();
        return {
            RomLoadStatus::InvalidRom,
            "invalid ROM: mGBA did not recognize this file as a GBA ROM"
        };
    }
    rom->seek(rom, 0, SEEK_SET);

    if (!core_->loadROM(core_, rom)) {
        rom->close(rom);
        release();
        return {
            RomLoadStatus::MgbaLoadFailure,
            "mGBA load failure: the ROM was recognized but could not be loaded"
        };
    }
    romLoaded_ = true;

    core_->reset(core_);
    for (int frame = 0; frame < kBootProbeFrames; ++frame) {
        core_->runFrame(core_);
    }
    paused_ = false;

    __android_log_print(ANDROID_LOG_INFO, kTag, "ROM boot probe succeeded.");
    return {
        RomLoadStatus::Success,
        "success: mGBA loaded and ran boot probe frames"
    };
}

void MgbaCoreAdapter::pause() {
    paused_ = true;
}

void MgbaCoreAdapter::resume() {
    if (core_ != nullptr) {
        paused_ = false;
    }
}

void MgbaCoreAdapter::release() {
    if (core_ != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "Releasing mGBA core.");
        if (romLoaded_) {
            core_->unloadROM(core_);
        }
        core_->deinit(core_);
        core_ = nullptr;
    }
    videoBuffer_.clear();
    romLoaded_ = false;
    paused_ = true;
}

} // namespace linkroom
