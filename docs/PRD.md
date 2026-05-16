# PRD: Social GBA Emulator for Android

## Working Name

**LinkRoom**

Avoid using names like Pokémon, Nintendo, Game Boy, GBA, FireRed, Emerald, etc. in the public app name or Play Store listing. Inside the app, detect compatible user-provided games carefully, but do not market using protected game branding.

---

# 1. Product Summary

## One-liner

A polished Android retro handheld emulator with online trade rooms, friend invites, save protection, and link-cable multiplayer for compatible user-owned games.

## Core Differentiator

Most Android emulators focus on running games. This product focuses on **social link play**:

- Find other players inside the emulator
- Create or join online trade rooms
- Invite friends
- Match compatible game versions
- Protect saves before every trade
- Remove setup pain like IP sharing, Discord coordination, manual save copying, or local Wi-Fi-only linking

## MVP Wedge

**Online trade rooms for compatible GBA-era monster-collecting RPGs.**

The first user promise should be:

> Open your legally owned game, enter a trade room, find another player, and complete a real in-game trade without complicated setup.

---

# 2. Problem

## User Problem

Players using Android emulators often cannot complete the full experience of classic link-cable games because trading and multiplayer are hard to set up.

Current workarounds are painful:

- Use cheats to force evolutions
- Use ROM hacks that remove trade requirements
- Use save editors
- Copy save files between devices
- Use local Wi-Fi or Bluetooth only
- Coordinate through Discord or Reddit
- Share IP addresses or use VPN tools
- Buy multiple emulators and hope they are compatible

## Emotional Pain

The player wants the authentic experience.

They do not want to cheat. They want the game to behave as if a real link cable exists.

## Business Pain

Normal emulator apps are crowded. A clean social layer creates differentiation and potential premium monetization.

---

# 3. Target Users

## Primary User

**Android retro RPG player**

- Plays classic handheld RPGs on Android
- Uses emulators casually
- Wants to complete trade evolutions and version-exclusive collection goals
- Does not want complicated technical setup
- Will pay once for a polished, reliable emulator if trust is high

## Secondary User

**Retro multiplayer enthusiast**

- Wants battles, races, link-cable multiplayer, and friend sessions
- More technical
- May tolerate setup pain, but prefers a simple product

## Excluded for MVP

- iOS users
- PC users
- DS/3DS emulation users
- Speedrunners
- Full competitive battle communities
- Users expecting built-in ROM downloads

---

# 4. Product Goals

## MVP Goals

1. Run compatible GBA games smoothly on Android.
2. Allow users to import their own ROMs safely.
3. Provide reliable normal saves and save states.
4. Create user accounts and friend system.
5. Match two users using compatible game versions.
6. Establish an online link session.
7. Complete a real in-game trade.
8. Auto-backup save before any link session.
9. Monetize online trade rooms as a Pro feature after proving demand.

## Non-Goals for MVP

- No ROM store
- No ROM download links
- No bundled copyrighted games
- No official Nintendo or Pokémon branding
- No DS/3DS support
- No global chat at launch
- No tournaments
- No real-money trading
- No marketplace for Pokémon or save files
- No save editing as the main trade system

---

# 5. Success Metrics

## Technical Metrics

- 95%+ successful emulator boot rate for supported user-provided ROMs
- 60 FPS on mid-range Android devices
- Less than 1% save corruption reports
- 90%+ successful local link test sessions
- 75%+ successful online trade completion in controlled beta
- Crash-free sessions above 99%

## Product Metrics

- 40%+ of users import at least one ROM after install
- 25%+ of users create account after importing compatible game
- 15%+ of compatible-game users open Online Trade
- 5%+ of compatible-game users complete at least one trade
- 8%+ free-to-Pro conversion among users who attempt Online Trade

## Retention Metrics

- D1 retention: 35%+
- D7 retention: 15%+
- Repeat trade rate: 25%+ of users who complete first trade

---

# 6. Recommended Tech Stack

