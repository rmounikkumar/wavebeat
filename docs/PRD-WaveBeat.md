# WaveBeat — Product Requirements Document (PRD)

**Version:** 1.0
**Status:** Approved draft (living document)
**Date:** 2026-08-30
**Author:** WaveBeat build sessions (AI-assisted development with user review)
**Repository:** https://github.com/rmounikkumar/wavebeat

---

## Table of Contents

1. Executive Summary
2. Product Overview & Vision
3. Goals & Success Metrics
4. Personas & Use Cases
5. Scope (In Scope / Out of Scope)
6. Functional Requirements
7. Non-Functional Requirements
8. User Flows
9. Information Architecture & Screens
10. Functional Specification by Module
11. Architecture Overview
12. Audio Engine & DSP Pipeline
13. Data Layer & State Management
14. Project File Structure
15. Tech Stack & Build Configuration
16. Development Timeline & Session History
17. Bugs Discovered, Root Causes & Fixes
18. Testing & Quality Assurance
19. Design Decisions — Pros & Cons
20. Known Limitations & Risks
21. Security & Privacy
22. Release Management
23. Roadmap (Next Steps)
24. Glossary

---

## 1. Executive Summary

WaveBeat is a lightweight, offline-first Android music player that plays audio files stored locally on the device (MediaStore). It is written in **Kotlin** on **Media3/ExoPlayer 1.2.0** with a custom dark UI, haptics, an Equalizer, playlists, favorites, and a fully custom player overlay plus a mini-player. It supports a background **foreground media service** with a Media3 session and notification, configurable audio effects (Equalizer presets, Bass, Loudness, 8D rotation, 3D stereo widening, and algorithmic Room Reverb), a sleep timer, lyrics panel, live library search, and an animated home screen.

The product was built iteratively through hands-on sessions that included **on-device testing via ADB**, crash/ANR audits, and a series of bug fixes shipped as GitHub releases **v1.0 → v1.5**. This document consolidates the full product: requirements, architecture, feature behavior, state/persistence schema, the complete development history (including what worked and what did not), pros & cons of key decisions, known limitations, and roadmap.

---

## 2. Product Overview & Vision

**Vision:** Give users a beautiful, responsive, *local-only* music player that feels and behaves like a premium streaming app — dark Spotify-like UI, rich playback controls, sound-enhancement effects — without any network, accounts, or ads.

**Core promise:**
- Play everything you already own on-device, instantly.
- Sound the way *you* want (EQ presets, widen, 8D, reverb, bass, loudness).
- Never lose your place: queues, position, favorites, and playlists survive restarts.

**Positioning:** Offline-first. Competes on polish + audio-effects capability more than on discovery/streaming.

---

## 3. Goals & Success Metrics

| Goal | Measure |
|---|---|
| Fast cold start (large libraries) | Library shown from cache in < 1s |
| Zero crashes | 0 FATAL / 0 ANR across sessions (verified) |
| Smooth UI | 0 Choreographer frame-skips observed |
| State survival | Queue, position, repeat, favorites, playlists persist across kills |
| Predictable resume | Reopen **never auto-plays**; only explicit user play starts audio |
| Effect quality | Custom DSP pipeline audibly delivers 8D/3D/reverb (device-verified) |
| Ship velocity | Iterative builds tested on-device before each release (v1.0→v1.5) |

---

## 4. Personas & Use Cases

- **Persona A — "Casual Commuter":** listens to a few albums offline; wants simple play/pause and it "just works". Values not hearing autoplay surprises when reopening the app.
- **Persona B — "The Audiophile Hobbyist":** wants 8D rotation, 3D widen, room reverb, EQ presets and auto-enhance recommendations per track.
- **Persona C — "Power Organizer":** uses playlists and favorites heavily; renames/deletes playlists; searches the library live.

**Key use cases:**
- UC-1 Play a song from the library → overlay → background playback via notification.
- UC-2 Swipe app from Recents → music keeps playing (foreground service); reopen → playback continues the *same* song (v1.5 behavior).
- UC-3 Fully close/kill app → reopen → position restored, **paused** (no auto-play).
- UC-4 Toggle 8D / 3D / Reverb / EQ live mid-track.
- UC-5 Auto-enhance: WaveBeat recommends an EQ preset for a track based on genre metadata/title keywords.
- UC-6 Sleep timer stops playback after N minutes.
- UC-7 Create/multi-add/rename/delete playlists; toggle favorites.

