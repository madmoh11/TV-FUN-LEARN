# Building from source

You need JDK 17 and the Android SDK (platform 34). Android Studio installs both.

```bash
git clone https://github.com/madmoh11/TV-FUN-LEARN.git
cd TV-FUN-LEARN
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # runs the tests
```

If Gradle cannot find the SDK, create `local.properties` with one line: `sdk.dir=/path/to/Android/sdk`.

## Tests

- `SnakeCoreTest` checks the snake rules (movement, wrap-around, crashes, scoring).
- `RenderTest` draws the real TV screens off-device with Robolectric and writes PNG files to `app/build/renders/`.
  Add `-DrenderLang=ar` to render them in another language.

## Signing your own release

Android only updates an app when the new APK is signed with the same key as the installed one.

```bash
keytool -genkeypair -v -keystore my.jks -alias tvdraw -keyalg RSA -keysize 2048 -validity 10000
export TVDRAW_KEYSTORE=$PWD/my.jks TVDRAW_KEYSTORE_PASSWORD=... TVDRAW_KEY_ALIAS=tvdraw TVDRAW_KEY_PASSWORD=...
./gradlew assembleRelease
```

Keep the keystore file and its passwords private and backed up. Never commit them.

The GitHub workflow uses the same key when these repository secrets exist: `KEYSTORE_BASE64` (the output of
`base64 -w0 my.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. To publish a release with the APK attached, push a tag such as `v3.1`, or open
**Actions → Build → Run workflow** and type the tag there.

## How the code is organised

| File | What it does |
| --- | --- |
| `OverlayService.kt` | The full-screen overlay on the TV: comments, pranks, pictures, the address card |
| `Ink.kt` | Drawing engine shared by all phones, laser pointer and spotlight |
| `Game.kt`, `Hockey.kt`, `SnakeGame.kt`, `SnakeCore.kt` | The three games |
| `Server.kt`, `Tls.kt` | The small HTTP and HTTPS server the phones talk to |
| `Audio.kt`, `Synth.kt` | Sound mixer and synthesised effects |
| `Prayer.kt`, `PrayerAlerts.kt` | Prayer-time calculation and alerts |
| `assets/web/index.html` | The phone page |
| `assets/web/lang/*.json`, `res/values*/strings.xml` | Translations |
