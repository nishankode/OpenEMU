# mGBA Third-Party Source

Phase 0.2B2 vendors and compiles pinned mGBA source for Android NDK integration preparation.

## Current Status

- mGBA source is present under `third_party/mgba/upstream`.
- Pinned upstream tag: `0.10.5`.
- Pinned upstream commit: `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`.
- Upstream URL: `https://github.com/mgba-emu/mgba`.
- The Android native build compiles a minimal static mGBA target named `linkroom_mgba`.
- `linkroom_native` links against actual mGBA implementation code.
- The app still uses the Phase 0 native placeholder renderer.
- No real ROM boot, audio, save states, fast-forward, link cable, or online functionality is implemented.

## Source Pinning Workflow

The current source was pinned with:

```powershell
git clone --branch 0.10.5 --depth 1 https://github.com/mgba-emu/mgba.git third_party/mgba/upstream
git -C third_party/mgba/upstream rev-parse HEAD
```

The nested `.git` directory was removed after recording the exact tag and commit so the repository contains vendored source files rather than a nested working copy.

## Integration Rules

- Do not include ROMs, BIOS files, screenshots, box art, or ROM download links.
- Do not modify files under `third_party/mgba/upstream` unless unavoidable.
- If upstream changes are required, keep them as small patches under `third_party/mgba/patches`.
- Keep LinkRoom-owned wrapper code under `app/src/main/cpp/linkroom_core`.
- Keep mGBA behind the native wrapper boundary. Kotlin and Compose code should not depend on mGBA APIs directly.
- Review license obligations before distributing any APK that includes mGBA.

## Phase 0.2B2 Build Integration

Phase 0.2B2 builds mGBA through upstream CMake with `LIBMGBA_ONLY=ON`, `M_CORE_GBA=ON`, `M_CORE_GB=OFF`, `MINIMAL_CORE=2`, `ENABLE_EXTRA=ON`, and dependency/frontends disabled.

This verifies the Android native toolchain can compile and link actual pinned mGBA implementation code while preserving the placeholder renderer and avoiding real emulation until the next phase.

The Settings screen calls a native smoke-check that creates and releases an mGBA GBA core object, returning a status string. It does not load ROMs.
