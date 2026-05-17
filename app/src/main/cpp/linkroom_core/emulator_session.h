#pragma once

#include <cstdint>
#include <string>

#include "mgba_core_adapter.h"
#include "save_paths.h"
#include "video_frame_buffer.h"

struct ANativeWindow;

namespace linkroom {

class EmulatorSession {
public:
    EmulatorSession();
    ~EmulatorSession();

    EmulatorSession(const EmulatorSession&) = delete;
    EmulatorSession& operator=(const EmulatorSession&) = delete;

    RomLoadResult loadRom(const std::string& romPath, const std::string& gameRootPath);
    bool runFrame();
    bool renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight);
    int readAudio(std::int16_t* output, int maxSamples);
    void setInputMask(std::uint32_t inputMask);
    void setFastForward(bool enabled);
    std::string saveState(int slot, const std::string& statePath);
    std::string loadState(int slot, const std::string& statePath);
    std::string flushBatterySave();
    void pause();
    void resume();
    void release();

    bool isReleased() const;
    bool hasLoadedRom() const;
    bool isPaused() const;
    std::string statusMessage() const;
    std::string saveStatus() const;
    std::string audioStatus() const;
    int frameStepCount() const;
    std::string fastForwardStatus() const;

private:
    MgbaCoreAdapter coreAdapter_;
    VideoFrameBuffer videoFrameBuffer_;
    SavePaths savePaths_;
    bool paused_ = true;
    bool released_ = false;
};

} // namespace linkroom
