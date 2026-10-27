# Architecture

## Current boundaries

`apps/wear` → `core/data` → `core/scoring`, with a direct Wear → scoring dependency.
Keep screens, input handling, haptics, and watch lifecycle in the Wear app. Keep scoring
rules independent of Android so another UI can reuse them.

The repository stores the current match UUID, immutable rules, first server, and ordered
point log in private SharedPreferences (schema version 4). Replaying the log reconstructs
state, all four players' service turns, and undo. Version 1 logs replay with the original
default rules and first server. Default settings are stored separately and only apply to
new matches. Version 3 rules add `setsToWinMatch`: an explicit JSON null means endless
mode; an absent value in version 1/2 data defaults to two. A fresh install has no current
match until the user starts one.

Settings controls save each change through the ViewModel immediately and stay on the
settings page. Their values come from the persisted settings state, with inputs gated
during a write. Back can return to the menu while a write finishes; saving does not depend
on the settings composable remaining on screen. Active matches retain their own rules.

Writes run off the UI thread and complete before publishing the new state. The ViewModel
gates actions while a write is pending; the repository updates its in-memory state only
after a successful commit and exposes failures to the UI. It caches the current state
for normal scoring and replays the log for undo.

The same preferences keep a bounded archive of the last 10 ended sessions, newest first.
Each entry contains the original rules, server and point log plus start/end epoch
milliseconds. Wins archive immediately; replacing an unfinished game archives it with
no winner and the replacement time as its end time. Pausing does not end a game. Undo
removes the current match from the archive, and rewinning records a new end time.
Archive and current-match changes share one commit, with entries deduplicated by match
UUID. Existing matches retain unknown timestamps as null. History displays dates/times
using the watch's locale and clock preference, and preserves partial scores for
interrupted games. Archived games are read-only and cannot replace the current game.

The Wear UI owns navigation, haptics, selection timeout, and bezel cooldown. The pure
`PointInput` reducer requires selection before confirmation, resets rotary progress when
direction changes, and shares a selection between touch and bezel input. Selection is
transient and cleared when leaving the court or pausing the app; only confirmed points
are persisted. Scoring and input transitions have local JVM regression tests.

Menu pages use Wear Compose Foundation's `rotaryScrollable` with its default scroll
behavior, which animates accumulated rotary deltas and adapts to low-resolution bezels
and high-resolution crowns. It shares the touch scroll state and handles momentum and
haptics; focus follows the active page using `requestFocusOnHierarchyActive`.

New-game setup starts immediately from one of two serving-team buttons. The repository
maps that team to the existing first-server representation, retaining compatibility with
saved player-based logs. The UI and service-ball accessibility descriptions expose teams.

The workout-launch preference is stored separately from scoring rules and defaults to on
when absent, including for existing installations. Settings autosave it through the same
ViewModel action queue. After a new match is saved, the activity checks the latest preference
before launching Samsung Health Tennis; turning it off leaves the user on the court.
The activity first queries Health Services `getCurrentExerciseInfo` without starting or
altering an exercise. Only `NO_EXERCISE_IN_PROGRESS` allows the external launch; an
active, unknown, failed, or timed-out (2.5 seconds) query leaves the user on the court.
Leaving the activity cancels the pending check, and launch eligibility is rechecked
against the current match/session and preference after the query completes. Detection
only covers workouts registered with Health Services.

The court route displays the result screen whenever the persisted match has a winner.
This also handles re-opening completed matches. Undo removes the winning point and
switches back to the court after saving; a new game uses the serving-team setup.
Endless matches still record set results and serving order but never declare a winner.

## Match visibility and ambient mode

The repository stores an optional `sessionMatchId` alongside the match log. Only a matching
unfinished match is an active session; older saved matches remain paused until continued.
Starting a match, scoring, and undo update that marker atomically with the point log.
Winning clears it; undoing the winning point activates it again. Returning from the court
to the menu pauses the session without discarding the match. The session flag survives
process recreation, allowing an active match to open directly on the court.

The ViewModel owns one `MatchSessionNotification`, backed by the Wear `OngoingActivity`
API. It posts a quiet local notification while a session is active and permission is
available, updates the existing notification's score/status, and cancels it on pause or
completion. Its PendingIntent routes both existing and recreated activities to the court;
the resume request is consumed so later navigation is preserved. A static score requires
no polling or foreground service. Notification permission is requested once when a session
first starts; denied/disabled notifications can be enabled from the app's settings.

`MainActivity` registers `AmbientLifecycleObserver` and supplies ambient state/timestamps
to Compose. Ambient rendering replaces the interactive screen, disposing any pending
point selection. `AmbientScoreScreen` draws a clock, point scores, games, and sets on
black, with no interactive input or periodic app timer. Ambient callbacks update the clock;
low-bit displays disable anti-aliasing, and burn-in protection uses outlined point scores
with edge clearance. Paused/menu screens show only the ambient clock. Wake restores the
normal UI, and Home remains under system control. The WAKE_LOCK permission is declared
for the Wear ambient API; the app does not acquire its own wake lock or keep the screen
in its full-power interactive state.

Android instrumentation tests use isolated SharedPreferences to cover session restoration,
pause/resume, wins and undo, replacement matches, endless mode, and settings isolation.

## Future companion

For a native Android companion, add `apps/phone` to settings.gradle.kts. It may depend on
the existing core modules. Shared module code does not imply shared storage: watch and
phone each have their own local database. Keep their UIs separate.

Add `core/sync` when there is an actual protocol, using the Wear OS Data Layer for paired
Android devices. The watch remains independently usable and authoritative for its active
match. Use stable match IDs, versioned payloads, revisions and explicit conflict rules;
do not merge competing point totals silently. A React Native companion can live under
apps/phone as a separate project, but Kotlin reuse needs a native bridge. No JavaScript
workspace tools are needed today.

## Exercise tracking

For the initial score-only app, users can start Samsung Health manually. If the app later
records workouts itself, use Health Services ExerciseClient and a foreground service.
Do not start a competing exercise session when another app is already recording. Optional
Health Connect export belongs on the phone; it does not remotely start Samsung Health.
