#include "save_paths.h"

namespace linkroom {

SavePaths::SavePaths(std::string gameRoot) : gameRoot_(std::move(gameRoot)) {}

const std::string& SavePaths::gameRoot() const {
    return gameRoot_;
}

std::string SavePaths::batteryDirectory() const {
    if (gameRoot_.empty()) {
        return kBatteryDirectory;
    }
    return gameRoot_ + "/" + kBatteryDirectory;
}

std::string SavePaths::currentBatterySave() const {
    return batteryDirectory() + "/" + kCurrentBatterySave;
}

std::string SavePaths::batteryBackupSave(std::int64_t timestampMillis) const {
    return batteryDirectory() + "/" + kBatteryBackupPrefix + std::to_string(timestampMillis) + ".sav";
}

std::string SavePaths::statesDirectory() const {
    if (gameRoot_.empty()) {
        return kStatesDirectory;
    }
    return gameRoot_ + "/" + kStatesDirectory;
}

} // namespace linkroom
