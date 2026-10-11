# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [0.4.0]

### Added
- Melta Shade: Melta's own pull down shade, opened by swiping down on the top edge
  - companion card header with clock and date, in your card theme
  - quick toggles: Wi‑Fi, Internet, Bluetooth, flashlight, Do not disturb, auto rotate, sound,
    battery bar, screenshot, lock screen, system shade
  - brightness slider
  - notifications grouped by app with tap to open, swipe to dismiss and clear all
- Setup checklist with live status for each permission, and help for "restricted settings"
- Choice of swipe area: left half, right half or the whole top edge

## [0.3.0]

### Added
- Stage 2: Notification Shade screen
- Companion card themes: System, Pokédex, Game Boy, Neon and Night
- The companion card can now show in the shade while the status bar overlay is on too
- Quick Settings tiles: Companion (battery level, bar on and off) and Pokémon of the day
- Long press a Melta tile to open the Shade settings

## [0.2.0]

### Added
- Notification panel battery bar: your companion and battery level as a live card in the pull down panel, with charge time remaining. Works without the "Display over other apps" permission
- "Show it in" choice between the status bar and the notification panel
- Help for the greyed out "Display over other apps" toggle (Android restricted settings), with a shortcut to App info

### Changed
- Turning the bar on never gets stuck on the permission screen anymore. Without the overlay permission Melta falls back to the notification panel automatically

## [0.1.0]

### Added
- All 1025 Pokémon sprites (normal and shiny) bundled in the APK at build time, so the picker works offline
- Popular row in the picker with Pikachu first, plus a shiny preview toggle
- Crisp whole pixel scaling and a "True size" mode that keeps relative sizes
- Bar colors can match the companion automatically
- Stage 1: Battery Bar overlay with Pokémon companions (all 1025, shiny and animated options)
- Charging effects: sparks, thunder, bubbles, flames, leaves, hearts, snow, with type based auto pick
- Fill styles: solid, gradient, rainbow, stripes, dots, checker and custom patterns
- Pixel art editor with mirror, bucket fill, undo and photo import
- Live preview with simulated charging
- Melta app icon, adaptive and themed
- CI workflow that builds, tests and lints every push, and a tag based release workflow
