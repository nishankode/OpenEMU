# LinkRoom

Phase 0 Android scaffold for a legal-safe retro handheld emulator app.

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
- Basic stereo PCM audio output through Android AudioTrack
- Basic 2x fast-forward

This phase can boot, render, play basic audio, fast-forward at 2x, accept basic touch input, and persist normal in-game battery saves for user-provided `.gba` files through mGBA. No save states, rewind, link cable, online trading, accounts, backend, billing, cloud sync, ROM downloads, ROMs, or copyrighted assets are included.

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
15. Use the game's normal in-game save flow, then leave the emulator to flush `current.sav`.
16. Reopen the same ROM and confirm the game sees the previous in-game save.
17. Background and resume the app once to confirm video/audio pause and resume cleanly.
18. Close and reopen the app to confirm onboarding stays complete and the imported ROM metadata returns.

For app state and native runtime logs:

```powershell
adb logcat -s AppPreferences RomMetadataStore NativeEmulatorBridge EmulatorRuntime EmulatorSurface LinkRoomNative LinkRoomRenderer MgbaCoreAdapter GameLibraryViewModel RomPicker AudioTrack
```

## Notes

- Onboarding completion is stored in shared preferences.
- Imported ROM metadata is stored in a small JSON file in app-private storage.
- `.gba` imports are copied into app-private storage and are not recopied on every launch.
- If copied ROM content is missing, the library item is shown as unavailable instead of crashing.
- Normal in-game battery saves are stored at `files/games/{rom_id}/battery/current.sav`.
- Before replacing an existing battery save, the app attempts a `backup-before-flush-{timestamp}.sav` copy in the same battery directory.
- Save states are not implemented yet. Closing and reopening the ROM starts a fresh emulator session that can load the prior in-game battery save.
- The native surface renders mGBA video after a successful `.gba` load and keeps the placeholder checkerboard as a fallback.
- Basic audio is streamed from mGBA into a native ring buffer and played with Android AudioTrack. If audio initialization fails on a device, video and controls should continue to work.
- Fast-forward currently supports 2x only. Audio is muted while fast-forward is active, then resumes when normal speed returns.
- Touch input is limited to simple on-screen controls. External controllers, haptics, and layout customization are not implemented yet.
- If the native library cannot be loaded, the app should show a graceful placeholder status instead of crashing.
- Future emulator integration should stay behind `EmulatorRuntime` and `NativeEmulatorBridge`.
- License obligations must be reviewed before vendoring or integrating any emulator core.
