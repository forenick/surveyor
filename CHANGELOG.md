# Changelog

Versions follow [Semantic Versioning](https://semver.org): while the mod is pre-release (0.x), a new feature
bumps the minor number (0.2.0 → 0.3.0) and a fix or small tweak bumps the patch number (0.2.0 → 0.2.1).
The version lives in `gradle.properties` and ends up in the jar name and in `fabric.mod.json`.

## 0.3.0 (2026-11-11)
- Journal screen, opened with `/shafttools journal` or a hotkey (unbound by default; set it under
  Controls → ShaftTools). Built with owo-ui, which is now a required dependency (owo-lib 0.13.1+26.2).
  - Session tab: totals (shafts, average spawn, shafts per hour, powder, Lapis looted), one row per shaft,
    and details for the selected shaft: Mayhem buff, powder split, cold, campfires, corpse loot as item
    icons, mined gems. `<` and `>` page through earlier sessions.
  - Corpse loot merges every Rough, Flawed and Fine gem into one "Gemstones" slot counted in Fine
    (80:1); hover it for the full list. Flawless and better gems keep their own slot.
  - Sessions tab: every session, newest first; click one to open it.
  - Stats tab: all-time totals, average and fastest spawn, average spawn per session (chart),
    personal bests, and how often each shaft type and corpse type showed up in your shafts.
- All text uses Minecraft's own font renderer, so resource-pack fonts apply.
- Item icons are vanilla stand-ins for now (`item_icons.txt`); the tooltip shows the real name.

## 0.2.1 (2026-11-11)
- Spawn timer counts Pickobulus (and other pickaxe abilities) as mining, not only block hits.
- Spawn timer pauses: a gap of more than 40 s without mining only counts as 40 s, so breaks
  (e.g. using the AH) don't inflate your spawn time.
- Fixed: mining between "WOW!" and entering the shaft no longer starts a new timer.
- Replay test for recording 4 checks all six spawn times.

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