## Android App

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Native core integration:** Android NDK + CMake
- **Local DB:** Room or SQLDelight
- **Storage:** Android Storage Access Framework for ROM import and folder access
- **Auth:** Supabase Auth or Firebase Auth
- **Analytics:** PostHog, Firebase Analytics, or self-hosted analytics
- **Crash reporting:** Firebase Crashlytics or Sentry

## Emulator Core

Recommended starting point:

- **mGBA core** as the first candidate because it is mature, accurate, open source, and supports local link-cable behavior.

Important:

- Verify license obligations before shipping.
- Do not modify emulator core files casually unless you understand source distribution obligations.
- Keep your Android frontend and emulator-core changes cleanly separated.
- If the license/compliance burden becomes too high, evaluate other cores before implementation.

## Backend

Recommended simple stack:

- **API:** Node.js/Fastify, NestJS, or Go
- **Realtime:** WebSocket server
- **Database:** Postgres
- **Auth:** Supabase Auth or custom JWT
- **Redis:** presence, matchmaking queues, temporary room state
- **Relay:** WebRTC DataChannels first, fallback relay server later
- **Hosting:** Fly.io, Render, Railway, Hetzner, or AWS

For v1, keep backend simple. The hard problem is link stability, not CRUD APIs.

---

# 7. High-Level Architecture

```text
Android App
  ├── Jetpack Compose UI
  ├── ROM Library
  ├── Save Manager
  ├── Emulator Runtime
  │     ├── Native GBA Core
  │     ├── Video Renderer
  │     ├── Audio Output
  │     ├── Input Mapper
  │     └── Link Adapter Interface
  │
  ├── Online Layer
  │     ├── Auth Client
  │     ├── Friends Client
  │     ├── Matchmaking Client
  │     ├── Realtime Socket
  │     └── Link Session Transport
  │
  └── Monetization
        ├── Pro Unlock
        └── Entitlement Check

Backend
  ├── Auth / User Profiles
  ├── Friend Invites
  ├── Presence
  ├── Matchmaking
  ├── Room Creation
  ├── Session Signaling
  ├── Relay / TURN-like Fallback
  └── Abuse Reports
```

---

# 8. Core Product Flow

## First-Time User Flow

1. User installs app.
2. App shows legal-safe onboarding:
   - “No games included.”
   - “Import your own legally obtained game files.”
3. User grants file access through Android picker.
4. User imports `.gba` or `.zip` file.
5. App scans game hash and identifies compatibility internally.
6. Game appears in library.
7. User taps game.
8. Emulator starts.
9. App prompts optional account creation:
   - “Create profile to use online trade rooms.”
10. User creates account.

## Online Trade Flow

1. User opens compatible game.
2. User taps overlay menu.
3. User selects **Online Trade**.
4. App checks:
   - ROM compatibility
   - save file exists
   - battery save is valid
   - network status
   - Pro entitlement if required
5. App creates automatic save backup.
6. User chooses:
   - Find random trade partner
   - Invite friend
   - Join code
7. App matches compatible users.
8. Both users receive instructions:
   - “Go to the in-game trade room.”
   - “Keep this screen open.”
9. Emulator connects link adapter.
10. Trade happens inside game.
11. App detects session end.
12. App confirms:
   - “Trade session ended.”
   - “Save backup created.”

---

# 9. Feature Requirements

## 9.1 ROM Library

### Must Have

- Import `.gba` files
- Import `.zip` containing one `.gba`
- Store file URI permissions safely
- Display game title
- Detect duplicate imports
- Show recently played
- Show compatibility badge:
  - Compatible with Online Trade
  - Local only
  - Unsupported

### Should Have

- Manual rename
- Favorite games
- Cover art placeholder
- Last played timestamp

### Later

- User-provided cover art
- Auto cover matching, only if legally safe
- Cloud sync of library metadata

---

## 9.2 Emulator Runtime

### Must Have

- Boot GBA ROM
- Render video at full speed
- Audio output
- Touch controls
- Save/load in-game battery save
- Save/load save states
- Fast-forward
- Pause/resume lifecycle handling
- Background save flush on app close

### Should Have

- External controller support
- Layout editor
- Haptic feedback
- Per-game controls
- Screenshot capture

### Later

