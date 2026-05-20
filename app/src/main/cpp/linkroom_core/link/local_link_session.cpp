#include "local_link_session.h"

#include <android/log.h>
#include <android/native_window.h>
#include <algorithm>
#include <cerrno>
#include <chrono>
#include <sstream>
#include <sys/stat.h>
#include <sys/types.h>

namespace linkroom {
namespace {
constexpr const char* kTag = "LocalLinkSession";
constexpr int kMaxSlicesPerSchedulerTick = 512;
constexpr int kBalancedSliceBudget = 96;
constexpr std::int64_t kNoTransferWarningMs = 5000;

std::int64_t steadyNowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
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

LocalLinkSession::LocalLinkSession() = default;

LocalLinkSession::~LocalLinkSession() {
    stop();
    std::lock_guard<std::mutex> lock(mutex_);
    releaseSlot1WindowLocked();
}

std::string LocalLinkSession::start(
    const std::string& primaryRomPath,
    const std::string& secondaryRomPath,
    const std::string& baseTestDir,
    LocalLinkSchedulerMode schedulerMode
) {
    stop();
    std::lock_guard<std::mutex> lock(mutex_);
    baseTestDir_ = baseTestDir;
    schedulerMode_ = schedulerMode;
    resetDiagnosticsLocked();
    const std::string slot1Root = baseTestDir_ + "/slot_1";
    const std::string slot2Root = baseTestDir_ + "/slot_2";

    __android_log_print(
        ANDROID_LOG_INFO,
        kTag,
        "start local link test schedulerMode=%s",
        schedulerMode_ == LocalLinkSchedulerMode::BalancedLockstep ? "balanced_lockstep" : "stable"
    );
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
    clearInputMasks();
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
    if (slot == 1) {
        slot1InputMask_.store(inputMask, std::memory_order_relaxed);
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "slot 1 input mask: 0x%03x", inputMask);
    } else if (slot == 2) {
        slot2InputMask_.store(inputMask, std::memory_order_relaxed);
        __android_log_print(ANDROID_LOG_DEBUG, kTag, "slot 2 input mask: 0x%03x", inputMask);
    }
}

void LocalLinkSession::clearInputMasks() {
    slot1InputMask_.store(0, std::memory_order_relaxed);
    slot2InputMask_.store(0, std::memory_order_relaxed);
    __android_log_print(ANDROID_LOG_INFO, kTag, "local link input cleared for both slots");
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
    slot1_.setInputMask(slot1InputMask_.load(std::memory_order_relaxed));
    slot2_.setInputMask(slot2InputMask_.load(std::memory_order_relaxed));
    int slicesUsed = 0;
    if (schedulerMode_ == LocalLinkSchedulerMode::BalancedLockstep) {
        slicesUsed = runBalancedSchedulerTickLocked();
    } else {
        runStableSchedulerTickLocked();
        prioritizedSlot_ = 0;
    }
    lastSlicesUsed_ = slicesUsed;
    const int transferPhase = static_cast<int>(lockstep_.d.transferActive);

    if (previousTransferPhase_ == 0 && transferPhase != 0) {
        ++transferAttemptCount_;
        lastTransferActivityMs_ = steadyNowMs();
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "local link transfer activity started: phase=%d attempts=%llu",
            transferPhase,
            static_cast<unsigned long long>(transferAttemptCount_)
        );
    } else if (previousTransferPhase_ != 0 && transferPhase == 0) {
        ++transferCompleteCount_;
        lastTransferActivityMs_ = steadyNowMs();
        __android_log_print(
            ANDROID_LOG_INFO,
            kTag,
            "local link transfer activity completed: completions=%llu",
            static_cast<unsigned long long>(transferCompleteCount_)
        );
    }
    previousTransferPhase_ = transferPhase;

