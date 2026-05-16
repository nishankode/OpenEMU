# mGBA Third-Party Source

Phase 0.2B3 vendors and compiles pinned mGBA source for Android NDK integration preparation and adds a native ROM boot probe for user-selected `.gba` files copied into app-private storage.

## Current Status

- mGBA source is present under `third_party/mgba/upstream`.
- Pinned upstream tag: `0.10.5`.
- Pinned upstream commit: `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`.
- Upstream URL: `https://github.com/mgba-emu/mgba`.
- The Android native build compiles a minimal static mGBA target named `linkroom_mgba`.
- `linkroom_native` links against actual mGBA implementation code.
- The app still uses the Phase 0 native placeholder renderer.
- User-selected `.gba` files are copied from Android SAF into app-private storage before native loading.
- Native code can create an mGBA GBA core, load the copied ROM, reset it, and run a short boot probe.
- No mGBA video presentation, audio, save states, battery saves, fast-forward, link cable, or online functionality is implemented.

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

## Phase 0.2B3 ROM Boot Probe

The Kotlin import path copies only selected `.gba` files into app-private storage under `files/imported_roms/{rom-id}.gba`. Native code never receives raw SAF URIs.

The native boot probe sequence is:

1. Create an mGBA GBA core with `mCoreCreate(mPLATFORM_GBA)`.
2. Initialize the core and config.
3. Attach an internal video buffer so mGBA can run frames safely.
4. Open the copied ROM path through mGBA's `VFileOpen`.
5. Verify `core->isROM`.
6. Load with `core->loadROM`.
7. Reset the core and run a few frames.
8. Return a clear status string to Kotlin.

The existing placeholder `SurfaceView` renderer remains active. Real mGBA video frames are intentionally not displayed in this phase.
