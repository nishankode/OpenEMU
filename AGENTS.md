# AGENTS.md

## Project

We are building an Android retro handheld emulator app with a future social online link/trade feature.

## Current priority

Build the project phase by phase. Do not implement social trading, accounts, backend, matchmaking, billing, or cloud sync until explicitly requested.

## Tech direction

- Android app: Kotlin + Jetpack Compose.
- Build system: Gradle.
- Native code: Android NDK/CMake only when needed.
- Emulator core: integrate through a clean native boundary.
- Keep emulator runtime separate from UI.
- Keep save handling safe and explicit.

## Rules

- Read docs/PRD.md before planning or coding.
- Implement only the requested phase.
- Before coding, summarize the exact files you plan to create/change.
- Prefer small, reviewable commits/patches.
- Do not add unnecessary dependencies.
- Do not include ROMs, copyrighted game assets, screenshots, or ROM download links.
- Do not use Pokémon/Nintendo/Game Boy branding in app metadata.
- Add clear TODOs where native emulator integration is stubbed.
- After implementation, run build/test commands and report results.
