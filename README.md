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

This phase can boot, render, and accept basic touch input for user-provided `.gba` files through mGBA. No audio, game progress saving, battery saves, save states, fast-forward, link cable, online trading, accounts, backend, billing, cloud sync, ROM downloads, ROMs, or copyrighted assets are included.

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
12. Use the on-screen controls to verify button input reaches the game.
13. Background and resume the app once to confirm the surface detaches and reattaches cleanly.
14. Close and reopen the app to confirm onboarding stays complete and the imported ROM metadata returns.

For app state and native runtime logs:

```powershell
adb logcat -s AppPreferences RomMetadataStore NativeEmulatorBridge EmulatorRuntime EmulatorSurface LinkRoomNative LinkRoomRenderer MgbaCoreAdapter GameLibraryViewModel RomPicker
```

## Notes

- Onboarding completion is stored in shared preferences.
- Imported ROM metadata is stored in a small JSON file in app-private storage.
- `.gba` imports are copied into app-private storage and are not recopied on every launch.
- If copied ROM content is missing, the library item is shown as unavailable instead of crashing.
- Game progress saving is not implemented yet. Closing the app and reopening the ROM starts a fresh emulator session.
- The native surface renders mGBA video after a successful `.gba` load and keeps the placeholder checkerboard as a fallback.
- Touch input is limited to simple on-screen controls. External controllers, haptics, and layout customization are not implemented yet.
- If the native library cannot be loaded, the app should show a graceful placeholder status instead of crashing.
- Future emulator integration should stay behind `EmulatorRuntime` and `NativeEmulatorBridge`.
- License obligations must be reviewed before vendoring or integrating any emulator core.