---

## 5. Scope

### In Scope (implemented)
- Local MediaStore audio playback (offline only).
- Full player + mini player + notification controls + Media3 session (hardware/media buttons).
- Library (songs, favorites, playlists, search) with songs-cache for fast cold start.
- Audio effects: Equalizer (strength + 7 presets), Bass Boost, Loudness, 8D rotation, 3D widen, Room Reverb, system Dolby/Music Center launch.
- Auto-next (toggle), auto-resume policy (now **inert by design**), sleep timer, lyrics panel, haptics, keep-screen-on, animated logo, splash, terms-acceptance gate.
- Persistence of queue/position/repeat/favorites/playlists/caches via SharedPreferences.

### Out of Scope (current)
- Network streaming, accounts, sync, cloud backups.
- Downloading/procuring music.
- Media transcoding/export; gapless audio.
- Multi-user / multiple devices.
- Analytics or crash-reporting SDKs (deliberately none).
- A released (signed) build — current releases ship the debug APK for personal/testing installs.

---

## 6. Functional Requirements

### FR-1 Playback
- FR-1.1 Play/pause, next/prev, seek (tap + swipe on a custom seek bar), shuffle, repeat (Off/One/All).
- FR-1.2 Background playback through a Media3 `MediaSessionService` foreground service with a persistent media notification.
- FR-1.3 Auto-next to next track on completion (toggle `auto_next`, default true).
- FR-1.4 Media-button/hardware-key support via `MediaButtonReceiver` + session.
- FR-1.5 Audio-focus handling: pause on interruptions (noisy/BT/handset), resume policy gated by user.

### FR-2 Restart & Resume Policy
- FR-2.1 Queue (JSON in prefs), current **index**, and **position** are persisted.
- FR-2.2 On cold reopen: restore queue + position but **always start PAUSED** — never auto-play (enforced in `restorePlaybackState()`; the legacy `auto_resume` flag is inert).
- FR-2.3 On reopen while the service is alive (e.g., after Recents swipe): **do not re-seed the playing queue** to the first song; keep the current item & position (`loadFullPlaylist()` guard, fixed in v1.5).

### FR-3 Library
- FR-3.1 Read MediaStore audio (READ_EXTERNAL_STORAGE ≤23 API 32 / READ_MEDIA_AUDIO 33+).
- FR-3.2 Cache song list (`songs_cache`) for near-instant cold start; rescan diffs (id/title/artist/duration).
- FR-3.3 Live search filtering on the songs list.
- FR-3.4 Favorites: toggle from overlay/library; persisted as CSV in prefs; render on restart.
- FR-3.5 Playlists: create, rename, delete; add songs via multi-select dialog; persisted as JSON map.

### FR-4 Audio Effects
- FR-4.1 Equalizer intensity slider + 7 presets (Flat, Pop, Rock, Jazz, Bass Boost, Treble, Vocal).
- FR-4.2 Bass Boost and Loudness toggles.
- FR-4.3 **8D Rotation**: true stereo rotation at 8 s/cycle equal-power (custom `AudioProcessor`).
- FR-4.4 **3D Widening**: Mid/Side widening, strength-proportional (custom `AudioProcessor`).
- FR-4.5 **Room Reverb**: algorithmic Schroeder/Freeverb-style comb+allpass reverb (custom `AudioProcessor`).
- FR-4.6 Effects toggle live at runtime per session; persisted per-toggle state.
- FR-4.7 **Auto-Enhance**: on track change, recommend an EQ preset from genre/title keywords via a one-dismiss suggestion dialog.
- FR-4.8 Launch device system audio effect (Dolby / Music Center) through a dedicated button.

### FR-5 UI & Tweaks
- FR-5.1 Bottom navigation: Home, Library, Audio, Settings.
- FR-5.2 Home: animated "dance" logo (toggle), recently played cards, quick actions.
- FR-5.3 Mini player (title/artist/progress) → taps open the full overlay.
- FR-5.4 Full overlay: artwork, controls, seek, favorite, queue entry, sleep timer, lyrics panel.
- FR-5.5 Settings: auto-next, haptics (track), nav-bar haptics, keep-screen-on, dance logo, splash, resume (legacy/inert).
- FR-5.6 Splash: animated bars + first-run terms-acceptance gate (`wavebeat_setup`).