    const int sioMode1 = slot1_.sioMode();
    const int sioMode2 = slot2_.sioMode();
    if (sioMode1 != previousSioMode1_) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "slot 1 SIO mode changed: %d -> %d", previousSioMode1_, sioMode1);
        previousSioMode1_ = sioMode1;
        lastModeChangeMs_ = steadyNowMs();
    }
    if (sioMode2 != previousSioMode2_) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "slot 2 SIO mode changed: %d -> %d", previousSioMode2_, sioMode2);
        previousSioMode2_ = sioMode2;
        lastModeChangeMs_ = steadyNowMs();
    }
    updateDiagnosticsLocked(transferPhase, sioMode1, sioMode2, slicesUsed);

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
            "scheduler running: mode=%s slot1Frames=%llu slot2Frames=%llu renderSlot=%d attached=%d transferPhase=%d attempts=%llu completions=%llu sio1=%d sio2=%d signals=%llu waits=%llu",
            schedulerMode_ == LocalLinkSchedulerMode::BalancedLockstep ? "balanced_lockstep" : "stable",
            static_cast<unsigned long long>(slot1_.framesRun()),
            static_cast<unsigned long long>(slot2_.framesRun()),
            activeRenderSlot_,
            lockstep_.d.attached,
            transferPhase,
            static_cast<unsigned long long>(transferAttemptCount_),
            static_cast<unsigned long long>(transferCompleteCount_),
            sioMode1,
            sioMode2,
            static_cast<unsigned long long>(lockstepContext_.signalCount.load(std::memory_order_relaxed)),
            static_cast<unsigned long long>(lockstepContext_.waitCount.load(std::memory_order_relaxed))
        );
    }
}

void LocalLinkSession::runStableSchedulerTickLocked() {
    slot1_.runFrame();
    slot2_.runFrame();
}

int LocalLinkSession::runBalancedSchedulerTickLocked() {
    const std::uint64_t startFrames1 = slot1_.framesRun();
    const std::uint64_t startFrames2 = slot2_.framesRun();
    const auto frameDelta = static_cast<int64_t>(startFrames1) - static_cast<int64_t>(startFrames2);
    prioritizedSlot_ = 0;

    if (frameDelta > 0) {
        prioritizedSlot_ = 2;
        slot2_.runFrame();
        return 0;
    }
    if (frameDelta < 0) {
        prioritizedSlot_ = 1;
        slot1_.runFrame();
        return 0;
    }

    int slicesUsed = 0;
    while ((slot1_.framesRun() == startFrames1 || slot2_.framesRun() == startFrames2) &&
           slicesUsed < kBalancedSliceBudget) {
        if (slot1_.framesRun() == startFrames1) {
            slot1_.runFrameSlice();
            ++slicesUsed;
        }
        if (slot2_.framesRun() == startFrames2) {
            slot2_.runFrameSlice();
            ++slicesUsed;
        }
    }

    const bool slot1Advanced = slot1_.framesRun() != startFrames1;
    const bool slot2Advanced = slot2_.framesRun() != startFrames2;
    if (!slot1Advanced && !slot2Advanced) {
        prioritizedSlot_ = 0;
        ++balancedFallbackCount_;
        slot1_.runFrame();
        slot2_.runFrame();
        __android_log_print(
            ANDROID_LOG_DEBUG,
            kTag,
            "balanced scheduler fallback: neither slot reached a frame within slice budget=%d fallbacks=%llu",
            kBalancedSliceBudget,
            static_cast<unsigned long long>(balancedFallbackCount_)
        );
    } else if (!slot1Advanced) {
        prioritizedSlot_ = 1;
        ++balancedFallbackCount_;
        slot1_.runFrame();
    } else if (!slot2Advanced) {
        prioritizedSlot_ = 2;
        ++balancedFallbackCount_;
        slot2_.runFrame();
    }

    if (slicesUsed >= kBalancedSliceBudget) {
        __android_log_print(
            ANDROID_LOG_DEBUG,
            kTag,
            "balanced scheduler slice budget reached: slices=%d slot1Frames=%llu slot2Frames=%llu priority=%d fallbacks=%llu",
            slicesUsed,
            static_cast<unsigned long long>(slot1_.framesRun()),
            static_cast<unsigned long long>(slot2_.framesRun()),
            prioritizedSlot_,
            static_cast<unsigned long long>(balancedFallbackCount_)
        );
    }
    return slicesUsed;
}

