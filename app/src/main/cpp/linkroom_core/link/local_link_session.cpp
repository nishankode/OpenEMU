#include "local_link_session.h"

#include <android/log.h>
#include <android/native_window.h>
#include <algorithm>
#include <cerrno>
#include <sstream>
#include <sys/stat.h>
#include <sys/types.h>

namespace linkroom {
namespace {
constexpr const char* kTag = "LocalLinkSession";

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

LocalLinkSession::LocalLinkSession() = default;

LocalLinkSession::~LocalLinkSession() {
    stop();
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSlot1WindowLocked();
}

std::string LocalLinkSession::start(
    const std::string& primaryRomPath,
    const std::string& secondaryRomPath,
    const std::string& baseTestDir
) {
    stop();
    std::lock_guard<std::mutex> lock(mutex_);
    baseTestDir_ = baseTestDir;
    slot1RenderedFrames_ = 0;
    slot2RenderedFrames_ = 0;
    const std::string slot1Root = baseTestDir_ + "/slot_1";
    const std::string slot2Root = baseTestDir_ + "/slot_2";

    __android_log_print(ANDROID_LOG_INFO, kTag, "start local link test");
    __android_log_print(ANDROID_LOG_INFO, kTag, "slot 1 ROM path: %s", primaryRomPath.c_str());
    __android_log_print(ANDROID_LOG_INFO, kTag, "slot 2 ROM path: %s", secondaryRomPath.c_str());
    __android_log_print(ANDROID_LOG_INFO, kTag, "slot 1 save root: %s", slot1Root.c_str());
    __android_log_print(ANDROID_LOG_INFO, kTag, "slot 2 save root: %s", slot2Root.c_str());

    if (!ensureDirectory(slot1Root) || !ensureDirectory(slot2Root)) {
        status_ = "local link failed: unable to prepare separate save roots";
        return status_;
    }

    if (!prepareLockstep()) {
        status_ = "local link failed: unable to initialize shared lockstep";
        return status_;
    }

    std::string error;
    if (!slot1_.load(1, primaryRomPath, slot1Root, &lockstep_, &node1_, &error)) {
        status_ = "local link failed: " + error;
        releaseLockstep();
        return status_;
    }
    if (!slot2_.load(2, secondaryRomPath, slot2Root, &lockstep_, &node2_, &error)) {
        status_ = "local link failed: " + error;
        slot1_.release();
        releaseLockstep();
        return status_;
    }

    running_ = true;
    status_ = "local link running: two ROM-backed cores loaded and lockstep attached";
    scheduler_.start(
        [this] { schedulerTick(); },
        [this] { return status(); }
    );
    __android_log_print(ANDROID_LOG_INFO, kTag, "scheduler start requested");
    return status_;
}

void LocalLinkSession::stop() {
    scheduler_.stop();
    std::lock_guard<std::mutex> lock(mutex_);
    if (running_ || slot1_.isLoaded() || slot2_.isLoaded()) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "stopping local link session");
    }
    running_ = false;
    slot2_.release();
    slot1_.release();
    releaseLockstep();
    status_ = "local link stopped";
}

std::string LocalLinkSession::status() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return statusLocked();
}

void LocalLinkSession::setInputMask(int slot, std::uint32_t inputMask) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (slot == 1) {
        slot1_.setInputMask(inputMask);
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "slot 1 input mask: 0x%03x", inputMask);
    } else if (slot == 2) {
        slot2_.setInputMask(inputMask);
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "slot 2 input mask: 0x%03x", inputMask);
    }
}

void LocalLinkSession::attachSlot1Surface(ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSlot1WindowLocked();
    slot1Window_ = window;
    if (slot1Window_ != nullptr) {
        slot1WindowWidth_ = ANativeWindow_getWidth(slot1Window_);
        slot1WindowHeight_ = ANativeWindow_getHeight(slot1Window_);
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "slot 1 surface attached: %d x %d",
            slot1WindowWidth_,
            slot1WindowHeight_
        );
    } else {
        __android_log_print(ANDROID_LOG_WARN, kTag, "slot 1 surface attach ignored: null window");
    }
}

void LocalLinkSession::resizeSlot1Surface(int width, int height) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (width <= 0 || height <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "slot 1 resize ignored: %d x %d", width, height);
        return;
    }
    slot1WindowWidth_ = width;
    slot1WindowHeight_ = height;
    __android_log_print(ANDROID_LOG_INFO, kTag, "slot 1 surface resized: %d x %d", width, height);
}

void LocalLinkSession::detachSlot1Surface() {
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSlot1WindowLocked();
    __android_log_print(ANDROID_LOG_INFO, kTag, "slot 1 surface detached");
}

void LocalLinkSession::setRenderSlot(int slot) {
    std::lock_guard<std::mutex> lock(mutex_);
    activeRenderSlot_ = slot == 2 ? 2 : 1;
    __android_log_print(ANDROID_LOG_INFO, kTag, "active local link render slot: %d", activeRenderSlot_);
}

