#include "mgba_core_adapter.h"

namespace linkroom {

bool MgbaCoreAdapter::isCoreAvailable() const {
    return false;
}

std::string MgbaCoreAdapter::statusMessage() const {
    return "mGBA source is not vendored yet; Phase 0.2A provides integration skeleton only.";
}

} // namespace linkroom