void LocalLinkSession::resetDiagnosticsLocked() {
    slot1RenderedFrames_ = 0;
    slot2RenderedFrames_ = 0;
    transferAttemptCount_ = 0;
    transferCompleteCount_ = 0;
    sliceLimitHitCount_ = 0;
    balancedFallbackCount_ = 0;
    previousTransferPhase_ = 0;
    previousSioMode1_ = -1;
    previousSioMode2_ = -1;
    lastSignalSample_ = 0;
    lastWaitSample_ = 0;
    lastTickSample_ = 0;
    signalRatePerSecond_ = 0;
    waitRatePerSecond_ = 0;
    schedulerTickRatePerSecond_ = 0;
    lastSlicesUsed_ = 0;
    prioritizedSlot_ = 0;
    const std::int64_t now = steadyNowMs();
    startMonotonicMs_ = now;
    lastTransferActivityMs_ = now;
    lastModeChangeMs_ = now;
    lastMetricsSampleMs_ = now;
    lockstepContext_.signalCount.store(0, std::memory_order_relaxed);
    lockstepContext_.waitCount.store(0, std::memory_order_relaxed);
}

void LocalLinkSession::updateDiagnosticsLocked(int transferPhase, int sioMode1, int sioMode2, int slicesUsed) {
    const std::int64_t now = steadyNowMs();
    const std::int64_t elapsed = now - lastMetricsSampleMs_;
    if (elapsed < 1000) {
        return;
    }

    const std::uint64_t signals = lockstepContext_.signalCount.load(std::memory_order_relaxed);
    const std::uint64_t waits = lockstepContext_.waitCount.load(std::memory_order_relaxed);
    const std::uint64_t ticks = scheduler_.ticks();
    signalRatePerSecond_ = ((signals - lastSignalSample_) * 1000) / static_cast<std::uint64_t>(elapsed);
    waitRatePerSecond_ = ((waits - lastWaitSample_) * 1000) / static_cast<std::uint64_t>(elapsed);
    schedulerTickRatePerSecond_ = ((ticks - lastTickSample_) * 1000) / static_cast<std::uint64_t>(elapsed);
    lastSignalSample_ = signals;
    lastWaitSample_ = waits;
    lastTickSample_ = ticks;
    lastMetricsSampleMs_ = now;

    const std::string warning = linkWarningLocked(now);
    if (warning != "none") {
        __android_log_print(
            ANDROID_LOG_WARN,
            kTag,
            "local link diagnostic warning: %s transferPhase=%d sio1=%d sio2=%d slices=%d signalRate=%llu waitRate=%llu tickRate=%llu",
            warning.c_str(),
            transferPhase,
            sioMode1,
            sioMode2,
            slicesUsed,
            static_cast<unsigned long long>(signalRatePerSecond_),
            static_cast<unsigned long long>(waitRatePerSecond_),
            static_cast<unsigned long long>(schedulerTickRatePerSecond_)
        );
    }
}