- Rewind
- Cheats
- Shaders
- Cloud save sync

---

## 9.3 Save Protection

This is critical. Link sessions can fail. Users must feel safe.

### Must Have

- Auto-backup before every trade session
- Manual save-state slots
- Battery save backup
- Restore previous backup
- Visible save timestamp
- Warning if game has not created a valid save yet

### Save Backup Events

Create backup when:

- User starts online session
- User starts local link session
- User exits emulator
- App detects crash recovery
- User manually creates backup

### Backup Naming

```text
/game_id/
  battery/
    current.sav
    backup_2026-05-16_14-30-before-online-trade.sav
  states/
    slot_1.state
    autosave.state
```

---

## 9.4 Account System

### Must Have

- Email login or Google login
- Username
- Avatar color/icon
- User ID
- Block/report user
- Delete account

### Should Have

- Friend list
- Recent trade partners
- Online status
- Private invite code

### Later

- Reputation score
- Verified trade completion count
- Public profile
- Region/language preferences

---

## 9.5 Matchmaking

### Must Have

- Match by game compatibility group
- Match by session type:
  - Trade evolution
  - Version exclusive
  - General trade
  - Battle later
- Friend invite
- Join by room code
- Timeout handling
- Cancel matchmaking

### Compatibility Rules

Matchmaking should check:

- Game family
- Game region/version hash
- Link mode support
- Emulator core version
- App version
- Network quality

### UX Copy

Use generic wording publicly:

- “Compatible trade-enabled game detected.”
- “Online link supported.”
- “Partner found.”
- “Enter the in-game trade room.”

Avoid official brand names in public-facing marketing.

---

## 9.6 Online Link Session

This is the hardest module.

### Must Have

- Session creation
- Peer negotiation
- Link adapter handshake
- Latency test
- Connection state UI
- Reconnect attempt
- Failure recovery
- Safe session end

### Link Session States

```text
Idle
PreparingSaveBackup
Searching
PartnerFound
TestingConnection
WaitingForInGameRoom
LinkConnected
Trading
SessionEnded
Failed
RestoringAvailable
```

### Transport Strategy

Phase 1:

- Same-device local link proof

Phase 2:

- Local network link

Phase 3:

- Online link through WebRTC DataChannel or direct socket

Phase 4:

- Relay fallback for users behind strict NAT

### Important Technical Note

Do not promise global online battles in v1. Trading is the first target because it is less demanding than real-time battles. Battles can come after session stability is proven.

---

## 9.7 Premium Feature System

### Free Tier

- Basic emulator
- ROM import
- Save states
- Fast-forward
- Basic controls
- Local saves
- Maybe limited local link testing

### Pro Tier

Recommended as one-time purchase first:

- Online trade rooms
- Friend invites
- Unlimited room creation
- Save backup timeline
- Cloud backup later
- Premium control skins/layouts
- Priority matchmaking later

### Suggested Pricing Tests

India:

- ₹199 early-bird
- ₹299 standard
- ₹499 premium lifetime

Global:

- $2.99 early-bird
- $4.99 standard
- $7.99 premium lifetime

Avoid subscription for v1 unless server costs become significant.

---

# 10. Step-by-Step Build Plan

## Phase 0: Feasibility Spike

Goal: prove the hard thing before building the product.

### Build

1. Create bare Android app with native emulator core running one user-selected `.gba` file.
2. Compile emulator core with Android NDK.
3. Render frames to Android surface.
4. Send touch input to emulator.
5. Load/save battery save.
6. Test local link-cable support in the core.
7. Run two emulator instances locally and attempt a trade.

### Exit Criteria

- A game boots on Android.
- Game is playable at acceptable speed.
- Save file works.
- Two local emulator instances can reach link/trade state.

### Kill Condition

If local link cannot be made stable, do not proceed to online accounts or UI polish yet.

---

## Phase 1: Basic Emulator MVP

Goal: build a usable emulator without online features.

### Build

1. ROM import screen.
2. Game library.
3. Emulator screen.
4. Touch controls.
5. Pause menu.
6. Save states.
7. Battery save support.
8. Fast-forward.
9. Basic settings.
10. Crash reporting.

