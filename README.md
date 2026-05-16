# LinkRoom

Phase 0 Android scaffold for a legal-safe retro handheld emulator app.

## Current Status

This project is a feasibility scaffold only. It includes:

- Kotlin Android app
- Jetpack Compose UI
- Legal-safe onboarding
- ROM picker using Android Storage Access Framework
- In-memory game library placeholder
- Emulator player placeholder screen
- Native C++ placeholder renderer through JNI and CMake

Real emulation is not implemented yet. No emulator core, save states, fast-forward, link cable, online trading, accounts, backend, billing, cloud sync, ROM downloads, ROMs, or copyrighted assets are included.

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
11. Tap the selected file in the library to open the native placeholder player screen.
12. Background and resume the app once to confirm the placeholder surface detaches and reattaches cleanly.

For native placeholder logs:

```powershell
adb logcat -s NativeEmulatorBridge EmulatorRuntime EmulatorSurface LinkRoomNative LinkRoomRenderer GameLibraryViewModel RomPicker
```

## Notes

- Imported ROM metadata is stored in memory only for Phase 0.
- If Android kills and recreates the process, the in-memory library will be empty and files must be selected again.
- The native surface renders a placeholder checkerboard from C++.
- If the native library cannot be loaded, the app should show a graceful placeholder status instead of crashing.
- Future emulator integration should stay behind `EmulatorRuntime` and `NativeEmulatorBridge`.
- License obligations must be reviewed before vendoring or integrating any emulator core.
