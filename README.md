# Set Point

A standalone Wear OS padel score tracker, with room for a future phone companion.

## Modules

- `apps/wear`: Android application, Compose for Wear OS Material 3 UI and ViewModels.
- `core/scoring`: Pure Kotlin match state and scoring rules; no Android dependencies.
- `core/data`: Android persistence and repository; depends on scoring.

The Wear app depends on both core modules. Core modules never depend on applications.
Add `apps/phone` and `core/sync` when needed; there are no empty placeholder modules.
See [architecture notes](docs/architecture.md).

## Development

Install Android Studio, a JDK supported by Gradle 8.14.3 (17–24), Android SDK Platform 35,
and SDK Build Tools 35.0.0. Android Studio can install the SDK packages for you.
Set `ANDROID_HOME` to your SDK location or create an ignored `local.properties`:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

Open this directory in Android Studio and select the `apps.wear` run configuration
(or the `apps/wear` application module). From the command line:

```sh
./gradlew :core:scoring:test
./gradlew :apps:wear:assembleDebug
./gradlew :apps:wear:installDebug
```

APK: `apps/wear/build/outputs/apk/debug/wear-debug.apk`.

For a physical Galaxy Watch: Settings → About watch → Software → tap Software version
five times. Enable ADB debugging and Wireless debugging in Developer options. Put the
computer and watch on the same Wi-Fi, select Pair new device, and run:

```sh
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:CONNECTION_PORT
adb devices
```

The pairing port and connection port are usually different. No phone companion is needed.

## Starter behavior and scope

Tap a team to award a point; the dot marks the serving team. Undo restores the previous
point, including across app restarts. Matches use advantage games, a seven-point tie-break
at 6–6 (win by two), and best of three sets. The active match has a stable UUID and is saved
after every action. `dev.setpoint.wear` is a provisional application ID; choose your own
namespace before publishing.

This is a project foundation: new-match flow, match history, configurable rules, player
serving order, bezel controls, haptics, localization, ambient behavior, and workout recording
are still to implement. The UI has not yet been validated on a physical watch.