### Screens

- Onboarding
- ROM Import
- Game Library
- Emulator Player
- Pause Menu
- Settings

### Exit Criteria

- 20 beta users can import and play games.
- No major save-loss bugs.
- App survives backgrounding and rotation.
- Users can play for 30+ minutes without crash.

---

## Phase 2: Save Protection Layer

Goal: make save reliability a core brand promise.

### Build

1. Automatic battery save backups.
2. Manual save-state slots.
3. Auto-save state on app close.
4. Restore backup UI.
5. Save timeline screen.
6. Pre-link backup system.

### Screens

- Save Timeline
- Restore Backup Modal
- Pre-Trade Safety Check

### Exit Criteria

- User can restore from previous backup.
- Backup exists before every link session.
- No save file is overwritten without backup.

---

## Phase 3: Local Link Proof

Goal: make link cable work locally before online.

### Build

1. Link adapter interface in native core.
2. Same-device two-instance mode.
3. Local session screen.
4. Local connection status.
5. Save backup before local link.
6. Manual disconnect.

### Exit Criteria

- Two local game instances connect.
- Trade can complete in a controlled test.
- Save remains valid after trade.

---

## Phase 4: Online Link Technical Prototype

Goal: prove online link session with two devices.

### Build

1. Minimal backend with room creation.
2. WebSocket signaling.
3. Two-device peer connection.
4. Network link adapter transport.
5. Latency test.
6. Session state UI.
7. Failure handling.

### Test Cases

- Same Wi-Fi
- Different Wi-Fi networks
- Mobile data to Wi-Fi
- Mobile data to mobile data
- Bad connection
- App backgrounded during session
- One user disconnects mid-trade

### Exit Criteria

- Two real Android devices can complete a trade.
- Failed sessions do not corrupt saves.
- App explains failure clearly.

### Kill Condition

If internet latency causes consistent desync, pivot to one of these:

1. Friend-assisted local link only
2. Save-file trade helper with strong warnings
3. Trade-evolution patching tool
4. Desktop relay companion app
5. Cloud save transfer between owned devices

---

## Phase 5: Account + Matchmaking

Goal: turn technical link into a social product.

### Build

1. Signup/login.
2. Username/avatar.
3. Friend list.
4. Invite friend to trade.
5. Join by room code.
6. Random matchmaking queue.
7. Trade intent selector:
   - Trade evolution
   - Version exclusive
   - General trade
8. Report/block user.

### Screens

- Login
- Profile Setup
- Friends
- Online Trade Lobby
- Match Found
- Room Code
- Report User

### Exit Criteria

- Users can find/invite a partner.
- Matchmaking does not pair incompatible games.
- Users understand what to do in-game after matching.

---

## Phase 6: Premium Monetization

Goal: monetize without damaging trust.

### Build

1. Pro entitlement system.
2. Google Play Billing integration.
3. Paywall for Online Trade Rooms.
4. Free trial or limited sessions.
5. Restore purchase.
6. Pricing experiments.

### Recommended Paywall Moment

Show paywall after user has:

1. Imported a compatible game.
2. Opened Online Trade.
3. Seen that partners are available.
4. Understood save backup protection.

Do not show paywall immediately after install.

### Exit Criteria

- Users understand what Pro unlocks.
- Purchases restore correctly.
- No paywall inside active gameplay.

---

## Phase 7: Private Beta

Goal: test reliability with real users before Play Store launch.

### Beta Group

Start with 50 to 100 users from:

- Reddit emulator communities
- Android emulation Discords
- Retro gaming groups
- Your X audience

### Beta Tasks

Ask every beta user to complete:

1. Import game
2. Play 10 minutes
3. Create save
4. Create account
5. Join trade room
6. Attempt trade
7. Restore backup
8. Submit feedback

### Metrics

- Import success
- Emulator crash rate
- Save backup success
- Matchmaking success
- Trade completion rate
- Average setup confusion
- Willingness to pay

---

## Phase 8: Public Launch

Goal: launch as a legal-safe social emulator, not a Pokémon-branded app.

