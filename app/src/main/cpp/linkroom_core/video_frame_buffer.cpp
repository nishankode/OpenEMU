#include "video_frame_buffer.h"

namespace linkroom {

int VideoFrameBuffer::width() const {
    return kGbaWidth;
}

int VideoFrameBuffer::height() const {
    return kGbaHeight;
}

int VideoFrameBuffer::bytesPerPixel() const {
    return kBytesPerPixel;
}

std::uint32_t* VideoFrameBuffer::pixels() {
    return nullptr;
}

} // namespace linkroom
