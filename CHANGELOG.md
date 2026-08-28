# Changelog

All notable changes to PromptFlow. Versions follow the app's `versionName`;
Play Store `versionCode` is assigned automatically at release time by Fastlane.

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
