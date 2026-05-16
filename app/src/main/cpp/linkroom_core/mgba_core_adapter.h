#pragma once

#include <string>

namespace linkroom {

class MgbaCoreAdapter {
public:
    MgbaCoreAdapter() = default;
    ~MgbaCoreAdapter() = default;

    MgbaCoreAdapter(const MgbaCoreAdapter&) = delete;
    MgbaCoreAdapter& operator=(const MgbaCoreAdapter&) = delete;

    bool isCoreAvailable() const;
    std::string statusMessage() const;
};

} // namespace linkroom
