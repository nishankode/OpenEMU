# Phase 1A: Local Link-Cable Feasibility

## Summary

Local GBA link-cable support looks feasible with the pinned mGBA 0.10.5 source, but it is not a small UI feature. mGBA has a local lockstep Serial I/O implementation that can attach multiple GBA cores to a shared link object. Our current Android app can compile the relevant mGBA link implementation, and a hidden native smoke harness can create two GBA cores, create a shared `GBASIOLockstep`, attach two lockstep nodes, and install those nodes as GBA SIO drivers.

This phase does not boot two ROMs, render two games, trade, or expose link UI. The current single-player emulator behavior remains the production path.

## Feasibility Result

Feasible, with architecture changes.

mGBA supports same-process local link through lockstep SIO drivers. The important constraint is that linked cores must be stepped in a coordinated way. Running two independent emulator threads at normal speed and hoping the SIO layer catches up is risky. The next phase should build a dedicated two-core native link session that owns both cores and advances them together.

## mGBA APIs And Files Found

Relevant upstream files:

- `third_party/mgba/upstream/include/mgba/core/lockstep.h`
- `third_party/mgba/upstream/src/core/lockstep.c`
- `third_party/mgba/upstream/include/mgba/internal/gba/sio.h`
- `third_party/mgba/upstream/src/gba/sio.c`
- `third_party/mgba/upstream/include/mgba/internal/gba/sio/lockstep.h`
- `third_party/mgba/upstream/src/gba/sio/lockstep.c`
- `third_party/mgba/upstream/include/mgba/internal/gba/gba.h`
- `third_party/mgba/upstream/src/gba/core.c`

Key APIs and structs:

- `mCoreCreate(mPLATFORM_GBA)`: creates each GBA core.
- `mCore::board`: gives access to the underlying `GBA` board for internal SIO wiring.
- `struct GBA`: contains `GBASIO sio`.
- `struct GBASIO`: owns SIO mode and active drivers.
- `GBASIOSetDriver(...)`: installs SIO drivers for `SIO_MULTI`, `SIO_NORMAL_8`, `SIO_NORMAL_32`, or `SIO_JOYBUS`.
- `struct GBASIOLockstep`: shared local link state for up to `MAX_GBAS` players.
- `GBASIOLockstepInit(...)`: initializes the GBA lockstep object.
- `struct GBASIOLockstepNode`: per-core SIO driver node.
- `GBASIOLockstepNodeCreate(...)`: initializes a node as a `GBASIODriver`.
- `GBASIOLockstepAttachNode(...)`: attaches a node to shared lockstep state.
- `GBASIOLockstepDetachNode(...)`: removes a node from shared lockstep state.
- `mLockstepInit(...)` / `mLockstepDeinit(...)`: initializes the generic lockstep base.

The GBA lockstep implementation handles both multiplayer SIO and normal serial modes:

- `SIO_MULTI`
- `SIO_NORMAL_8`
- `SIO_NORMAL_32`

The implementation also uses timing events and early exits to keep cores synchronized during transfers.

## Current App Architecture Fit

Current architecture is single-session:

- Kotlin owns one `EmulatorRuntime`.
- JNI owns one global `linkroom::EmulatorSession gSession`.
- The native emulation thread advances one `EmulatorSession`.
- One Android surface is attached at a time.
- Audio is drained from one core.
- Battery saves and save states are keyed to one `RomHandle`.

This is good for single-player, but not enough for real local link. A real local link session should not be implemented by creating two unrelated global sessions. It needs a new native owner that holds both cores and the shared lockstep object.

## Hidden Smoke Harness

Added hidden/debug-only wiring:

- Kotlin: `NativeEmulatorBridge.runLocalLinkSmokeTest()`
- JNI: `nativeRunLocalLinkSmokeTest()`
- Native: `MgbaCoreAdapter::localLinkSmokeStatus()`

The harness is intentionally not exposed in normal UI. It performs setup only:

1. Create two GBA mGBA cores.
2. Initialize both cores.
3. Create one `GBASIOLockstep`.
4. Create two `GBASIOLockstepNode`s.
5. Attach both nodes to the lockstep object.
6. Install each node as the `SIO_MULTI` and `SIO_NORMAL_32` driver for its core.
7. Clean everything up.

The hidden status string reports success if both nodes attach and driver installation reaches cleanup.

Build note: our existing minimal mGBA static target did not link `src/gba/sio/lockstep.c` by default. Phase 1A explicitly adds that upstream source file to `linkroom_native` without modifying upstream mGBA files.

## Required Native Architecture Changes

Recommended new native structure:

```text
app/src/main/cpp/linkroom_core/
  link/
    local_link_session.h/.cpp
    linked_emulator_slot.h/.cpp
    link_scheduler.h/.cpp
    link_save_coordinator.h/.cpp
```

`LocalLinkSession` should own:

- two `MgbaCoreAdapter`-like core objects, or a lower-level split of the current adapter
- one `GBASIOLockstep`
- two `GBASIOLockstepNode`s
- a deterministic stepping loop
- separate input masks for player 1 and player 2
- explicit save roots for each ROM

