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
./gradlew :apps:wear:testDebugUnitTest :apps:wear:lintDebug
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

## Playing a match

The main menu offers **Start new game**, **Continue game** when a match is saved, and
**Settings**. A finished match can be opened with **View last game**. Starting a new game
lets you select the first server and replaces the saved match. Back from the court, or
the Menu target at its top, returns to the menu without losing progress.

Settings apply to new matches: choose 1–12 games to win a set, advantage or golden point
(the next point at 40–40 wins), and whether to play a tie-break at the configured games
target. Sets require a two-game lead; tie-breaks require seven points with a two-point
lead. Matches are best of three sets. Resumed matches retain their original rules.

The court has four touch areas, with opponents above and my team below. Point scores
sit over each team's half, and the middle badge shows games in **opponents : my team**
order. Tap either quarter of a team's half to highlight that team and its score; tap the
same team again to confirm the point. Tapping the other team changes the selection.
Selections expire after 3.5 seconds and clear when the app leaves the foreground.

Rotate the bezel clockwise to select my team, or counterclockwise to select opponents.
Continue in that direction to fill the confirmation ring and award one point. Reversing
direction selects the other team and resets progress. A short cooldown after confirmation
guards against extra points from continued rotation. The bezel also scrolls menus.

The ball marks the current service corner, alternating sides after every point. Service
rotates between all four players, including the one-point, then two-point rotation in a
tie-break. The ball's accessibility description identifies the player serving. Long-press
any court quarter to undo the last point, including set and match wins. Hold the middle
badge to open cards with previous sets' results; a normal tap also opens them.

The match UUID, rules, first server, and point log are saved after every confirmed action;
undo works after restarting the app. Existing version 1 matches retain the original
six-game, advantage, tie-break rules. Only the latest match is retained.

`dev.setpoint.wear` is a provisional application ID; choose your own namespace before
publishing. Localization, ambient behavior, workout recording, and a match archive remain
future work. Layout and touch flows have been checked on a round Wear OS emulator;
physical bezel feel and haptics still need validation on a watch.
