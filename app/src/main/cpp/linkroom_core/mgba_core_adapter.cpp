#include "mgba_core_adapter.h"

#include <mgba/core/core.h>
#include <mgba/core/input.h>
#include <mgba/core/interface.h>
#include <mgba/internal/gba/input.h>

namespace linkroom {

bool MgbaCoreAdapter::isCoreAvailable() const {
    static_assert(mPLATFORM_GBA == 0, "Unexpected mGBA platform enum layout.");
    static_assert(GBA_KEY_A == 0, "Unexpected mGBA input enum layout.");
    return false;
}

std::string MgbaCoreAdapter::statusMessage() const {
    return "mGBA 0.10.5 headers are available; full core linking and ROM boot are not implemented yet.";
}

} // namespace linkroom