std::string LocalLinkSession::linkWarningLocked(std::int64_t nowMs) const {
    if (!running_) {
        return "none";
    }
    const int frameDelta = static_cast<int>(slot1_.framesRun() > slot2_.framesRun()
        ? slot1_.framesRun() - slot2_.framesRun()
        : slot2_.framesRun() - slot1_.framesRun());
    if (frameDelta > 2) {
        return "core_frame_delta";
    }
    if (schedulerTickRatePerSecond_ < 30 && scheduler_.isRunning()) {
        return "scheduler_starvation";
    }
    if (schedulerMode_ == LocalLinkSchedulerMode::BalancedLockstep && balancedFallbackCount_ > scheduler_.ticks() / 2 && scheduler_.ticks() > 120) {
        return "balanced_fallback_high";
    }
    if (sliceLimitHitCount_ > 0 && lastSlicesUsed_ >= kMaxSlicesPerSchedulerTick) {
        return "slice_cap_hit";
    }
    if (lockstep_.d.transferActive == 0 &&
        slot1_.hasActiveSioDriver() &&
        slot2_.hasActiveSioDriver() &&
        (nowMs - lastTransferActivityMs_) > kNoTransferWarningMs &&
        transferAttemptCount_ > 0) {
        return "sio_idle_no_recent_transfers";
    }
    if (lockstepContext_.waitCount.load(std::memory_order_relaxed) > 0 &&
        waitRatePerSecond_ > 0 &&
        signalRatePerSecond_ == 0) {
        return "lockstep_wait_without_signal";
    }
    return "none";
}

std::string LocalLinkSession::statusLocked() const {
    const std::int64_t now = steadyNowMs();
    const int frameDelta = static_cast<int>(slot1_.framesRun() > slot2_.framesRun()
        ? slot1_.framesRun() - slot2_.framesRun()
        : slot2_.framesRun() - slot1_.framesRun());
    std::ostringstream out;
    out << status_
        << " schedulerMode=" << (schedulerMode_ == LocalLinkSchedulerMode::BalancedLockstep ? "balanced_lockstep" : "stable")
        << " scheduler=" << (scheduler_.isRunning() ? "running" : "stopped")
        << " ticks=" << scheduler_.ticks()
        << " tickRate=" << schedulerTickRatePerSecond_
        << " slot1Frames=" << slot1_.framesRun()
        << " slot2Frames=" << slot2_.framesRun()
        << " frameDelta=" << frameDelta
        << " slot1Rendered=" << slot1RenderedFrames_
        << " slot2Rendered=" << slot2RenderedFrames_
        << " renderSlot=" << activeRenderSlot_
        << " attached=" << lockstep_.d.attached
        << " transferPhase=" << static_cast<int>(lockstep_.d.transferActive)
        << " transferAttempts=" << transferAttemptCount_
        << " transferCompletions=" << transferCompleteCount_
        << " lastTransferMsAgo=" << std::max<std::int64_t>(0, now - lastTransferActivityMs_)
        << " lockstepSignals=" << lockstepContext_.signalCount.load(std::memory_order_relaxed)
        << " lockstepWaits=" << lockstepContext_.waitCount.load(std::memory_order_relaxed)
        << " signalRate=" << signalRatePerSecond_
        << " waitRate=" << waitRatePerSecond_
        << " sioMode1=" << slot1_.sioMode()
        << " sioMode2=" << slot2_.sioMode()
        << " siocnt1=" << slot1_.sioCnt()
        << " siocnt2=" << slot2_.sioCnt()
        << " rcnt1=" << slot1_.rCnt()
        << " rcnt2=" << slot2_.rCnt()
        << " activeDriver1=" << (slot1_.hasActiveSioDriver() ? "true" : "false")
        << " activeDriver2=" << (slot2_.hasActiveSioDriver() ? "true" : "false")
        << " lastModeMsAgo=" << std::max<std::int64_t>(0, now - lastModeChangeMs_)
        << " slicesLastTick=" << lastSlicesUsed_
        << " sliceLimitHits=" << sliceLimitHitCount_
        << " balancedFallbacks=" << balancedFallbackCount_
        << " prioritizedSlot=" << prioritizedSlot_
        << " linkWarning=" << linkWarningLocked(now);
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

bool LocalLinkSession::signalCallback(mLockstep* lockstep, unsigned) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (context) {
        context->signalCount.fetch_add(1, std::memory_order_relaxed);
    }
    return true;
}

bool LocalLinkSession::waitCallback(mLockstep* lockstep, unsigned) {
    auto* context = static_cast<LockstepContext*>(lockstep->context);
    if (context) {
        context->waitCount.fetch_add(1, std::memory_order_relaxed);
    }
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
