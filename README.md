# LinkRoom

Android feasibility MVP for a legal-safe retro handheld emulator app.

## Current Status

This project is a feasibility scaffold only. It includes:

- Kotlin Android app
- Jetpack Compose UI
- Legal-safe onboarding
- ROM picker using Android Storage Access Framework
- Persisted onboarding completion
- Persisted basic ROM library metadata
- App-private `.gba` copy for native loading
- mGBA native video rendering through JNI and CMake
- Minimal on-screen controls for D-pad, A/B, L/R, Start, and Select
- In-game battery save load/flush for games that support normal saves
- Manual save states in three app-private slots
- Basic stereo PCM audio output through Android AudioTrack
- Basic 2x fast-forward

This phase can boot, render, play basic audio, fast-forward at 2x, accept basic touch input, persist normal in-game battery saves, and save/load manual states for user-provided `.gba` files through mGBA. No rewind, link cable, online trading, accounts, backend, billing, cloud sync, ROM downloads, ROMs, or copyrighted assets are included.

## Supported Emulator Features

- Import user-owned `.gba` files through Android Storage Access Framework.
- Copy `.gba` files into app-private storage for native loading.
- Boot user-provided `.gba` files with mGBA.
- Render video to the native `SurfaceView`.
- Use on-screen D-pad, A/B, L/R, Start, and Select controls.
- Play basic game audio in normal-speed mode.
- Use 2x fast-forward. Audio is muted while fast-forward is active.
- Use normal in-game battery saves.
- Use three manual save-state slots.
- Restore imported ROM metadata after app restart.

## Requirements

- Android Studio or Android SDK command line tools
- JDK 17
- Android SDK platform 35
- Android NDK
- CMake 3.22.1

## Build

From the project root:

```powershell
.\gradlew :app:assembleDebug
```

## Run On A Physical Android Phone

1. Enable Developer Options on the phone.
2. Enable USB debugging.
3. Connect the phone with USB.
4. Confirm the debugging prompt on the phone.
5. Verify the Android SDK `platform-tools` directory is on your `PATH`, or run `adb` from the SDK folder.
6. Verify the device is visible:

```powershell
adb devices
```

The output should list one device with the `device` state. If it says `unauthorized`, unlock the phone and accept the USB debugging prompt.

7. Install the debug build:

```powershell
.\gradlew :app:installDebug
```

8. Open LinkRoom on the phone.
9. Continue past onboarding.
10. Tap import and select your own legally obtained `.gba` or `.zip` file.
11. Tap the selected `.gba` file in the library to open the native player screen.
12. Confirm video renders and basic game audio is audible.
13. Use the on-screen controls to verify button input reaches the game.
14. Toggle 2x fast-forward, then disable it and confirm normal speed returns.
15. Save state to Slot 1, move somewhere else in-game, then load Slot 1 to confirm the saved point returns.
16. Use the game's normal in-game save flow, then leave the emulator to flush `current.sav`.
17. Reopen the same ROM and confirm the game sees the previous in-game save.
18. Background and resume the app once to confirm video/audio pause and resume cleanly.
19. Close and reopen the app to confirm onboarding stays complete and the imported ROM metadata returns.

For app state and native runtime logs:

```powershell
adb logcat -s AppPreferences RomMetadataStore NativeEmulatorBridge EmulatorRuntime EmulatorSurface LinkRoomNative LinkRoomRenderer MgbaCoreAdapter GameLibraryViewModel RomPicker AudioTrack
```

## Manual Test Checklist

- Fresh install launches onboarding once.
- Importing a valid `.gba` adds it to the library.
- Reopening the app restores the imported ROM metadata.
- Opening a ROM boots mGBA and renders video.
- On-screen controls affect gameplay.
- Audio is audible at normal speed.
- Toggling 2x fast-forward visibly speeds up gameplay.
- Turning fast-forward off returns to normal speed and audio resumes.
- In-game battery save creates/updates `files/games/{rom_id}/battery/current.sav`.
- Reopening the same ROM loads the previous in-game battery save.
- Saving Slot 1 writes `files/games/{rom_id}/states/slot_1.ss`.
- Loading Slot 1 returns to the saved point.
- Loading an empty slot shows an error and does not crash.
- Background/resume keeps the emulator stable.
- Back navigation releases the emulator and flushes battery save data.
- Surface destroy/recreate during rotation or app switch does not crash.

## Notes

- Onboarding completion is stored in shared preferences.
- Imported ROM metadata is stored in a small JSON file in app-private storage.
- `.gba` imports are copied into app-private storage and are not recopied on every launch.
- If copied ROM content is missing, the library item is shown as unavailable instead of crashing.
- Normal in-game battery saves are stored at `files/games/{rom_id}/battery/current.sav`.
- Before replacing an existing battery save, the app attempts a `backup-before-flush-{timestamp}.sav` copy in the same battery directory.
- Manual save states are stored at `files/games/{rom_id}/states/slot_1.ss`, `slot_2.ss`, and `slot_3.ss`.
- Save states and normal in-game battery saves are separate; battery saves still flush on pause/release.
- The native surface renders mGBA video after a successful `.gba` load and keeps the placeholder checkerboard as a fallback.
- Basic audio is streamed from mGBA into a native ring buffer and played with Android AudioTrack. If audio initialization fails on a device, video and controls should continue to work.
- Fast-forward currently supports 2x only. Audio is muted while fast-forward is active, then resumes when normal speed returns.
- Touch input is limited to simple on-screen controls. External controllers, haptics, and layout customization are not implemented yet.
- If the native library cannot be loaded, the app should show a graceful placeholder status instead of crashing.
- Future emulator integration should stay behind `EmulatorRuntime` and `NativeEmulatorBridge`.
- License obligations must be reviewed before vendoring or integrating any emulator core.
