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
# Run Android session/persistence tests on a selected emulator or test watch:
ANDROID_SERIAL=emulator-5554 ./gradlew :apps:wear:connectedDebugAndroidTest
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
opens two buttons for **My team** or **Opponents** to serve first. Tapping either starts
the match immediately and replaces the saved match; there is no separate confirmation.
Use the watch's Back action to leave setup. Back from the court, or
the Menu target at its top, returns to the menu without losing progress.

By default, selecting either serving team also launches Samsung Health's
`com.samsung.android.wear.shealth.intent.action.START_WORKOUT` action, scoped to the
`com.samsung.android.wear.shealth` package, with the string extra `exercise.type=TENNIS`.
That parameter matches the installed Samsung Health handler's exercise enum contract.
This is an undocumented, version-dependent integration.
The launch requests Samsung Health's own task with `FLAG_ACTIVITY_NEW_TASK`, keeping
Set Point separate in Recent apps so you can switch back to the saved court while the
workout runs. Back navigation within the workout is controlled by Samsung Health.
The match is saved before launching Samsung Health. Return to Set Point to score; Continue,
undo, and reopening the app do not launch the action again. If the action is unavailable
or denied, a message appears and the match remains playable.

**Settings → Launch workout** controls this launch and is **ON** by default. Turn it off
to start new games directly on the court without opening Samsung Health. The preference
saves automatically and is retained after restarting the app.

Settings save automatically after each change; use the watch's Back action to return to
the menu. They apply to new matches: choose 1–12 games to win a set, advantage or golden point
(the next point at 40–40 wins), and whether to play a tie-break at the configured games
target. Sets require a two-game lead; tie-breaks require seven points with a two-point
lead. Choose 1–12 sets to win a match (two by default), or enable **Endless mode** to keep
playing sets without a match winner. Resumed matches retain their original rules.

When a team reaches the sets-to-win target, a result screen names the winning team and
shows the final set score with the winner's score first. **Undo last point** reopens the
match before the winning point; **Start new game** opens serving-team selection. Set
results remain available from this screen, including after restarting the app.

The court has four touch areas, with opponents above and my team below. Point scores
sit over each team's half, and the middle badge shows games in **opponents : my team**
order. The court shows scores and the service ball without team labels or gesture hints.
Tap either quarter of a team's half to highlight that team and its score; tap the
same team again to confirm the point. Tapping the other team changes the selection.
Selections expire after 3.5 seconds and clear when the app leaves the foreground.

Rotate the bezel clockwise to select my team, or counterclockwise to select opponents.
Continue in that direction to fill the confirmation ring and award one point. Reversing
direction selects the other team and resets progress. A short cooldown after confirmation
guards against extra points from continued rotation. Menus use Wear OS's animated rotary
scrolling, which adapts to coarse bezel detents (such as Galaxy Watch Classic) and finer
crown input, with momentum and scrolling haptics.

The ball marks the current service corner, alternating sides after every point. Service
alternates between teams, including the one-point, then two-point rotation in a
tie-break. The ball's accessibility description identifies the serving team. Long-press
any court quarter to undo the last point, including set and match wins. Hold the middle
badge to open cards with previous sets' results; a normal tap also opens them.

The match UUID, rules, first server, and point log are saved after every confirmed action;
undo works after restarting the app. Existing version 1 matches retain the original
six-game, advantage, tie-break rules; version 1 and 2 matches retain the two-set match
target. Only the latest match is retained.

Starting or continuing a match also starts an ongoing match session. Allow the notification
permission when prompted: its quiet ongoing indicator keeps the match available after
sleep and provides a shortcut back to the court from the watch face. If permission was
denied, use **Settings → Allow match notifications** to enable it in the watch's settings.

In ambient mode the app shows just the clock, point scores, games, and sets on black,
without scoring controls or animations. Pending point selections clear when entering
ambient mode; wake the watch before scoring again. The display still follows the watch's
Always On Display setting. Home continues to work, and the match indicator can reopen
the court directly. An active session also restores straight to the court after the app
process is recreated.

Returning to the menu from the court pauses the ongoing session and removes its
indicator while keeping the match saved. Continue resumes it. A match win ends the
session automatically; undoing the winning point reactivates it. In endless mode, use
the court's Menu target or Back to pause the session when finished playing.

`dev.setpoint.wear` is a provisional application ID; choose your own namespace before
publishing. Localization, workout recording, and a match archive remain future work.
Layout and touch flows have been checked on a round Wear OS emulator;
physical bezel feel and haptics still need validation on a watch.