### Play Store Positioning

Use wording like:

> A modern retro handheld emulator with online link rooms, save protection, fast-forward, and friend invites for compatible user-owned games.

Avoid:

- Pokémon in title
- Nintendo in title
- Game Boy in title unless legally reviewed
- ROM download language
- Screenshots from copyrighted games
- “Play Pokémon online” as marketing copy

### Launch Assets

- App icon
- Library screen
- Emulator UI with placeholder/homebrew game
- Online room UI
- Save timeline UI
- Controller layout UI

---

# 11. Screens Needed

## MVP Screens

1. Splash Screen
2. Legal Onboarding
3. ROM Import
4. Game Library
5. Game Detail
6. Emulator Player
7. Pause Menu
8. Save State Menu
9. Save Timeline
10. Control Layout Editor
11. Settings
12. Login
13. Profile Setup
14. Online Trade Lobby
15. Matchmaking Screen
16. Match Found Screen
17. In-Game Instructions Overlay
18. Session Status Overlay
19. Trade Complete Screen
20. Session Failed Screen
21. Friends List
22. Invite Friend Modal
23. Join Room Code Screen
24. Paywall
25. Restore Purchase
26. Report User
27. Account Settings
28. Delete Account

---

# 12. Backend Data Model

## users

```json
{
  "id": "uuid",
  "email": "string",
  "username": "string",
  "avatar": "string",
  "created_at": "datetime",
  "pro_status": "free|pro|expired",
  "last_seen_at": "datetime"
}
```

## games

```json
{
  "id": "uuid",
  "user_id": "uuid",
  "rom_hash": "string",
  "detected_title": "string",
  "compatibility_group": "string",
  "is_trade_supported": true,
  "last_played_at": "datetime"
}
```

## rooms

```json
{
  "id": "uuid",
  "room_code": "string",
  "host_user_id": "uuid",
  "guest_user_id": "uuid|null",
  "compatibility_group": "string",
  "intent": "trade_evolution|version_exclusive|general_trade|battle",
  "status": "waiting|matched|active|ended|failed",
  "created_at": "datetime"
}
```

## sessions

```json
{
  "id": "uuid",
  "room_id": "uuid",
  "transport": "webrtc|relay|local",
  "status": "connecting|active|ended|failed",
  "latency_ms": 42,
  "jitter_ms": 8,
  "started_at": "datetime",
  "ended_at": "datetime|null",
  "failure_reason": "string|null"
}
```

## reports

```json
{
  "id": "uuid",
  "reporter_id": "uuid",
  "reported_user_id": "uuid",
  "room_id": "uuid",
  "reason": "string",
  "created_at": "datetime"
}
```

---

# 13. API Requirements

## Auth

- `POST /auth/signup`
- `POST /auth/login`
- `POST /auth/logout`
- `DELETE /account`

## Profile

- `GET /me`
- `PATCH /me`

## Friends

- `POST /friends/invite`
- `POST /friends/accept`
- `DELETE /friends/:id`
- `GET /friends`

## Rooms

- `POST /rooms`
- `GET /rooms/:code`
- `POST /rooms/:id/join`
- `POST /rooms/:id/leave`
- `POST /matchmaking/enqueue`
- `POST /matchmaking/cancel`

## Session Signaling

- WebSocket: `session.created`
- WebSocket: `peer.joined`
- WebSocket: `connection.offer`
- WebSocket: `connection.answer`
- WebSocket: `connection.ice_candidate`
- WebSocket: `session.state_changed`
- WebSocket: `session.failed`
- WebSocket: `session.ended`

## Reports

- `POST /reports`
- `POST /blocks`

---

# 14. Legal and Compliance Requirements

## Must Follow

- Do not include ROMs.
- Do not link to ROM sites.
- Do not scrape or bundle copyrighted box art.
- Do not use protected game screenshots in Play Store listing.
- Do not use Pokémon/Nintendo/Game Boy branding in app name.
- Include clear disclaimer:
  - “No games are included.”
  - “Users must provide their own legally obtained game files.”
  - “This app is not affiliated with any console manufacturer or game publisher.”
