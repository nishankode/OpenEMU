# mGBA Preparation

Phase 0.2A prepares the project for mGBA integration, but does not vendor mGBA source yet.

## Current Status

- mGBA source is not present in this repository.
- The app still uses the Phase 0 native placeholder renderer.
- No real ROM boot, audio, save states, fast-forward, link cable, or online functionality is implemented.

## Future Pinning Workflow

When the project is ready to vendor mGBA, pin the upstream source to an explicit release tag or commit:

```powershell
git submodule add https://github.com/mgba-emu/mgba third_party/mgba/upstream
git -C third_party/mgba/upstream checkout <pinned-tag-or-commit>
```

Record the selected tag or commit in `third_party/mgba/NOTICE.md`.

## Integration Rules

- Do not include ROMs, BIOS files, screenshots, box art, or ROM download links.
- Do not modify files under `third_party/mgba/upstream` unless unavoidable.
- If upstream changes are required, keep them as small patches under `third_party/mgba/patches`.
- Keep LinkRoom-owned wrapper code under `app/src/main/cpp/linkroom_core`.
- Keep mGBA behind the native wrapper boundary. Kotlin and Compose code should not depend on mGBA APIs directly.
- Review license obligations before distributing any APK that includes mGBA.
