# Contributing to Melta

Thanks for helping out. A few simple rules keep things smooth.

## Getting started

1. Fork the repo and create a branch from `main`.
2. Open the project in Android Studio, or use the command line with JDK 17.
3. Run the checks before you push:

   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

## Guidelines

- **Keep the overlay light.** Code under `battery/service` and `battery/overlay` runs all day. Avoid
  allocations in `onDraw` and in the animation loop, and do not add libraries to that path.
- **No copyrighted art in the repo.** Pokémon sprites must stay downloaded at runtime. Original art is welcome.
- **Pure logic gets a unit test.** Math, parsing and editor operations live in plain Kotlin classes so
  they can be tested on the JVM (see `app/src/test`).
- Follow the existing Kotlin style (`.editorconfig`, official Kotlin code style).
- One topic per pull request, with a short description of what and why.

## Adding a new stage

New modules live in their own package (for example `com.melta.app.shade`) with a `ui/` folder for
Compose screens. Add a card in `home/HomeScreen.kt` and a screen in `MainActivity.kt`.

## Reporting bugs

Use the bug report template and include your device, Android version and steps to reproduce.

By contributing you agree that your contributions are licensed under the MIT license and that you
follow the [Code of Conduct](CODE_OF_CONDUCT.md).
