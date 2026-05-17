#include "linked_emulator_slot.h"

#include <mgba/core/core.h>
#include <mgba/core/input.h>
#include <mgba/core/interface.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/sio.h>
#include <mgba/internal/gba/sio/lockstep.h>
#include <mgba-util/vfs.h>

#include <android/log.h>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <sstream>
#include <sys/stat.h>
#include <sys/types.h>

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

    GBASIOSetDriver(&gba->sio, &node->d, SIO_MULTI);
    GBASIOSetDriver(&gba->sio, &node->d, SIO_NORMAL_32);
    __android_log_print(ANDROID_LOG_INFO, kTag, "slot %d SIO drivers installed", slotIndex);

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
    if (!attached && error) {
        *error = "unable to attach battery save";
    }
    return attached;
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

void LinkedEmulatorSlot::setInputMask(std::uint32_t inputMask) {
    inputMask_ = inputMask;
}

void LinkedEmulatorSlot::release() {
    if (core_ != nullptr) {
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

GBA* LinkedEmulatorSlot::gba() const {
    return core_ != nullptr ? static_cast<GBA*>(core_->board) : nullptr;
}

} // namespace linkroom
