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

## Phase 1C Findings

Phase 1C adds a hidden developer-only `Local Link Debug` screen. It is not part of the normal player or library flow. Access is intentionally buried behind the Settings screen version row: long-press the version row seven times, then open the revealed debug action.

The screen can:

- select Slot 1 from imported, available `.gba` ROMs
- select Slot 2 from imported, available `.gba` ROMs
- default Slot 2 to the same ROM when only one imported ROM exists
- start the hidden `LocalLinkSession`
- stop the hidden `LocalLinkSession`
- poll `NativeEmulatorBridge.getLocalLinkStatus()` once per second
- show the current phase, scheduler ticks, Slot 1/Slot 2 frame counts, lockstep attachment count, transfer phase, runtime duration, ROM paths, and separate save roots

Save roots remain separated:

```text
files/link_tests/slot_1/
files/link_tests/slot_2/
```

Phase 1C keeps the link debug harness headless. Slot 1 rendering from `LocalLinkSession` is intentionally not wired yet because the existing production renderer is still owned by the single-player `EmulatorSession`. Reusing that path for link rendering would require a native rendering abstraction that can attach a surface to either a single-player session or a local-link slot without introducing global-session conflicts.

The screen does not expose save-state controls during link debug sessions. This avoids creating single-core state snapshots while two cores are expected to remain synchronized.

Phase 1C status: partially working. It provides an on-device developer UI to start, stop, and observe the two-core local link scheduler using imported ROMs. It does not prove real game-level SIO transfer, and it does not render either linked slot yet.

Recommended Phase 1D:

- Add native Slot 1 video-buffer access to `LocalLinkSession`.
- Attach the debug screen's `SurfaceView` to Slot 1 without changing the single-player renderer path.
- Add transfer-attempt counters near the mGBA lockstep/SIO callbacks.
- Add optional Player 1 input routing to the hidden link session.
- Run a known link-capable user-provided ROM pair for 30+ seconds and verify whether SIO transfer activity appears.

## Phase 1D Findings

Phase 1D extends the hidden `Local Link Debug` screen so Slot 1 is visible and controllable while Slot 2 remains headless.

Native additions:

- `LinkedEmulatorSlot::renderFrameToWindow(...)` renders the slot's existing 240x160 software video buffer into an `ANativeWindow`.
- `LocalLinkSession` owns a separate Slot 1 `ANativeWindow` reference, dimensions, and rendered-frame counter.
- `LocalLinkSession::attachSlot1Surface(...)`, `resizeSlot1Surface(...)`, and `detachSlot1Surface(...)` manage the hidden debug surface independently from the production single-player surface.
- The local link scheduler still runs both cores on one native scheduler thread. After each bounded Slot 1/Slot 2 frame step, it renders the latest Slot 1 frame if a debug surface is attached.
- `LocalLinkSession::setInputMask(1, ...)` now has Kotlin/JNI wiring from the debug screen's Player 1 controls.

Kotlin/JNI additions:

- `NativeEmulatorBridge.attachLocalLinkSurface(surface)`
- `NativeEmulatorBridge.resizeLocalLinkSurface(width, height)`
- `NativeEmulatorBridge.detachLocalLinkSurface()`
- `NativeEmulatorBridge.setLocalLinkInputMask(slot, inputMask)`

Debug-screen behavior:

- Slot 1 renders in the hidden Local Link Debug screen.
- Slot 2 runs headless.
- Player 1 controls route to Slot 1 only:
  - D-pad
  - A/B
  - L/R
  - Start/Select
- Input is cleared on app pause, stop, and screen disposal.
- The screen still shows scheduler ticks, Slot 1/Slot 2 frame counts, rendered Slot 1 frame count, lockstep attachment count, and transfer phase.

Single-player emulator behavior remains separate. The production `EmulatorSession`, global single-player surface, audio path, battery saves, save states, and fast-forward controls were not reused for the local link debug renderer.

Current limitations:

- Slot 2 has no video and no controls.
- Audio is still disabled for local-link debug sessions.
- SIO transfer count is not yet instrumented beyond `transferPhase`.
- Real game-level link/trade behavior is still not proven.

Phase 1D status: partially working until device testing confirms Slot 1 video and controls on the hidden screen. Architecturally, Slot 1 rendering/input is now wired without exposing public multiplayer UI.

Recommended Phase 1E:

- Add SIO mode-change and transfer-attempt counters around the lockstep/SIO driver path.
- Add a link activity panel that distinguishes idle, mode-change, transfer-start, transfer-complete, and stall states.
- Test two user-provided link-capable ROM instances for actual in-game cable handshake behavior.
- Add optional scripted/secondary input for Slot 2 only if needed for handshake testing.

## Phase 1E Findings

Phase 1E upgrades the hidden `Local Link Debug` screen from a Slot 1-only viewer into a two-slot control harness.

Implemented approach: single video surface with a `Viewing` toggle.

Why toggle instead of split-screen:

- It keeps the native rendering path simple: one debug `ANativeWindow` is attached at a time.
- It avoids running two Android surface locks per scheduler tick.
- It is enough to guide both local cores toward an in-game link room during feasibility testing.

Native additions:

- `LocalLinkSession::setRenderSlot(slot)` chooses which linked slot renders to the attached debug surface.
- The scheduler renders either Slot 1 or Slot 2 after both cores advance.
- Status now includes:
  - `slot1Rendered`
  - `slot2Rendered`
  - `renderSlot`
  - `sioMode1`
  - `sioMode2`
- `LinkedEmulatorSlot::sioMode()` exposes the current mGBA SIO mode value for each slot.
- Input logging now distinguishes Slot 1 and Slot 2 input masks.

Kotlin/JNI additions:

- `NativeEmulatorBridge.setLocalLinkRenderSlot(slot)`
- Local Link Debug screen `Viewing` toggle:
  - Slot 1
  - Slot 2
- Local Link Debug screen `Input target` toggle:
  - Slot 1
  - Slot 2

Debug-screen behavior:

- User can start one two-core local link session.
- User can view Slot 1 or Slot 2 on the same surface.
- User can route the controls to Slot 1 or Slot 2.
- Switching input target clears both input masks to avoid stuck buttons.
- Both slots keep separate save roots:

```text
files/link_tests/slot_1/
files/link_tests/slot_2/
```

Current limitations:

- No split-screen.
- No audio for either link slot.
- No save states during active local link debug.
- SIO transfer/activity is still inferred from `transferPhase` and SIO mode values; explicit transfer counters are not yet instrumented.
- Real game-level trade/link completion remains unproven.

Phase 1E status: partially working until device testing confirms Slot 2 video and Slot 2 controls. Architecturally, two-slot viewing and input routing are now wired while keeping the screen hidden/debug-only.

Recommended Phase 1F:

- Instrument explicit SIO transfer counters in or around the lockstep driver callbacks.
- Add a compact link activity panel:
  - idle
  - mode changed
  - transfer active
  - transfer completed
  - possible stall
- Add optional pre-link battery-save backup for both `link_tests` slots before starting a session.
- Test whether two local user-provided compatible ROMs can enter an in-game cable room and generate transfer activity.