The current `MgbaCoreAdapter` mixes core ownership, video, audio, input, battery saves, save states, and rendering. For link, split core ownership from rendering/audio so two cores can run while the UI initially renders only one side.

## Timing And Synchronization Risks

Main risk: desync or deadlock if one core advances too far ahead of the other.

mGBA lockstep uses:

- `mTimingEvent`
- shared transfer phases
- `mLockstep::signal`
- `mLockstep::wait`
- cycle accounting through `addCycles`, `useCycles`, and `unusedCycles`

The generic `mLockstep` object initializes callbacks to `NULL`; a production local link session likely needs concrete synchronization callbacks if cores run on separate threads. The safer first implementation is a single native scheduler thread that alternates both cores and avoids long blocking waits.

Recommended early scheduler:

1. Load both ROMs.
2. Attach both SIO nodes before reset/run.
3. Step core A for a bounded slice.
4. Step core B for a bounded slice.
5. Render only player 1 initially.
6. Keep both cores near the same frame/cycle budget.
7. Add watchdog logging for stalls.

## Save Handling Risks

Two linked games must never share save paths.

Risks:

- accidentally using the same `rom_id` root for both players
- flushing one battery save while the other core is mid-transfer
- save-state capture while SIO transfer is active
- restoring a state for one core but not the other, causing link desync

Recommendation:

- Require distinct `gameRootPath` values for each linked slot.
- Flush both battery saves on pause/release.
- Disable single-slot save-state loading during active link until paired link-state snapshots exist.
- Before any future trade session, create battery save backups for both slots.

## Audio And Rendering Implications

Rendering:

- Phase 1B should render only player 1 first.
- Player 2 can run headless until the link protocol is proven.
- Later, a split-screen/debug view can render both 240x160 buffers.

Audio:

- Start with player 1 audio only.
- Mute player 2 audio to avoid double-mixing and timing pressure.
- Do not let either core block on audio output.

Input:

- Maintain two independent input masks.
- Phase 1B can map on-screen controls only to player 1 and leave player 2 idle or scripted.

## Recommended Next Phase

Phase 1B: local two-core native prototype, still hidden/debug-only.

Acceptance for Phase 1B:

- Create `LocalLinkSession`.
- Load the same user-selected ROM into two separate app-private test roots.
- Attach a shared `GBASIOLockstep` before reset/run.
- Run both cores on one native scheduler thread.
- Render player 1 only.
- Log SIO mode changes and transfer attempts.
- Keep single-player `EmulatorSession` untouched.
- No public link UI.

Phase 1C can add a hidden two-instance UI only after Phase 1B proves no deadlocks and no save corruption in a controlled ROM/link test.

## Phase 1B Findings

Phase 1B adds a hidden native two-core prototype:

```text
app/src/main/cpp/linkroom_core/link/
  local_link_session.h/.cpp
  linked_emulator_slot.h/.cpp
  link_scheduler.h/.cpp
```

Hidden JNI/Kotlin entry points:

- `NativeEmulatorBridge.startLocalLinkTest(primaryRomPath, secondaryRomPath, baseTestDir)`
- `NativeEmulatorBridge.stopLocalLinkTest()`
- `NativeEmulatorBridge.getLocalLinkStatus()`

These methods are not exposed in normal app UI.

The prototype can:

1. Create two mGBA GBA cores.
2. Load two ROM paths. The same ROM path is allowed for both slots during testing.
3. Use separate save roots:
   - `baseTestDir/slot_1`
   - `baseTestDir/slot_2`
4. Create one shared `GBASIOLockstep`.
5. Create and attach two `GBASIOLockstepNode` objects.
6. Install the nodes as `SIO_MULTI` and `SIO_NORMAL_32` drivers.
7. Reset both cores after link driver installation.
8. Run both cores on one hidden native scheduler thread.
9. Advance slot 1 and slot 2 in bounded alternating frame steps.
10. Log scheduler watchdog status every few seconds.

Current limitations:

- The prototype is headless. It does not render either linked slot yet.
- Player 2 is idle.
- Audio is not mixed for the link session.
- No real SIO transfer has been proven yet.
- The lockstep callbacks are minimal same-thread callbacks. They avoid missing callback crashes, but they are not yet proof of robust transfer synchronization under real link activity.
- Battery save roots are separated, but linked-session save lifecycle still needs a dedicated save coordinator before user-facing link tests.

Phase 1B status: partially working. It proves two ROM-backed cores can be loaded into one hidden session and attached to one lockstep object, with a scheduler thread able to run them together. It does not yet prove a game-level cable handshake or trade room connection.

Recommended Phase 1C:

- Add a hidden developer-only link test screen or command path that starts `LocalLinkSession` from an imported ROM.
- Show `getLocalLinkStatus()` live for 30+ seconds.
- Render slot 1 from the link session, still keeping slot 2 headless.
- Add SIO mode-change and transfer-attempt counters to `LocalLinkSession`.
- Test with a known link-capable user-provided ROM.
- Keep save states disabled during active local-link tests.
