#pragma once

#include <string>

#include "mgba_core_adapter.h"
#include "save_paths.h"
#include "video_frame_buffer.h"

namespace linkroom {

class EmulatorSession {
public:
    EmulatorSession();
    ~EmulatorSession();

    EmulatorSession(const EmulatorSession&) = delete;
    EmulatorSession& operator=(const EmulatorSession&) = delete;

    bool loadRomPlaceholder(const std::string& romUri);
    void pause();
    void resume();
    void release();

    bool isReleased() const;
    std::string statusMessage() const;

private:
    MgbaCoreAdapter coreAdapter_;
    VideoFrameBuffer videoFrameBuffer_;
    SavePaths savePaths_;
    bool paused_ = true;
    bool released_ = false;
};

} // namespace linkroom
