# WaveBeat — Feature List

## Playback
- Full player overlay: shuffle, prev/next, play/pause, repeat (off/one/all), seek bar (tap + swipe)
- Mini player bar (title/artist, progress, open overlay)
- Auto-next on track end (toggleable)
- Auto-resume after process death / interruption (toggleable)
- Repeat/order + shuffle persist across restarts
- Sleep timer (dialog)
- Favorite toggle from the overlay (persists)

## Library
- Songs list (full library, cached for fast cold start)
- Playlists: create, rename, delete, add songs from a multi-select dialog
- Favorites (persisting) — Playlists + Favorites rows now render correctly after restart
- Search filters the songs list live

## Audio
- Equalizer intensity seek bar (persisting)
- Audio presets (grid) + auto-enhance
- Bass boost, reverb, loudness toggles
- Launch into system Dolby / Music Center via dedicated button
- Playback respects audio-focus (auto pause on interruptions)

## UI / Tweaks
- Bottom nav: Home, Library, Audio, Settings
- Home tab with animated dance logo
- Track haptics toggle (vibrate on tap)
- Nav-bar haptics toggle (vibrate on tab switch)
- Keep-screen-on toggle; dance-logo toggle; splash screen
- Lyrics panel per-track
- Lightweight toolbar search

## State
- Prefs backed in `wavebeat_state` (queues, position, repeat, favorites, playlists, caches, toggles)
- Player/media-session state verified to survive cold restarts (UI resets to Home by design)