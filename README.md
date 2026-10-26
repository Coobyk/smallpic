<img src="assets/icon.svg" width="64" height="64" alt="">

# SmallPic

A tiny Android image optimizer. Pick, shrink, done:

- One minimal screen — tap **Choose images** and select one or many photos with
  the system photo picker (no permissions, no fuss).
- Each image is compressed to roughly **10% of its original file size**
  (downscaling in steps + JPEG quality search to keep resolution as high as
  possible), with a live progress bar and per-image savings
  (e.g. `4.8 MB → 470 KB (90% smaller)`).
- Then either **Save to gallery** (lands in `Pictures/SmallPic` as
  `smallpic_<timestamp>.jpg`) or **Share** the shrunken files straight to any
  app via the share sheet.
- Transparent images are flattened onto white (JPEG output).
- Images that are already tiny are skipped and left alone.

Requires Android 10 (API 29)+.

## Build

Open the project in Android Studio, or from the command line:

```sh
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Install on a connected
device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

JDK 21 is pinned for this project via `mise.toml` (Gradle 8.11 doesn't support
newer JDKs).

Created in [T3 Code](https://t3.codes).
