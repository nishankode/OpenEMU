#include "placeholder_renderer.h"

#include <android/log.h>
#include <algorithm>
#include <cstdint>

namespace {
constexpr const char* kTag = "LinkRoomRenderer";
}

void render_placeholder_frame(ANativeWindow* window, int width, int height) {
    if (window == nullptr || width <= 0 || height <= 0) {
        return;
    }

    if (ANativeWindow_setBuffersGeometry(window, width, height, WINDOW_FORMAT_RGBA_8888) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Unable to set buffer geometry.");
        return;
    }

    ANativeWindow_Buffer buffer;
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Unable to lock native window.");
        return;
    }

    auto* pixels = static_cast<uint32_t*>(buffer.bits);
    if (pixels == nullptr || buffer.stride <= 0 || buffer.width <= 0 || buffer.height <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Native window buffer is invalid.");
        ANativeWindow_unlockAndPost(window);
        return;
    }

    const int draw_width = std::min(width, buffer.width);
    const int draw_height = std::min(height, buffer.height);
    const int stride = buffer.stride;
    const int tile = std::max(12, std::min(draw_width, draw_height) / 12);

    for (int y = 0; y < draw_height; ++y) {
        for (int x = 0; x < draw_width; ++x) {
            const bool checker = ((x / tile) + (y / tile)) % 2 == 0;
            const uint8_t r = checker ? 18 : 15;
            const uint8_t g = checker ? 112 : 78;
            const uint8_t b = checker ? 104 : 96;
            pixels[y * stride + x] =
                (0xFFu << 24u) |
                (static_cast<uint32_t>(b) << 16u) |
                (static_cast<uint32_t>(g) << 8u) |
                static_cast<uint32_t>(r);
        }
    }

    ANativeWindow_unlockAndPost(window);
}
