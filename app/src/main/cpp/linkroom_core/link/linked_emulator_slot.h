#pragma once

#include <cstdint>
#include <string>
#include <vector>

struct mCore;
struct GBA;
struct GBASIOLockstep;
struct GBASIOLockstepNode;

namespace linkroom {

class LinkedEmulatorSlot {
public:
    LinkedEmulatorSlot() = default;
    ~LinkedEmulatorSlot();

    LinkedEmulatorSlot(const LinkedEmulatorSlot&) = delete;
    LinkedEmulatorSlot& operator=(const LinkedEmulatorSlot&) = delete;

    bool load(
        int slotIndex,
        const std::string& romPath,
        const std::string& saveRoot,
        GBASIOLockstep* lockstep,
        GBASIOLockstepNode* node,
        std::string* error
    );
    void reset();
    void runFrame();
    void setInputMask(std::uint32_t inputMask);
    void release();

    bool isLoaded() const;
    const std::string& romPath() const;
    const std::string& saveRoot() const;
    std::uint64_t framesRun() const;
    GBA* gba() const;

private:
    bool attachBatterySave(std::string* error);

    mCore* core_ = nullptr;
    std::vector<std::uint32_t> videoBuffer_;
    std::string romPath_;
    std::string saveRoot_;
    std::string batteryPath_;
    std::uint32_t inputMask_ = 0;
    std::uint64_t framesRun_ = 0;
    bool loaded_ = false;
};

} // namespace linkroom
