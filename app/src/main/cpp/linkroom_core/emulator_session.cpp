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

bool EmulatorSession::loadRomPlaceholder(const std::string& romUri) {
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "load ROM skeleton only: %s",
        romUri.c_str()
    );

    // TODO: Phase 0.2B should copy/open the user-selected ROM and load it through mGBA.
    return false;
}

void EmulatorSession::pause() {
    if (released_) {
        return;
    }
    paused_ = true;
}

void EmulatorSession::resume() {
    if (released_) {
        return;
    }
    paused_ = false;
}

void EmulatorSession::release() {
    if (released_) {
        return;
    }

    paused_ = true;
    released_ = true;
    // TODO: Phase 0.2B should stop the emulator thread and flush battery save state.
}

bool EmulatorSession::isReleased() const {
    return released_;
}

std::string EmulatorSession::statusMessage() const {
    return coreAdapter_.statusMessage();
}

} // namespace linkroom
