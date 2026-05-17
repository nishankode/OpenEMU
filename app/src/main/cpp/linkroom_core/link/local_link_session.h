#pragma once

#include "link_scheduler.h"
#include "linked_emulator_slot.h"

#include <mgba/core/lockstep.h>
#include <mgba/internal/gba/sio/lockstep.h>

#include <array>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>

struct ANativeWindow;

namespace linkroom {

enum class LocalLinkSchedulerMode : int {
    Stable = 0,
    ExperimentalLockstep = 1
};

class LocalLinkSession {
public:
    LocalLinkSession();
    ~LocalLinkSession();

    LocalLinkSession(const LocalLinkSession&) = delete;
    LocalLinkSession& operator=(const LocalLinkSession&) = delete;

    std::string start(
        const std::string& primaryRomPath,
        const std::string& secondaryRomPath,
        const std::string& baseTestDir,
        LocalLinkSchedulerMode schedulerMode
    );
    void stop();
    std::string status() const;
    void setInputMask(int slot, std::uint32_t inputMask);
    void clearInputMasks();
    void attachSlot1Surface(ANativeWindow* window);
    void resizeSlot1Surface(int width, int height);
    void detachSlot1Surface();
    void setRenderSlot(int slot);

private:
    struct LockstepContext {
        std::mutex mutex;
        std::array<int32_t, 4> cycles {};
        std::atomic<std::uint64_t> signalCount {0};
        std::atomic<std::uint64_t> waitCount {0};
    };

    bool prepareLockstep();
    void releaseLockstep();
    void schedulerTick();
    void runStableSchedulerTickLocked();
    int runExperimentalSchedulerTickLocked();
    std::string statusLocked() const;
    void releaseSlot1WindowLocked();
    void resetDiagnosticsLocked();
    void updateDiagnosticsLocked(int transferPhase, int sioMode1, int sioMode2, int slicesUsed);
    std::string linkWarningLocked(std::int64_t nowMs) const;

    static void lockCallback(mLockstep* lockstep);
    static void unlockCallback(mLockstep* lockstep);
    static bool signalCallback(mLockstep* lockstep, unsigned mask);
    static bool waitCallback(mLockstep* lockstep, unsigned mask);
    static void addCyclesCallback(mLockstep* lockstep, int id, int32_t cycles);
    static int32_t useCyclesCallback(mLockstep* lockstep, int id, int32_t cycles);
    static int32_t unusedCyclesCallback(mLockstep* lockstep, int id);
    static void unloadCallback(mLockstep* lockstep, int id);

    mutable std::mutex mutex_;
    LinkedEmulatorSlot slot1_;
    LinkedEmulatorSlot slot2_;
    LinkScheduler scheduler_;
    GBASIOLockstep lockstep_ {};
    GBASIOLockstepNode node1_ {};
    GBASIOLockstepNode node2_ {};
    LockstepContext lockstepContext_;
    std::string status_ = "local link: idle";
    std::string baseTestDir_;
    LocalLinkSchedulerMode schedulerMode_ = LocalLinkSchedulerMode::Stable;
    ANativeWindow* slot1Window_ = nullptr;
    int slot1WindowWidth_ = 0;
    int slot1WindowHeight_ = 0;
    std::uint64_t slot1RenderedFrames_ = 0;
    std::uint64_t slot2RenderedFrames_ = 0;
    std::uint64_t transferAttemptCount_ = 0;
    std::uint64_t transferCompleteCount_ = 0;
    std::uint64_t sliceLimitHitCount_ = 0;
    std::uint64_t lastSignalSample_ = 0;
    std::uint64_t lastWaitSample_ = 0;
    std::uint64_t lastTickSample_ = 0;
    std::uint64_t signalRatePerSecond_ = 0;
    std::uint64_t waitRatePerSecond_ = 0;
    std::uint64_t schedulerTickRatePerSecond_ = 0;
    int lastSlicesUsed_ = 0;
    std::int64_t startMonotonicMs_ = 0;
    std::int64_t lastTransferActivityMs_ = 0;
    std::int64_t lastModeChangeMs_ = 0;
    std::int64_t lastMetricsSampleMs_ = 0;
    int activeRenderSlot_ = 1;
    int previousTransferPhase_ = 0;
    int previousSioMode1_ = -1;
    int previousSioMode2_ = -1;
    std::atomic<std::uint32_t> slot1InputMask_ {0};
    std::atomic<std::uint32_t> slot2InputMask_ {0};
    bool lockstepReady_ = false;
    bool running_ = false;
};

} // namespace linkroom
