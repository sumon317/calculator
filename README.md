# Calu

A minimal Android calculator: one Activity, lightweight, and offline.
No prefs, no files, no database, no cache writes - all state is RAM-only.

## Project Structure

    app/src/main/java/com/calu/CalcActivity.java   app + expression parser (BODMAS)
    app/src/main/res/                              app resources and icons
    app/src/main/AndroidManifest.xml               manifest (minSdk 21, targetSdk 34)
    app/src/test/java/com/calu/EvalTest.java       offline parser/formatting tests

## Build

Build the debug APK using Gradle:

```bash
./gradlew assembleDebug
```

Output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Test

Run unit tests via Gradle:

```bash
./gradlew test
```

## Keys

Collapsed: `C ( ) /`, `7 8 9 *`, `4 5 6 -`, `1 2 3 +`, `0 . backspace =`

EXPAND adds a scientific row: `% ^ SQRT 00`

- `%` is postfix (`50%` = 0.5); `^` is right-associative (`2^3^2` = 512).
- `SQRT` is a prefix (`SQRT16` = 4).
- `(` and `SQRT` insert a missing `*` after a digit, `)` or `%`.
- The expression survives the EXPAND/COLLAPSE toggle and rotation.

## License

Calu - a minimal offline calculator for Android
Copyright (C) 2026 sumon317

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License version 3 as
published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

Full license text: [LICENSE](LICENSE) (SPDX: GPL-3.0-only)
