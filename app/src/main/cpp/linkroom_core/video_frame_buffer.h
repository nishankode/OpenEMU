#pragma once

#include <cstdint>

namespace linkroom {

class VideoFrameBuffer {
public:
    static constexpr int kGbaWidth = 240;
    static constexpr int kGbaHeight = 160;
    static constexpr int kBytesPerPixel = 4;

    VideoFrameBuffer() = default;
    ~VideoFrameBuffer() = default;

    VideoFrameBuffer(const VideoFrameBuffer&) = delete;
    VideoFrameBuffer& operator=(const VideoFrameBuffer&) = delete;

    int width() const;
    int height() const;
    int bytesPerPixel() const;
    std::uint32_t* pixels();
};

} // namespace linkroom
