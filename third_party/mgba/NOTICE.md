# mGBA Notice

Selected emulator core: mGBA.

Phase 0.2B1 status:

- Upstream source is vendored under `third_party/mgba/upstream`.
- Upstream URL: `https://github.com/mgba-emu/mgba`.
- Pinned tag: `0.10.5`.
- Pinned commit: `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`.
- License: Mozilla Public License 2.0 (MPL-2.0).
- License text: `third_party/mgba/LICENSE` and `third_party/mgba/upstream/LICENSE`.
- Local patches: none.
- Native integration level: header-verified only. The full mGBA core is not linked or used for ROM boot yet.

Future distribution checklist:

- Include or reference the exact upstream license text.
- Preserve upstream copyright notices.
- Publish source for MPL-covered files and any changes to those files when distributing builds that include mGBA.
- Do not include ROMs, BIOS files, copyrighted assets, screenshots, or ROM download links.