### FR-6 Notification & System Integration
- FR-6.1 Foreground media notification with play/pause/next/prev controls.
- FR-6.2 Session exposed to other media UIs (wear, lockscreen, assistants) via Media3 session.

---

## 7. Non-Functional Requirements

- **NFR-1 Compatibility:** minSdk 26 (Android 8.0+), targetSdk 34, compileSdk 34.
- **NFR-2 Performance:** cached library for fast start; seek bar updates lightweight; 0 frame-skips observed.
- **NFR-3 Stability:** 0 FATAL/ANR/RuntimeExceptions across audited sessions; robust against process death (position restored).
- **NFR-4 Battery:** playback uses foreground service + standard `DefaultLoadControl` buffering (20s/60s/1.5s/2s); sleep timer available.
- **NFR-5 Privacy:** fully offline; no analytics, no network permissions in manifest.
- **NFR-6 Accessibility/Security:** supports RTL; backup enabled; per-Android-version storage permission model.

---

## 8. User Flows

**Primary flow — play a song:**
1. Open app (Splash → Home).
2. Library tab → Songs list (or Playlist/Favorites) → tap track.
3. Mini player appears; tapping opens full overlay; controls work.
4. Leaving the app: notification controls / lock-screen media controls continue playback.

**Recents-swipe flow (the "surprise song" saga):**
1. Play any song **S** (e.g., item index 5).
2. Swipe app from Recents → activity destroyed, media service keeps playing **S**.
3. Reopen → **v1.4 bug**: queue was re-seeded at index 0 → "Alan Walker" played. **v1.5 fix**: keeps **S** playing. 

**Kill-flow:**
1. Play, kill the process (system eviction).
2. Reopen → queue restored at saved index + position, **paused**.

**Effect tuning flow:** Audio tab → toggle 8D/3D/Reverb, slide EQ strength, pick preset → auto-enhance dialog may suggest a preset → accept/dismiss.

---

## 9. Information Architecture & Screens

- **SplashActivity** — animated logo/bars; terms gate (one-time).
- **MainActivity** (main shell) — 4 tabs:
  - **Home** — dance logo, media cards / recently played grid, quick entry into library.
  - **Library** — sub-tabs: Songs | Playlists | Favorites; search field; FAB-less actions; playlist detail screen via `SongListActivity`.
  - **Audio** — EQ strength seek bar, preset chips grid (7), 8D / 3D / Reverb / Bass / Loudness toggles, "Open Dolby Music" button, auto-enhance toggle.
  - **Settings** — tweaks switch list (auto-next, haptics, nav haptics, keep-screen-on, dance logo, splash), about.
- **SongListActivity** — generic list screen reused for playlists (multi-select add/remove, rename, delete).
- **Mini player** — fixed bar above bottom nav.
- **Full player overlay** — in MainActivity overlay view: artwork, title/artist, seek bar, transport cluster (shuffle, prev/widenPlay, play/pause, next, repeat), like, queue, sleep timer, lyrics panel.

---

## 10. Functional Specification by Module

### 10.1 Splash & Onboarding
- First launch shows terms acceptance (stored in `wavebeat_setup`). Non-blocking after acceptance.
- Animated accent bars; configurable (splash toggle in Settings).

### 10.2 Home
- Animated equalizer/logo "dance" synced to playback (`dance_logo`).
- Card grid driven by current song/recent history; taps route to player/library.
- Mini player reflects current title/artist/progress and opens overlay.

### 10.3 Library
- **Songs:** MediaStore query; cached to `songs_cache`; diff-rescan keeps `currentSongIndex` stable when possible.
- **Search:** filter-as-you-type on title/artist.
- **Favorites:** persisted IDs (CSV) in `wavebeat_state` key `favorites`; rendered as a sub-tab row; toggle synced from overlay.
- **Playlists:** key `playlists` JSON map (name → id[]); create/rename/delete/add-songs via `SongListActivity` multi-select; survive restart.

### 10.4 Player Overlay & Mini Player
- Overlay: seek bar supports tap-to-seek and swipe; transport cluster (shuffle, prev, play/pause, next, repeat 3-state cycle); like; queue; sleep timer; lyrics.
- Repeat/shuffle persist across restarts (repeat applied via `applyRepeatMode()` on load; shuffle state in saved instance state).
- Sleep timer: dialog minutes; stops playback at countdown (per-session, not persisted).

