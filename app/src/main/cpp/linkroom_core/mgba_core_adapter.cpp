#include "mgba_core_adapter.h"

#include <mgba/core/core.h>
#include <mgba/core/input.h>
#include <mgba/core/interface.h>
#include <mgba/internal/gba/input.h>

namespace linkroom {

bool MgbaCoreAdapter::isCoreAvailable() const {
    static_assert(mPLATFORM_GBA == 0, "Unexpected mGBA platform enum layout.");
    static_assert(GBA_KEY_A == 0, "Unexpected mGBA input enum layout.");
    struct mCore* core = mCoreCreate(mPLATFORM_GBA);
    if (core == nullptr) {
        return false;
    }
    core->deinit(core);
    return true;
}

std::string MgbaCoreAdapter::statusMessage() const {
    return linkedCoreStatus();
}

std::string MgbaCoreAdapter::linkedCoreStatus() const {
    return isCoreAvailable()
        ? "mGBA core linked: true (0.10.5, GBA core compiled; ROM boot disabled)"
        : "mGBA core linked: false";
}

} // namespace linkroom
