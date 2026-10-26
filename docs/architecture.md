# Architecture

## Current boundaries

`apps/wear` → `core/data` → `core/scoring`, with a direct Wear → scoring dependency.
Keep screens, input handling, haptics, and watch lifecycle in the Wear app. Keep scoring
rules independent of Android so another UI can reuse them.

The repository stores the current match UUID, immutable rules, first server, and ordered
point log in private SharedPreferences (schema version 2). Replaying the log reconstructs
state, all four players' service turns, and undo. Version 1 logs replay with the original
default rules and first server. Default settings are stored separately and only apply to
new matches. A fresh install has no current match until the user starts one.

Writes run off the UI thread and complete before publishing the new state. The ViewModel
gates actions while a write is pending; the repository updates its in-memory state only
after a successful commit and exposes failures to the UI. It caches the current state
for normal scoring and replays the log for undo. This small implementation can migrate
to Room when a match archive is added.

The Wear UI owns navigation, haptics, selection timeout, and bezel cooldown. The pure
`PointInput` reducer requires selection before confirmation, resets rotary progress when
direction changes, and shares a selection between touch and bezel input. Selection is
transient and cleared when leaving the court or pausing the app; only confirmed points
are persisted. Scoring and input transitions have local JVM regression tests.

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