### 10.5 Audio Effects Engine
- EQ: 5-band (custom band gains per preset, milliBel values) + master strength.
- 8D rotation: custom DSP, 8 s/cycle equal-power pan (L↔C↔R), float+short paths.
- 3D widen: Mid/Side; width = `1 + (strength/1000f)*1.4` (≈2.05 @ 750).
- Reverb: 4 comb + 2 allpass filters (Freeverb-style tunings), wet mix ~0.45, mono/stereo rooms, sample-rate-scaled delay lines.
- DSP chain order: **Widen → Reverb → Rotation** (custom `DefaultAudioSink` `AudioProcessor` stack).
- All processors `@OptIn(UnstableApi)`; share the classic Media3 `AudioProcessor` API (`configure`, `queueInput`, `getOutput`, `flush`, `reset`).

### 10.6 Settings & Tweaks
| Key | Default | Effect |
|---|---|---|
| `auto_next` | true | Auto-advance at track end |
| `auto_resume` | false | **Legacy/inert** — no auto-play on reopen |
| `haptics` | true | Track-tap vibration |
| `navbar_haptics` | true | Tab-switch vibration |
| `keep_screen_on` | true | Screen stays awake while app open |
| `dance_logo` | true | Animated home logo |
| `auto_enhance` | true | Suggest EQ preset per track |

*(All prefs live in `wavebeat_state`, except the terms gate in `wavebeat_setup`.)*

---

## 11. Architecture Overview

```
   Launcher
     |
     v
   SplashActivity ->(terms gate)-> MainActivity --> SongListActivity
     |                              |            (playlist detail,
     |  bind/start service          |             multi-select add)
     |------------------------------|
     v                              v
   MediaController <--> Media3 Session (MediaSessionService)
     |
     v
   MusicService = MediaSessionService + MediaSession
     |  player       = ExoPlayer (Builder with custom RenderersFactory)
     |  audioSink    = DefaultAudioSink + [Widen][Reverb][Rotation]
     |  extras       = LoadControl / AudioAttributes / noisy handling
     v
   Media3/DSP audio pipeline (PCM16 / PCM_FLOAT)
   input --> WidenAudioProcessor --> ReverbAudioProcessor --> RotationAudioProcessor --> AudioTrack
```

**Key components**
- **UI layer:** 3 activities (Main shell, Splash, Song list); custom views/drawables (EQ seek, mini player, animated logo).
- **Service layer:** `MusicService` — owns ExoPlayer, MediaSession, effects state, notification, and persistence.
- **Audio layer:** custom `AudioProcessor`s (Widen/Reverb/Rotation) inserted in the audio sink via a private `RenderersFactory`.
- **Data layer:** SharedPreferences (`wavebeat_state`) + MediaStore; JSON for queues/playlists/caches.

**Runtime model**
- `MainActivity` binds the service; a `MediaController` relays UI commands.
- Playback/effects **live in the service**, so they survive activity recreation (Recents swipe).
- Service `onTaskRemoved` intentionally keeps foreground playback going (media-app convention).

---

## 12. Audio Engine & DSP Pipeline

- **Snapshot of effects state** is held in MusicService companion + service fields (`is8D`, `virtStrength`, `reverbEnabled`, EQ preset/strength, bass/loudness).
- Platform effects (`AudioEffect` — Equalizer, Virtualizer, BassBoost, EnvironmentalReverb) were the original approach; **most proved ineffective on this hardware** (esp. `EnvironmentalReverb` and old-style Virtualizer), so the app migrated to **custom DSP processors** for 8D / 3D / Reverb.
- Toggle updates either run through `applyEffects()` (platform EQ/bass) or directly set processor booleans (`set8D`, `setVirtualizerStrength`, `setReverb`).

---

## 13. Data Layer & State Management

**Storage:** SharedPreferences (file `wavebeat_state` unless noted).