- Provide account deletion.
- Provide block/report for social features.
- Comply with emulator core license obligations.

## Risk Areas

- Trademark usage
- Copyrighted game screenshots
- User-generated illegal ROM sharing
- Multiplayer moderation
- Save corruption liability
- Play Store review rejection due to misleading metadata

---

# 15. Technical Risks

## Risk 1: Online link desync

Online GBA link behavior may fail under real-world latency.

### Mitigation

- Prove local link first.
- Prove same-Wi-Fi link second.
- Prove internet link third.
- Start with trading, not battles.
- Add connection quality check before session.
- Auto-backup saves.

## Risk 2: Save corruption

A failed trade session could leave the user angry.

### Mitigation

- Backup before every session.
- Restore button after failure.
- Never overwrite backup automatically.
- Keep rolling backup timeline.

## Risk 3: Legal rejection

Bad branding could get the app rejected or removed.

### Mitigation

- Generic app name.
- No official franchise names in public listing.
- No ROMs or copyrighted screenshots.
- Legal review before launch.

## Risk 4: Users cannot find partners

Social products suffer from cold start.

### Mitigation

- Room code invites first.
- Friend invites first.
- Random matchmaking second.
- Scheduled community trade hours.
- Discord/X launch events.

## Risk 5: Users refuse subscription

Retro emulator users may dislike monthly pricing.

### Mitigation

- Use one-time Pro unlock first.
- Add subscription only for cloud/server-heavy features later.

---

# 16. Roadmap

## V0.1: Emulator Spike

- Native core booting
- Touch controls
- Save/load
- One ROM import

## V0.2: Playable Emulator

- Library
- Save states
- Fast-forward
- Settings
- Crash reporting

## V0.3: Save Protection

- Save timeline
- Auto backups
- Restore system

## V0.4: Local Link

- Same-device link
- Local test sessions
- Pre-link backup

## V0.5: Online Link Prototype

- Backend room creation
- Two-device online link
- Connection status
- Failure recovery

## V0.6: Social Layer

- Accounts
- Friends
- Invite code
- Matchmaking queue

## V0.7: Premium Beta

- Google Play Billing
- Pro unlock
- Online trade room paywall
- Beta analytics

## V1.0: Public Launch

- Stable emulator
- Online trade rooms
- Save protection
- Friends/invites
- Legal-safe Play Store listing

---

# 17. MVP Scope Cut

If timeline gets too big, keep only:

1. Android emulator
2. ROM import
3. Save protection
4. Account creation
5. Friend invite trade rooms
6. Online trade session
7. One-time Pro unlock

Cut these from v1:

- Global random matchmaking
- Battles
- Public profiles
- Reputation
- Cloud saves
- Cheats
- Shaders
- DS/3DS support
- Tournaments
- Chat

---

# 18. Validation Plan Before Full Build

## Landing Page Test

Headline:

> Trade and battle in classic handheld games online, right from your Android emulator.

CTA:

> Join private beta

Ask signup question:

- What do you want most?
  - Trade evolutions
  - Version-exclusive trades
  - Battles
  - Friend rooms
  - Save backup

## Reddit/X Test Post Angle

Do not say “I built a Pokémon emulator.”

Say:

> I’m testing an Android retro emulator feature that lets two players create an online link room, so compatible games can trade without local Wi-Fi, IP sharing, or save editors. Would you use this?

## Willingness-to-Pay Question

Ask:

> If this worked reliably and protected your save before every trade, would you pay a one-time $4.99 / ₹299 Pro unlock?

Do not ask about monthly subscription first.

---

# 19. Final Recommendation

Build this only if you treat online link play as the core technical bet.

The correct order is:

1. Prove emulator core works.
2. Prove save reliability.
3. Prove local link.
4. Prove online link.
5. Then build accounts, matchmaking, UI polish, and monetization.

Do not start with a beautiful social app and then discover the trade protocol cannot work reliably.

The product becomes worth building only when this sentence is true:

> Two Android users can import their own compatible games, join the same online room, enter the in-game trade area, and complete a trade without losing their saves.

