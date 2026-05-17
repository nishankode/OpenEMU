#include "linked_emulator_slot.h"

#include <mgba/core/core.h>
#include <mgba/core/input.h>
#include <mgba/core/interface.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/sio.h>
#include <mgba/internal/gba/sio/lockstep.h>
#include <mgba-util/vfs.h>

#include <android/log.h>
#include <android/native_window.h>
#include <algorithm>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <fcntl.h>
#include <sstream>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

namespace linkroom {
namespace {
constexpr const char* kTag = "LinkedEmulatorSlot";
constexpr int kVideoStride = 256;
constexpr int kGbaWidth = 240;
constexpr int kGbaHeight = 160;

bool isFile(const std::string& path) {
    struct stat info {};
    return !path.empty() && stat(path.c_str(), &info) == 0 && S_ISREG(info.st_mode);
}

bool isDirectory(const std::string& path) {
    struct stat info {};
    return !path.empty() && stat(path.c_str(), &info) == 0 && S_ISDIR(info.st_mode);
}

bool ensureDirectory(const std::string& path) {
    if (path.empty() || isDirectory(path)) {
        return !path.empty();
    }

    std::string current;
    size_t start = 0;
    if (path[0] == '/') {
        current = "/";
        start = 1;
    }

    while (start <= path.size()) {
        const size_t end = path.find('/', start);
        const std::string part = path.substr(start, end == std::string::npos ? std::string::npos : end - start);
        if (!part.empty()) {
            if (!current.empty() && current.back() != '/') {
                current += "/";
            }
            current += part;
            if (!isDirectory(current) && mkdir(current.c_str(), 0700) != 0 && errno != EEXIST) {
                return false;
            }
        }
        if (end == std::string::npos) {
            break;
        }
        start = end + 1;
    }
    return isDirectory(path);
}
}

LinkedEmulatorSlot::~LinkedEmulatorSlot() {
    release();
}

bool LinkedEmulatorSlot::load(
    int slotIndex,
    const std::string& romPath,
    const std::string& saveRoot,
    GBASIOLockstep* lockstep,
    GBASIOLockstepNode* node,
    std::string* error
) {
    release();
    romPath_ = romPath;
    saveRoot_ = saveRoot;
    batteryPath_ = saveRoot_ + "/battery/current.sav";
    framesRun_ = 0;
    inputMask_ = 0;

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "slot %d load requested: rom=%s saveRoot=%s",
        slotIndex,
        romPath_.c_str(),
        saveRoot_.c_str()
    );

    if (!isFile(romPath_)) {
        if (error) {
            *error = "slot " + std::to_string(slotIndex) + ": ROM file not found";
        }
        return false;
    }
    if (!ensureDirectory(saveRoot_ + "/battery")) {
        if (error) {
            *error = "slot " + std::to_string(slotIndex) + ": unable to prepare save root";
        }
        return false;
    }

    core_ = mCoreCreate(mPLATFORM_GBA);
    if (core_ == nullptr || !core_->init(core_)) {
        if (error) {
            *error = "slot " + std::to_string(slotIndex) + ": unable to initialize GBA core";
        }
        release();
        return false;
    }

    mCoreInitConfig(core_, slotIndex == 1 ? "linkroom-link-slot-1" : "linkroom-link-slot-2");
    videoBuffer_.assign(kVideoStride * kGbaHeight, 0);
    core_->setVideoBuffer(core_, reinterpret_cast<color_t*>(videoBuffer_.data()), kVideoStride);

    VFile* rom = VFileOpen(romPath_.c_str(), O_RDONLY);
    if (rom == nullptr) {
        if (error) {
            *error = "slot " + std::to_string(slotIndex) + ": unable to open ROM";
        }
        release();
        return false;
    }
    if (!core_->isROM(rom)) {
        rom->close(rom);
        if (error) {
            *error = "slot " + std::to_string(slotIndex) + ": mGBA did not recognize ROM";
        }
        release();
        return false;
    }
    rom->seek(rom, 0, SEEK_SET);
    if (!core_->loadROM(core_, rom)) {
        rom->close(rom);
        if (error) {
            *error = "slot " + std::to_string(slotIndex) + ": mGBA failed to load ROM";
        }
        release();
        return false;
    }

    if (!attachBatterySave(error)) {
        release();
        return false;
    }

    auto* gba = static_cast<GBA*>(core_->board);
    if (gba == nullptr || lockstep == nullptr || node == nullptr) {
        if (error) {
            *error = "slot " + std::to_string(slotIndex) + ": missing GBA board or lockstep node";
        }
        release();
        return false;
    }

    // mGBA maps SIO_NORMAL_8 and SIO_NORMAL_32 to the same "normal" driver slot.
    // Installing SIO_NORMAL_32 here intentionally covers both normal serial modes.
    GBASIOSetDriver(&gba->sio, &node->d, SIO_MULTI);
    GBASIOSetDriver(&gba->sio, &node->d, SIO_NORMAL_32);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "slot %d SIO drivers installed: MULTI and NORMAL_8/NORMAL_32",
        slotIndex
    );

    core_->reset(core_);
    loaded_ = true;
    return true;
}

bool LinkedEmulatorSlot::attachBatterySave(std::string* error) {
    if (batteryPath_.empty()) {
        if (error) {
            *error = "battery save path is empty";
        }
        return false;
    }
    const bool attached = mCoreLoadSaveFile(core_, batteryPath_.c_str(), false);
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "battery save attach: path=%s result=%s",
        batteryPath_.c_str(),
        attached ? "success" : "failure"
    );
    if (!attached && !isFile(batteryPath_)) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "battery save is missing; slot will start with a fresh in-game save and flush to this path on release"
        );
        return true;
    }
    return attached;
}