| Key | Type | Purpose |
|---|---|---|
| `playlist` | JSON string | Full queue: {u, t, a} per item — saved with `index`/`position` |
| `index` | int | Current item index (for restore) |
| `position` | long | Playback position ms (for restore) |
| `playing` | boolean | Saved playWhenReady — no longer *drives* autoplay (v1.4+) |
| `favorites` | CSV string | Favorite song IDs |
| `playlists` | JSON map | name → [ids] |
| `songs_cache` | JSON string | Rapid cold-start song list |
| `auto_next`,`auto_resume`,`haptics`,`navbar_haptics`,`keep_screen_on`,`dance_logo`,`auto_enhance` | bool | Toggles |
| (`wavebeat_setup` file) `terms_accepted` | bool | First-run gate |

**Persistence philosophy:** everything is local & synchronous (`apply()`); no DB required at this scale. Caches are self-healing (a corrupted volatile cache regenerates on relaunch).

---

## 14. Project File Structure

```
wavebeat/
|-- .gitignore              # ignores build/, .gradle/, *.apk, *.log, *.png, *.mp4
|-- build.gradle            # root gradle (app plugin + kotlin)
|-- settings.gradle
|-- gradle.properties
|-- gradlew / gradlew.bat   # wrapper
|-- README.md               # repo readme + screenshots
|-- FEATURES.md             # terse feature list
|-- LICENSE                 # MIT (c) 2026
|-- docs/
|   |-- screenshots/        # 18 preview images (home, library, player, audio, settings)
|   `-- PRD-WaveBeat.md     # this document
`-- app/
    |-- build.gradle        # module config (see section 15)
    `-- src/main/
        |-- AndroidManifest.xml
        |-- java/com/wavebeat/
        |   |-- MainActivity.kt            # shell, tabs, library, player overlay, settings
        |   |-- MusicService.kt            # media session service, playback, effects, persistence
        |   |-- ReverbAudioProcessor.kt    # algorithmic room reverb DSP
        |   |-- RotationAudioProcessor.kt  # 8D stereo rotation DSP
        |   |-- WidenAudioProcessor.kt     # mid/side 3D widen DSP
        |   |-- SongListActivity.kt        # playlist/song list + multi-select
        |   `-- SplashActivity.kt          # splash + terms gate
        `-- res/ (animator|color|drawable|layout|menu|values)  # 60+ custom resources
