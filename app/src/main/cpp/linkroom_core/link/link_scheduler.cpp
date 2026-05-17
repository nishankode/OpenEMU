#include "link_scheduler.h"

#include <android/log.h>
#include <chrono>

namespace linkroom {
namespace {
constexpr const char* kTag = "LinkScheduler";
constexpr auto kFrameInterval = std::chrono::microseconds(16667);
}

LinkScheduler::~LinkScheduler() {
    stop();
}

void LinkScheduler::start(TickCallback tickCallback, StatusCallback statusCallback) {
    stop();
    ticks_.store(0);
    stopRequested_.store(false);
    thread_ = std::thread(&LinkScheduler::runLoop, this, std::move(tickCallback), std::move(statusCallback));
}

void LinkScheduler::stop() {
    stopRequested_.store(true);
    if (thread_.joinable()) {
        thread_.join();
    }
    running_.store(false);
}

bool LinkScheduler::isRunning() const {
    return running_.load();
}

std::uint64_t LinkScheduler::ticks() const {
    return ticks_.load();
}

void LinkScheduler::runLoop(TickCallback tickCallback, StatusCallback statusCallback) {
    running_.store(true);
    __android_log_print(ANDROID_LOG_INFO, kTag, "local link scheduler started");
    auto nextFrame = std::chrono::steady_clock::now();
    auto lastWatchdog = nextFrame;

    while (!stopRequested_.load()) {
        tickCallback();
        const auto tick = ticks_.fetch_add(1) + 1;

        const auto now = std::chrono::steady_clock::now();
        if (now - lastWatchdog >= std::chrono::seconds(5)) {
            const std::string status = statusCallback ? statusCallback() : "";
            __android_log_print(
                ANDROID_LOG_INFO,
                kTag,
                "local link watchdog: ticks=%llu %s",
                static_cast<unsigned long long>(tick),
                status.c_str()
            );
            lastWatchdog = now;
        }

        nextFrame += kFrameInterval;
        std::this_thread::sleep_until(nextFrame);
        if (std::chrono::steady_clock::now() > nextFrame + kFrameInterval) {
            __android_log_print(ANDROID_LOG_WARN, kTag, "local link scheduler fell behind; resetting pacing");
            nextFrame = std::chrono::steady_clock::now();
        }
    }

    running_.store(false);
    __android_log_print(ANDROID_LOG_INFO, kTag, "local link scheduler stopped");
}

} // namespace linkroom
