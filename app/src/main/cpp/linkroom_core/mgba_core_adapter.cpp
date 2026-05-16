#include "mgba_core_adapter.h"

#include <mgba/core/core.h>
#include <mgba/core/input.h>
#include <mgba/core/interface.h>
#include <mgba/internal/gba/input.h>
#include <mgba-util/vfs.h>

#include <android/log.h>
#include <android/native_window.h>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <algorithm>
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
    if (romLoaded_ && !paused_) {
        return "running: mGBA video frames are rendering";
    }
    if (romLoaded_ && paused_) {
        return "paused: mGBA core is loaded";
    }
    return linkedCoreStatus();
}

std::string MgbaCoreAdapter::linkedCoreStatus() const {
    return isCoreAvailable()
        ? "mGBA core linked: true (0.10.5, GBA core compiled; video rendering enabled)"
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
    // Phase 0.2C1 renders this software buffer into the Android SurfaceView
    // on the native emulation thread. Audio/input/saves are intentionally off.
    mCoreInitConfig(core_, "linkroom");
    videoBuffer_.assign(kVideoStride * VideoFrameBuffer::kGbaHeight, 0);
    core_->setVideoBuffer(core_, reinterpret_cast<color_t*>(videoBuffer_.data()), kVideoStride);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "Configured mGBA video buffer: %d x %d, stride %d",
        VideoFrameBuffer::kGbaWidth,
        VideoFrameBuffer::kGbaHeight,
        kVideoStride
    );

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

    __android_log_print(ANDROID_LOG_INFO, kTag, "ROM load succeeded; video rendering can start.");
    return {
        RomLoadStatus::Success,
        "running: mGBA loaded and started video rendering"
    };
}

bool MgbaCoreAdapter::runFrame() {
    if (core_ == nullptr || !romLoaded_ || paused_) {
        return false;
    }

    core_->runFrame(core_);
    return true;
}

bool MgbaCoreAdapter::renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight) {
    if (window == nullptr || windowWidth <= 0 || windowHeight <= 0 || videoBuffer_.empty()) {
        return false;
    }

    if (ANativeWindow_setBuffersGeometry(window, windowWidth, windowHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Unable to set native window geometry for mGBA frame.");
        return false;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Unable to lock native window for mGBA frame.");
        return false;
    }

    auto* destination = static_cast<std::uint32_t*>(buffer.bits);
    if (destination == nullptr || buffer.stride <= 0 || buffer.width <= 0 || buffer.height <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Native window buffer is invalid for mGBA frame.");
        ANativeWindow_unlockAndPost(window);
        return false;
    }

    const int bufferWidth = buffer.width;
    const int bufferHeight = buffer.height;
    const int destinationStride = buffer.stride;
    const int sourceWidth = VideoFrameBuffer::kGbaWidth;
    const int sourceHeight = VideoFrameBuffer::kGbaHeight;
    const float sourceAspect = static_cast<float>(sourceWidth) / static_cast<float>(sourceHeight);
    const float bufferAspect = static_cast<float>(bufferWidth) / static_cast<float>(bufferHeight);

    int drawWidth = bufferWidth;
    int drawHeight = bufferHeight;
    if (bufferAspect > sourceAspect) {
        drawWidth = static_cast<int>(bufferHeight * sourceAspect);
    } else {
        drawHeight = static_cast<int>(bufferWidth / sourceAspect);
    }
    drawWidth = std::max(1, std::min(drawWidth, bufferWidth));
    drawHeight = std::max(1, std::min(drawHeight, bufferHeight));
    const int offsetX = (bufferWidth - drawWidth) / 2;
    const int offsetY = (bufferHeight - drawHeight) / 2;

    for (int y = 0; y < bufferHeight; ++y) {
        std::uint32_t* row = destination + y * destinationStride;
        std::fill(row, row + bufferWidth, 0xFF000000u);
    }

    for (int y = 0; y < drawHeight; ++y) {
        const int sourceY = (y * sourceHeight) / drawHeight;
        std::uint32_t* destinationRow = destination + (offsetY + y) * destinationStride + offsetX;
        const std::uint32_t* sourceRow = videoBuffer_.data() + sourceY * kVideoStride;
        for (int x = 0; x < drawWidth; ++x) {
            const int sourceX = (x * sourceWidth) / drawWidth;
            destinationRow[x] = sourceRow[sourceX] | 0xFF000000u;
        }
    }

    ANativeWindow_unlockAndPost(window);
    return true;
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

bool MgbaCoreAdapter::hasLoadedRom() const {
    return romLoaded_;
}

bool MgbaCoreAdapter::isPaused() const {
    return paused_;
}

} // namespace linkroom