bool LocalLinkSession::prepareLockstep() {
    mLockstepInit(&lockstep_.d);
    lockstep_.d.context = &lockstepContext_;
    lockstep_.d.lock = &LocalLinkSession::lockCallback;
    lockstep_.d.unlock = &LocalLinkSession::unlockCallback;
    lockstep_.d.signal = &LocalLinkSession::signalCallback;
    lockstep_.d.wait = &LocalLinkSession::waitCallback;
    lockstep_.d.addCycles = &LocalLinkSession::addCyclesCallback;
    lockstep_.d.useCycles = &LocalLinkSession::useCyclesCallback;
    lockstep_.d.unusedCycles = &LocalLinkSession::unusedCyclesCallback;
    lockstep_.d.unload = &LocalLinkSession::unloadCallback;
    lockstepContext_.cycles = {};
    GBASIOLockstepInit(&lockstep_);
    GBASIOLockstepNodeCreate(&node1_);
    GBASIOLockstepNodeCreate(&node2_);
    const bool attached1 = GBASIOLockstepAttachNode(&lockstep_, &node1_);
    const bool attached2 = GBASIOLockstepAttachNode(&lockstep_, &node2_);
    lockstepReady_ = attached1 && attached2 && lockstep_.d.attached == 2;
    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "lockstep init: attached1=%s attached2=%s attached=%d",
        attached1 ? "true" : "false",
        attached2 ? "true" : "false",
        lockstep_.d.attached
    );
    return lockstepReady_;
}

void LocalLinkSession::releaseLockstep() {
    if (!lockstepReady_) {
        return;
    }
    GBASIOLockstepDetachNode(&lockstep_, &node2_);
    GBASIOLockstepDetachNode(&lockstep_, &node1_);
    mLockstepDeinit(&lockstep_.d);
    lockstepReady_ = false;
}

void LocalLinkSession::schedulerTick() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!running_ || !slot1_.isLoaded() || !slot2_.isLoaded()) {
        return;
    }
    slot1_.runFrame();
    slot2_.runFrame();
    if (slot1Window_ != nullptr && slot1WindowWidth_ > 0 && slot1WindowHeight_ > 0) {
        LinkedEmulatorSlot& renderSlot = activeRenderSlot_ == 2 ? slot2_ : slot1_;
        if (renderSlot.renderFrameToWindow(slot1Window_, slot1WindowWidth_, slot1WindowHeight_)) {
            if (activeRenderSlot_ == 2) {
                ++slot2RenderedFrames_;
            } else {
                ++slot1RenderedFrames_;
            }
        }
    }
    if ((slot1_.framesRun() % 600) == 0) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "scheduler running: slot1Frames=%llu slot2Frames=%llu renderSlot=%d attached=%d transferPhase=%d sio1=%d sio2=%d",
            static_cast<unsigned long long>(slot1_.framesRun()),
            static_cast<unsigned long long>(slot2_.framesRun()),
            activeRenderSlot_,
            lockstep_.d.attached,
            static_cast<int>(lockstep_.d.transferActive),
            slot1_.sioMode(),
            slot2_.sioMode()
        );
    }
}

std::string LocalLinkSession::statusLocked() const {
    std::ostringstream out;
    out << status_
        << " scheduler=" << (scheduler_.isRunning() ? "running" : "stopped")
        << " ticks=" << scheduler_.ticks()
        << " slot1Frames=" << slot1_.framesRun()
        << " slot2Frames=" << slot2_.framesRun()
        << " slot1Rendered=" << slot1RenderedFrames_
        << " slot2Rendered=" << slot2RenderedFrames_
        << " renderSlot=" << activeRenderSlot_
        << " attached=" << lockstep_.d.attached
        << " transferPhase=" << static_cast<int>(lockstep_.d.transferActive)
        << " sioMode1=" << slot1_.sioMode()
        << " sioMode2=" << slot2_.sioMode();
    return out.str();
}

void LocalLinkSession::releaseSlot1WindowLocked() {
    if (slot1Window_ != nullptr) {
        ANativeWindow_release(slot1Window_);
        slot1Window_ = nullptr;
    }
    slot1WindowWidth_ = 0;
    slot1WindowHeight_ = 0;
}

void LocalLinkSession::lockCallback(mLockstep* lockstep) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (context) {
        context->mutex.lock();
    }
}

void LocalLinkSession::unlockCallback(mLockstep* lockstep) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (context) {
        context->mutex.unlock();
    }
}

bool LocalLinkSession::signalCallback(mLockstep*, unsigned) {
    return true;
}

bool LocalLinkSession::waitCallback(mLockstep*, unsigned) {
    return true;
}

void LocalLinkSession::addCyclesCallback(mLockstep* lockstep, int id, int32_t cycles) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (context && id >= 0 && id < static_cast<int>(context->cycles.size())) {
        context->cycles[static_cast<size_t>(id)] += cycles;
    }
}

int32_t LocalLinkSession::useCyclesCallback(mLockstep* lockstep, int id, int32_t cycles) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (!context || id < 0 || id >= static_cast<int>(context->cycles.size())) {
        return 0;
    }
    auto& available = context->cycles[static_cast<size_t>(id)];
    const int32_t used = std::min(available, cycles);
    available -= used;
    return used;
}

int32_t LocalLinkSession::unusedCyclesCallback(mLockstep* lockstep, int id) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (!context || id < 0 || id >= static_cast<int>(context->cycles.size())) {
        return 0;
    }
    return context->cycles[static_cast<size_t>(id)];
}

void LocalLinkSession::unloadCallback(mLockstep* lockstep, int id) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (context && id >= 0 && id < static_cast<int>(context->cycles.size())) {
        context->cycles[static_cast<size_t>(id)] = 0;
    }
}

} // namespace linkroom