```

**Scale:** `MainActivity.kt` ≈ 2,354 lines, `MusicService.kt` ≈ 815 lines — biggest maintainability lever for refactoring (see §19).

---

## 15. Tech Stack & Build Configuration

- **Language:** Kotlin (jvmTarget 1.8).
- **Build:** Android Gradle Plugin + Kotlin Android plugin.
- **SDK:** compileSdk 34 · targetSdk 34 · minSdk 26.
- **Dependencies:** core-ktx 1.12.0 · appcompat 1.6.1 · material 1.11.0 · media3-exoplayer **1.2.0** · media3-session 1.2.0 · androidx.media 1.7.0 · guava 32.1.3-android.
- **Build command:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- **Lint:** `./gradlew lintDebug` — maintained at 0 errors.
- **APK size:** ≈ **8.5 MB** (debug); project ≈ 169 MB on disk.
- **Signing:** debug key (releases are personal/test installs only).

---

## 16. Development Timeline & Session History

> Consolidated from the full build/test sessions.

| Phase | What happened | Result / verdict |
|---|---|---|
| Foundations | Project scaffold, Media3 session service, Splash/Main/List activities, dark UI, mini + full player, EQ, playlists, favorites, search, lyrics, sleep timer, haptics, tweaks | Working app; FEATURES/README written |
| Health check | Full functional audit of favorites, playlists, audio, settings; toggle persistence; cold-restart state; force-stop→Home landing; Choreographer/ANR/FATAL scan | All green; flaky UI-dump test only |
| Data repair | A cleanup script over-deleted both test playlists; prefs repaired via runtime pull-edit-push (run-as) while app stopped; corrupted quotes in volatile caches self-healed on relaunch | State fully restored; lesson: destructive cleanup must be scoped |
| 8D (v1.1) | "8D didn't feel like rotation" → replaced static EQ scoop with `RotationAudioProcessor` (8 s/cycle equal-power) wired through custom `RenderersFactory` | Real rotation, verified PLAYING on device |
| 3D (v1.2) | "3D not like the sound from widen" → `WidenAudioProcessor` M/S widening; strength-driven width | Real widen, verified on device |
| Reverb (v1.3) | "Reverb not like room ambience" → platform `EnvironmentalReverb` replaced with algorithmic `ReverbAudioProcessor`; platform effect disabled to avoid doubling | Audible room ambience, verified PLAYING |
| Autoplay #1 (v1.4) | "Alan Walker plays when I reopen" — restore path auto-played when `playing=true` + `auto_resume` → enforce **always pause** on restore | Fixed kill→reopen autoplay |
| Autoplay #2 (v1.5) | Same complaint after Recents-swipe — **fresh activity re-seeded the live queue to index 0** (`loadFullPlaylist`) while service still playing → guard: never re-seed an existing queue; sync UI from controller | Fixed reopen-jump to first song; verified item preserved across swipe-away |

**Validation loop used throughout:** build → `adb install -r` → launch/play via keyevents/UI taps → assert via `dumpsys media_session` (state/position/item) → logcat sweep (FATAL/ANR/Choreographer/audio errors) → user listen test → release.

---

## 17. Bugs Discovered, Root Causes & Fixes

| # | Symptom | Root cause | Fix (version) |
|---|---|---|---|
| B1 | Reopen auto-plays last song | `restorePlaybackState()` called `play()` when `playing && auto_resume` | Always restore **paused** (v1.4) |
| B2 | After Recents-swipe + reopen, playback jumps to first song ("Alan Walker") | Fresh `MainActivity` reseeded live queue at `currentSongIndex=0` via `setMediaItems(...) + prepare()` while service kept playing | Skip re-seeding when a queue exists; sync UI from controller (v1.5) |
| B3 | 8D effect not rotational | Static L/R EQ scoop, not a true rotator | Custom `RotationAudioProcessor` 8 s/cycle (v1.1) |
| B4 | 3D effect not widening | Platform Virtualizer barely effective on device | M/S `WidenAudioProcessor` (v1.2) |
| B5 | Reverb not ambient | Platform `EnvironmentalReverb` no-op on most hardware | Algorithmic reverb processor (v1.3) |
| B6 | Lost both test playlists | Over-broad cleanup script executed while app running held stale data, then wrote empty playlist map | Scoped repair + prefs surgery via run-as; `P2` retired |
| B7 | Flaky UI automation | uiautomator dumps intermittently incomplete; coordinate math bug produced 4.4M offsets | Retry dump + verify bounds in 1080 range |
| B8 | Device BT sink errors | Device's stale Bluetooth route (`Bluetooth audio disconnected`) | Environmental; app code clean; test with BT enabled |

---

## 18. Testing & Quality Assurance

**Manual/device methodology (all through ADB on the reference device):**
- Functional: launch, play, pause, next/prev, seek, repeat/shuffle, sleep timer, favorites, playlists, search, effects toggles.
- Lifecycle: Recents-swipe, force-stop, cold relaunch, background-kill, notification controls.
- State: prefs introspection via `run-as cat`; media session assertion via `dumpsys media_session`.
- Robustness: logcat sweeps — no FATAL EXCEPTION, no ANR, no RuntimeExceptions, no Choreographer skips, no AudioSink/processor errors.

**Latest verified state (v1.5):**
- Swipe-away → reopen: **same song continues** (item preserved).
- Kill → reopen: restores position, **paused**.
- Effects toggled live with zero pipeline errors.

**Remaining test debt:** no automated unit/UI tests; regression relies on the ADB acceptance script + device listening.

---

## 19. Design Decisions — Pros & Cons

### D1 — Custom DSP processors over platform AudioEffect sync
- **Pros:** works uniformly across devices; true 8D/3D/reverb; testable independently; no `audio_effect` HAL dependency.
- **Cons:** re-implements DSP (risk of quality issues vs. tuned effects); extra CPU per sample; `@OptIn(UnstableApi)` surface; more code to maintain.

### D2 — No auto-play on reopen (policy chosen by user)
- **Pros:** predictable; kills the "surprise song" complaint; respects user intent.
- **Cons:** the `auto_resume` toggle is now inert (UI vestige); power users lose "resume" convenience; needs cleanup or re-purposing.

### D3 — Foreground media service keeps playing after Recents-swipe
- **Pros:** standard media-app behavior; music uninterrupted.
- **Cons:** can surprise users who expect silence; requires explicit pause from notification.

### D4 — SharedPreferences + JSON for state
- **Pros:** zero deps; simple; synchronous; adequate at this scale.
- **Cons:** not transactional; large `playlist`/`songs_cache` strings; concurrency with `apply()` if processes race — mitigated by in-process design.

### D5 — Single large `MainActivity.kt` (+2.3k lines)
- **Pros:** fast iteration during build sessions.
- **Cons:** readability/testing/maintenance burden — **top refactor candidate** (§23).

### D6 — Debug APK-only releases
- **Pros:** simple, no keystore management for personal installs.
- **Cons:** not Play-ready; users must enable unknown sources; no Play integrity.

### D7 — No analytics/crash SDKs
- **Pros:** privacy-first, no network permission, lean APK.
- **Cons:** no remote visibility into crashes/usage — relies on ADB audits.

---

## 20. Known Limitations & Risks

- **Reference-device dependence:** audio-effect quality can't be fully second-guessed remotely — user confirmation required per effect.
- **Device BT audio quirk:** stale Bluetooth routes can surface `Bluetooth audio disconnected` errors during rapid install/kill cycles (environmental, not app logic).
- **Inert UI toggle:** "Resume playback" switch does nothing today (auto-play disabled); confusing unless removed/re-purposed.
- **Version skew:** APK naming (v1.x) vs `versionCode 1`/`versionName 1.0` — releases fine for personal use, but Play upload would need version hygiene.
- **No automated tests:** regression coverage is procedural (ADB scripts + manual listens).
- **Songs cache staleness:** if MediaStore changes and the rescan diff is interrupted, cold start may briefly show stale titles until next successful scan.
- **Large-file monolith:** risk of merge conflicts/regressions as features accumulate.

---

## 21. Security & Privacy

- **No network permissions** in the manifest — playback is 100% local.
- Storage permissions are scoped per Android version (`READ_EXTERNAL_STORAGE` ≤32, `READ_MEDIA_AUDIO` 33+).
- Sensitive data: none transmitted; favorites/playlists/queues stored on-device only.
- `allowBackup=true`: device backups could carry user prefs (consider `false` if desired — see §23).

---

## 22. Release Management

- **Repository:** https://github.com/rmounikkumar/wavebeat (public, `main`).
- **Releases:** v1.0 → v1.5, each with wavebeat-vX.Y.apk attached; announced generically ("Update with audio improvements / stability and autoplay fix") per user preference.
- **Workflow:** local build → on-device verify → user approval → commit → push → `gh release create`.
- **Branching:** single `main`; releases tagged from commits.

---

## 23. Roadmap (Next Steps)

**Short term**
1. Remove or re-purpose the inert `auto_resume` switch (UI honesty).
2. Optionally add a "Resume" intent action as the *explicit* replacement.
3. Split `MainActivity` (e.g., fragments: Home/Library/Audio/Settings + overlay) and extract `Prefs` accessor.
4. Add a thin automated smoke test (robolectric/espresso) for the restore-pause + no-reseed rules — the two regressions that historically slipped.

**Medium term**
5. Release signing + proper versionName hygiene; rename-scheme documentation.
6. `allowBackup=false` or encrypted backup of private prefs.
7. Visualizer/audio-reactive home logo options; artwork loading from MediaStore album-art URIs.
8. Gapless playback & buffering preference surfaces.

**Long term (out of current scope)**
9. Streaming/account features (contradicts offline-first positioning — treat as a v2 product).
10. Playlist import/export; statistics (top tracks).

---

## 24. Glossary

- **Media3 / ExoPlayer** — Android media library used for playback & sessions.
- **MediaSessionService / MediaSession** — Media3 service exposing transport to the notification, lock screen, and media buttons.
- **AudioProcessor** — Media3 DSP hook inside the audio sink (ByteBuffer PCM16/PCM_FLOAT).
- **DSP** — Digital Signal Processing.
- **M/S (Mid/Side)** — stereo width technique used by the widen processor.
- **Schroeder/Freeverb** — classic algorithmic reverb topology (comb + allpass filters) used by the reverb processor.
- **8D rotation** — binaural-style stereo pan around the listener over a period.
- **Recents swipe / task removal** — Android gesture that destroys the activity but (for media apps) may leave the foreground service alive.

---

*End of PRD — living document: update section 16/17/23 as the product evolves.*