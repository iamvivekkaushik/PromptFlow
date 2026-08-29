# Changelog

All notable changes to PromptFlow. Versions follow the app's `versionName`;
Play Store `versionCode` is assigned automatically at release time by Fastlane.

## [1.2] — 2026-08-29

### Added
- **Takes** — every Studio recording is now saved against the script it was read from. Each
  script shows a strip of its takes with a thumbnail, duration, quality/fps and age; tap to play
  in the system player, or delete (which removes the video from the gallery too, behind a
  confirmation). Takes whose file was deleted elsewhere show as "file missing" rather than
  disappearing. Room schema v3 with an additive migration — existing scripts are untouched, and
  deleting a script now also clears its take records.
- **Script markers** — `## Section` headers, `[[pause]]` stop points, `[[pause 3s]]` timed holds,
  and `[[b-roll: …]]` / `[[note: …]]` stage directions. Markers are excluded from the spoken word
  stream, so word counts, duration estimates and voice-sync alignment stay accurate. Headers
  render as lime titles and directives as chips, so it is obvious what not to read aloud.
  The prompter stops at a bare `[[pause]]` until you resume, and counts down through a timed one.
- **Section jumps** — move to the previous section without losing playback state or your place.
- **Marker toolbar in the editor** — insert Section, Pause, Pause 3s or B-roll at the cursor.
- **Help & shortcuts screen** — marker syntax with examples, prompter gestures, hardware-remote
  keys, voice sync, Studio controls, overlay handling and import formats. Reachable from a HELP
  card in Settings and a `?` chip on the editor's marker toolbar.
- **Studio camera controls** — tap-to-focus with a reticle and an exposure slider beside it,
  pinch zoom plus a 1× / 2× / 4× preset chip, a torch toggle (rear lens only) and a
  rule-of-thirds grid.
- **Settings → Recording → Mirror front camera video**, on by default.
- Project README, and unit tests for the markup parser.

### Changed
- The prompter now maps each word to its **real laid-out pixel offset** instead of estimating
  from average text height, so voice sync, pause markers and section jumps land on the right line.
- Rewind is now tap for the previous section, long-press for the top of the script — in the
  Studio and the overlay. Page Up / D-pad Up on a remote does the same.
- Zoom, torch and exposure survive a camera rebind (quality, fps or aspect change) and reset
  when you flip between lenses.
- Release pipeline: manual workflow runs default to the internal track, test-track uploads roll
  out to testers immediately, and production uploads land as a draft for a human to release.

### Fixed
- **Front-camera recordings were saved flipped** relative to the viewfinder. CameraX records the
  front camera un-mirrored by default while the preview is mirrored; the recorder now uses
  `MIRROR_MODE_ON_FRONT_ONLY` when the new setting is on.
- **Pause markers never fired.** The pause pointer was resynced during `load()`, before layout —
  every word still mapped to pixel 0, so the pointer skipped past every pause immediately. It now
  holds until real text positions arrive.
- **Word counts and read-time estimates on the library and All Scripts rows** counted markup as
  speech; they now parse the script the same way the prompter does. (The editor's own counter was
  fixed alongside it.)
- The editor's marker toolbar overflowed on narrow screens — it now scrolls horizontally.
- Play Console *outdated SDK*: CameraX pulled in `appcompat` 1.1.0 and with it `fragment` 1.1.0.
  Both are now raised by dependency constraints (1.7.1 / 1.8.9) without adding direct dependencies.
- Play Console *deprecated edge-to-edge APIs*: `enableEdgeToEdge()` calls
  `Window.setStatusBarColor()` / `setNavigationBarColor()` internally, deprecated in Android 15.
  System bars are now configured from the theme, with `WindowCompat` and
  `WindowInsetsControllerCompat` handling insets and icon appearance.
- Fastlane's release status was inverted: internal and beta builds uploaded as drafts that Play
  never distributed, while a production run rolled out publicly with no human gate.
- A manual run of the release workflow defaulted to the production track.

## [1.1] — 2026-08-28

First Play Store submission. Everything below ships in that build.

### Added
- **All Scripts screen** — full library with search, All/Drafts/Recorded filters,
  per-script recorded state, and a delete action with confirmation.
- **Delete a script** from the All Scripts list (recordings in the gallery are untouched).
- **Design icon set** — 25 hand-drawn 24dp vector drawables replacing text glyphs
  across every screen, plus an adaptive launcher icon (with themed/monochrome layer)
  and a dedicated notification icon.
- **Licenses screen** — open-source attributions for all shipped dependencies,
  reachable from an About card in Settings that also shows the app version.
- **Start-delay countdown** (Off / 3s / 5s / 10s) before scrolling begins.
- **Live WPM readout in the overlay header**, with ± steppers.
- **Live mic level in dB** in the Studio, driven by the recorder's audio stats.
- **Manual recorded/draft toggle** per script, in addition to automatic marking
  when a Studio take saves.
- **Clear (✕) button** in both search fields.
- **Release pipeline** — Fastlane lanes for internal/beta/production plus a GitHub
  Actions workflow, Play listing metadata, store graphics, and a GitHub Pages
  privacy policy.

### Changed
- Prompter starts at the top of the script; only the library's Continue card resumes
  from a saved position.
- Scroll velocity recalibrated against the true content height so WPM matches the
  actual reading pace.
- Studio side rail moved clear of the prompter panel, and the camera-flip icon
  redrawn so it reads at 21dp.
- Settings: taller live preview; the non-functional hardware-remotes card removed.

### Fixed
- **Words cut off / text starting mid-script** — the prompter column was measured
  against the viewport height, and the oversized layout was being auto-centered by
  Compose. Text now measures unbounded and anchors to the top.
- **Volume-key speed control in the overlay** snapping back — the media-session
  volume provider wrote `currentVolume` inside its own callback, so the system
  re-issued adjustments in a loop. One key press is now exactly one ±10 step, and
  the value is persisted.
- **Periodic chime during voice sync** — the recognizer's start/stop tone is muted
  across all candidate audio streams for the duration of a session, and the
  on-device recognizer is preferred where available.
- Studio: recording now reports success/failure, stops cleanly on exit and camera
  flip, and the record button reflects live recording state.
- Dark status-bar icons made visible; Studio back button no longer sits under the
  system status bar.

## [1.0]
- Initial internal build: script library, editor, Studio recording, floating overlay,
  prompter engine, and typography settings.
