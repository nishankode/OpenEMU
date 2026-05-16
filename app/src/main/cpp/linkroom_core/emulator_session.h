#pragma once

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

    RomLoadResult loadRom(const std::string& romPath);
    bool runFrame();
    bool renderFrameToWindow(ANativeWindow* window, int windowWidth, int windowHeight);
    void pause();
    void resume();
    void release();

    bool isReleased() const;
    bool hasLoadedRom() const;
    bool isPaused() const;
    std::string statusMessage() const;

private:
    MgbaCoreAdapter coreAdapter_;
    VideoFrameBuffer videoFrameBuffer_;
    SavePaths savePaths_;
    bool paused_ = true;
    bool released_ = false;
};

} // namespace linkroom
