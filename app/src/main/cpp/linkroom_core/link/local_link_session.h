#pragma once

#include "link_scheduler.h"
#include "linked_emulator_slot.h"

#include <mgba/core/lockstep.h>
#include <mgba/internal/gba/sio/lockstep.h>

#include <array>
#include <cstdint>
#include <mutex>
#include <string>

struct ANativeWindow;

namespace linkroom {

class LocalLinkSession {
public:
    LocalLinkSession();
    ~LocalLinkSession();

    LocalLinkSession(const LocalLinkSession&) = delete;
    LocalLinkSession& operator=(const LocalLinkSession&) = delete;

    std::string start(const std::string& primaryRomPath, const std::string& secondaryRomPath, const std::string& baseTestDir);
    void stop();
    std::string status() const;
    void setInputMask(int slot, std::uint32_t inputMask);
    void attachSlot1Surface(ANativeWindow* window);
    void resizeSlot1Surface(int width, int height);
    void detachSlot1Surface();
    void setRenderSlot(int slot);

private:
    struct LockstepContext {
        std::mutex mutex;
        std::array<int32_t, 4> cycles {};
    };

    bool prepareLockstep();
    void releaseLockstep();
    void schedulerTick();
    std::string statusLocked() const;
    void releaseSlot1WindowLocked();

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
    ANativeWindow* slot1Window_ = nullptr;
    int slot1WindowWidth_ = 0;
    int slot1WindowHeight_ = 0;
    std::uint64_t slot1RenderedFrames_ = 0;
    std::uint64_t slot2RenderedFrames_ = 0;
    int activeRenderSlot_ = 1;
    bool lockstepReady_ = false;
    bool running_ = false;
};

} // namespace linkroom
