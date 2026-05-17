#include "emulator_session.h"

#include <android/log.h>

namespace linkroom {
namespace {
constexpr const char* kTag = "LinkRoomSession";
}

EmulatorSession::EmulatorSession() = default;

EmulatorSession::~EmulatorSession() {
    release();
}

RomLoadResult EmulatorSession::loadRom(const std::string& romPath, const std::string& gameRootPath) {
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load ROM request: %s",
        romPath.c_str()
    );

    released_ = false;
    paused_ = false;
    const SavePaths savePaths(gameRootPath);
    const RomLoadResult result = coreAdapter_.loadAndBootGba(romPath, savePaths);
    if (!result.isSuccess()) {
        paused_ = true;
    }
    return result;
}

bool EmulatorSession::runFrame() {
    if (released_ || paused_) {
        return false;
    }
    return coreAdapter_.runFrame();
}

bool EmulatorSession::renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight) {
    if (released_ || paused_) {
        return false;
    }
    return coreAdapter_.renderFrameToWindow(window, windowWidth, windowHeight);
}

int EmulatorSession::readAudio(std::int16_t* output, int maxSamples) {
    if (released_ || output == nullptr || maxSamples <= 0) {
        return 0;
    }
    return coreAdapter_.readAudio(output, maxSamples);
}

void EmulatorSession::setInputMask(std::uint32_t inputMask) {
    if (released_) {
        return;
    }
    coreAdapter_.setInputMask(inputMask);
}

std::string EmulatorSession::flushBatterySave() {
    if (released_) {
        return coreAdapter_.saveStatus();
    }
    return coreAdapter_.flushBatterySave();
}

void EmulatorSession::pause() {
    if (released_) {
        return;
    }
    paused_ = true;
    coreAdapter_.flushBatterySave();
    coreAdapter_.pause();
}

void EmulatorSession::resume() {
    if (released_) {
        return;
    }
    paused_ = false;
    coreAdapter_.resume();
}

void EmulatorSession::release() {
    if (released_) {
        return;
    }

    paused_ = true;
    released_ = true;
    coreAdapter_.release();
}

bool EmulatorSession::isReleased() const {
    return released_;
}

bool EmulatorSession::hasLoadedRom() const {
    return !released_ && coreAdapter_.hasLoadedRom();
}

bool EmulatorSession::isPaused() const {
    return paused_ || coreAdapter_.isPaused();
}

std::string EmulatorSession::statusMessage() const {
    if (released_) {
        return "released: emulator runtime resources released";
    }
    return coreAdapter_.statusMessage();
}

std::string EmulatorSession::saveStatus() const {
    return coreAdapter_.saveStatus();
}

std::string EmulatorSession::audioStatus() const {
    return coreAdapter_.audioStatus();
}

} // namespace linkroom
