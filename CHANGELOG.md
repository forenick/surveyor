# Changelog

Versions follow [Semantic Versioning](https://semver.org): while the mod is pre-release (0.x), a new feature
bumps the minor number (0.2.0 → 0.3.0) and a fix or small tweak bumps the patch number (0.2.0 → 0.2.1).
The version lives in `gradle.properties` and ends up in the jar name and in `fabric.mod.json`.

## 0.2.0 (2026-11-11)
- Shaft journal: every finished shaft is saved to `.minecraft/shafttools/journal.jsonl`.
- Chat summary after each shaft: spawn time, time inside, corpses looted, mined gems in Fine, powder,
  campfires, and session totals with the average spawn time.
- Spawn timer: from the first block hit in the Dwarven Mines to "WOW! You found a Glacite Mineshaft portal!".
- Sessions: end after 15 minutes without activity, or with `/shafttools session new`.
- Recorder: also writes the blocks you hit (`block_hit`).
- Replay test for recording 3 (2026-11-11).

## 0.1.0 (2026-11-08)
- Recorder dev tool: `/shafttools record start|stop`, `/shafttools mark <note>`.
- Detection layer: shaft visits, exits, corpses and loot, cold, campfires, Mayhem, pristine and sacks,
  checked against recordings 1 and 2.
