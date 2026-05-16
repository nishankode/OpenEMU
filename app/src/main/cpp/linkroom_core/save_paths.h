#pragma once

#include <cstdint>
#include <string>

namespace linkroom {

class SavePaths {
public:
    static constexpr const char* kBatteryDirectory = "battery";
    static constexpr const char* kCurrentBatterySave = "current.sav";
    static constexpr const char* kBatteryBackupPrefix = "backup-before-flush-";
    static constexpr const char* kStatesDirectory = "states";

    SavePaths() = default;
    explicit SavePaths(std::string gameRoot);
    ~SavePaths() = default;

    SavePaths(const SavePaths&) = delete;
    SavePaths& operator=(const SavePaths&) = delete;

    const std::string& gameRoot() const;
    std::string batteryDirectory() const;
    std::string currentBatterySave() const;
    std::string batteryBackupSave(std::int64_t timestampMillis) const;
    std::string statesDirectory() const;

private:
    std::string gameRoot_;
};

} // namespace linkroom