void LinkedEmulatorSlot::flushBatterySave() {
    if (core_ == nullptr || !loaded_ || batteryPath_.empty()) {
        return;
    }
    if (!ensureDirectory(saveRoot_ + "/battery")) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "link slot save flush failed: battery directory unavailable");
        return;
    }

    void* saveData = nullptr;
    const size_t saveSize = core_->savedataClone(core_, &saveData);
    if (saveSize == 0 || saveData == nullptr) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "link slot save flush skipped: no battery data yet path=%s",
            batteryPath_.c_str()
        );
        if (saveData != nullptr) {
            std::free(saveData);
        }
        return;
    }

    const std::string tempPath = batteryPath_ + ".tmp";
    bool wrote = false;
    {
        std::ofstream output(tempPath, std::ios::binary | std::ios::trunc);
        if (output) {
            output.write(static_cast<const char*>(saveData), static_cast<std::streamsize>(saveSize));
            wrote = output.good();
        }
    }
    std::free(saveData);

    if (!wrote || rename(tempPath.c_str(), batteryPath_.c_str()) != 0) {
        unlink(tempPath.c_str());
        __android_log_print(ANDROID_LOG_WARN, kTag, "link slot save flush failed: path=%s", batteryPath_.c_str());
        return;
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "link slot save flushed: path=%s size=%zu",
        batteryPath_.c_str(),
        saveSize
    );
}

void LinkedEmulatorSlot::reset() {
    if (core_ != nullptr && loaded_) {
        core_->reset(core_);
        framesRun_ = 0;
    }
}

void LinkedEmulatorSlot::runFrame() {
    if (core_ == nullptr || !loaded_) {
        return;
    }
    core_->setKeys(core_, inputMask_);
    core_->runFrame(core_);
    ++framesRun_;
}

bool LinkedEmulatorSlot::runFrameSlice() {
    if (core_ == nullptr || !loaded_) {
        return false;
    }
    auto* board = gba();
    if (board == nullptr) {
        return false;
    }
    const auto frameBefore = board->video.frameCounter;
    core_->setKeys(core_, inputMask_);
    core_->runLoop(core_);
    if (board->video.frameCounter != frameBefore) {
        ++framesRun_;
        return true;
    }
    return false;
}

bool LinkedEmulatorSlot::renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight) {
    if (window == nullptr || windowWidth <= 0 || windowHeight <= 0 || videoBuffer_.empty()) {
        return false;
    }

    if (ANativeWindow_setBuffersGeometry(window, windowWidth, windowHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "slot render failed: unable to set window geometry");
        return false;
    }

    ANativeWindow_Buffer buffer {};
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "slot render failed: unable to lock window");
        return false;
    }

    auto* destination = static_cast<std::uint32_t*>(buffer.bits);
    if (destination == nullptr || buffer.stride <= 0 || buffer.width <= 0 || buffer.height <= 0) {
        ANativeWindow_unlockAndPost(window);
        return false;
    }

    const int bufferWidth = buffer.width;
    const int bufferHeight = buffer.height;
    const int destinationStride = buffer.stride;
    const float sourceAspect = static_cast<float>(kGbaWidth) / static_cast<float>(kGbaHeight);
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
        const int sourceY = (y * kGbaHeight) / drawHeight;
        std::uint32_t* destinationRow = destination + (offsetY + y) * destinationStride + offsetX;
        const std::uint32_t* sourceRow = videoBuffer_.data() + sourceY * kVideoStride;
        for (int x = 0; x < drawWidth; ++x) {
            const int sourceX = (x * kGbaWidth) / drawWidth;
            destinationRow[x] = sourceRow[sourceX] | 0xFF000000u;
        }
    }

    ANativeWindow_unlockAndPost(window);
    return true;
}

void LinkedEmulatorSlot::setInputMask(std::uint32_t inputMask) {
    inputMask_ = inputMask;
}

void LinkedEmulatorSlot::release() {
    if (core_ != nullptr) {
        flushBatterySave();
        auto* gba = static_cast<GBA*>(core_->board);
        if (gba != nullptr) {
            GBASIOSetDriver(&gba->sio, nullptr, SIO_NORMAL_32);
            GBASIOSetDriver(&gba->sio, nullptr, SIO_MULTI);
        }
        if (loaded_) {
            core_->unloadROM(core_);
        }
        core_->deinit(core_);
        core_ = nullptr;
    }
    videoBuffer_.clear();
    loaded_ = false;
}

bool LinkedEmulatorSlot::isLoaded() const {
    return loaded_;
}

const std::string& LinkedEmulatorSlot::romPath() const {
    return romPath_;
}

const std::string& LinkedEmulatorSlot::saveRoot() const {
    return saveRoot_;
}

std::uint64_t LinkedEmulatorSlot::framesRun() const {
    return framesRun_;
}

int LinkedEmulatorSlot::sioMode() const {
    auto* board = gba();
    return board != nullptr ? static_cast<int>(board->sio.mode) : -1;
}

int LinkedEmulatorSlot::sioCnt() const {
    auto* board = gba();
    return board != nullptr ? static_cast<int>(board->sio.siocnt) : -1;
}

int LinkedEmulatorSlot::rCnt() const {
    auto* board = gba();
    return board != nullptr ? static_cast<int>(board->sio.rcnt) : -1;
}

bool LinkedEmulatorSlot::hasActiveSioDriver() const {
    auto* board = gba();
    return board != nullptr && board->sio.activeDriver != nullptr;
}

GBA* LinkedEmulatorSlot::gba() const {
    return core_ != nullptr ? static_cast<GBA*>(core_->board) : nullptr;
}

} // namespace linkroom
