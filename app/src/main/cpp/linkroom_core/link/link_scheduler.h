#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <string>
#include <thread>

namespace linkroom {

class LinkScheduler {
public:
    using TickCallback = std::function<void()>;
    using StatusCallback = std::function<std::string()>;

    LinkScheduler() = default;
    ~LinkScheduler();

    LinkScheduler(const LinkScheduler&) = delete;
    LinkScheduler& operator=(const LinkScheduler&) = delete;

    void start(TickCallback tickCallback, StatusCallback statusCallback);
    void stop();
    bool isRunning() const;
    std::uint64_t ticks() const;

private:
    void runLoop(TickCallback tickCallback, StatusCallback statusCallback);

    std::thread thread_;
    std::atomic<bool> stopRequested_{true};
    std::atomic<bool> running_{false};
    std::atomic<std::uint64_t> ticks_{0};
};

} // namespace linkroom
