# Architecture

## Current boundaries

`apps/wear` → `core/data` → `core/scoring`, with a direct Wear → scoring dependency.
Keep screens, input handling, haptics, and watch lifecycle in the Wear app. Keep scoring
rules independent of Android so another UI can reuse them.

The starter repository stores the current match UUID and ordered point log in private
SharedPreferences. Replaying the log reconstructs state and enables undo. Writes happen
on a serial background dispatcher before publishing the new state. This intentionally
small implementation can migrate to Room when match history is added. Persisted data
includes a schema version; introduce migrations when changing the format or rule engine.

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
