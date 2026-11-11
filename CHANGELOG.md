# Changelog

Versions follow [Semantic Versioning](https://semver.org): while the mod is pre-release (0.x), a new feature
bumps the minor number (0.2.0 → 0.3.0) and a fix or small tweak bumps the patch number (0.2.0 → 0.2.1).
The version lives in `gradle.properties` and ends up in the jar name and in `fabric.mod.json`.

## 0.4.1 (2026-11-11)
- Fixed a crash when hovering a journal label next to its text instead of on it (e.g. the empty
  part of a full-width line). owo-lib passes "no text here" on to Minecraft, which crashes on it;
  the journal's labels now answer with an empty style instead.

## 0.4.0 (2026-11-11)
- Renamed from ShaftTools to ShaftJournal. The journal is becoming the mod's main menu.
- `/shaftjournal` or the short `/sj` opens the journal. Every subcommand works under both names,
  e.g. `/sj record start` and `/sj session new`. `/shafttools` is gone.
- The data folder is now `.minecraft/shaftjournal`. On first start the old `.minecraft/shafttools`
  folder (journal and recordings) is moved there, unless the new folder already exists.
- The journal hotkey has a new internal name, so a key bound to it before needs binding again.
- Corpse loot icons are the real SkyBlock items: each icon carries the item's SkyBlock ID, so
  texture packs through Firmament or Catharsis show their texture, and while Hypixel's resource
  pack is loaded it uses Hypixel's own model. Without either, the vanilla stand-ins show as before.
  Enchanted items and keys glint. IDs and models come from the NEU item repo
  (`shaftjournal/skyblock_items.txt`).
- The merged gemstone slot shows the most common gem in it instead of an amethyst shard.

## 0.3.6 (2026-11-11)
- The "Shafts / h" personal best only counts sessions with at least 5 shafts, so a short lucky
  session can't hold it.

## 0.3.5 (2026-11-11)
Fixes from a code review of 0.3.0 to 0.3.4.
- The journal screen re-lays itself out when the window is resized or the GUI scale changes,
  instead of keeping the old sizes until a tab is clicked.
- Session tab tiles show their values in bold, as in the mockup.
- Personal-best arrows of a shaft without an ID say "Open shaft" instead of "Open null".
- The slowest bar in the spawn chart no longer reaches past the top line.
- Tiny gem amounts show as "<0.1" instead of "0.0"; 9.95 and up show as "10" in the slot.
- "Last 5 sessions" says "same" when it is within a second of the all-time average.
- `shaft_types.txt` and `item_icons.txt` both allow a trailing "# comment"; a bad color falls back
  to aqua instead of crashing when the journal opens.
- Both files are read once instead of every time the journal opens.
## 0.3.4 (2026-11-11)
- Personal bests use short labels: "Shafts / h", "Shafts / session", "Lapis / shaft", "Powder / shaft",
  "Gems / shaft", "Coins / shaft", "Coins / h".
- The open arrow is a small dark gray icon (with a larger click area) instead of an aqua ».
- Corpses looted shows a yellow "(soon)" where each type's coin value will go once prices exist.
- "Corpse chance per shaft" is now "Corpse % per shaft" without the explanation line; "average" is
  "avg" next to the chart.

## 0.3.3 (2026-11-11)
- Stats tab layout: Personal bests moved under "Corpses looted"; the spawn chart is taller.
- Personal bests reworked: fastest spawn, fastest session (needs 3+ timed spawns), most shafts per hour,
  most shafts in a session, most Lapis, powder and gems in one shaft. Coin records show "Soon" until
  prices exist, as does the all-time Coins total.
- The gray shaft IDs and dates next to records are replaced by a » arrow that opens that shaft or
  session. Clicking a bar in the spawn chart opens that session.
- Spawn chart: starts right after the axis (no more gap on the left), shows the all-time average as a
  yellow line, and has the first and last session date under it. New "Last 5 sessions" average with
  how much faster or slower it is than your all-time average.
- "Avg spawn per session" lost "oldest first"; "Shafts with this corpse" is now "Corpse chance per
  shaft" with a line explaining it.

## 0.3.2 (2026-11-11)
- Stats tab: the type 1 / type 2 / crystal counts moved from a line under each shaft type into a tooltip;
  hover the row to see them.
- Shaft type bars are colored like the shaft's gem or material (colors in `shaft_types.txt`; a display
  choice, easy to change).
- No more "·" separators: the screen uses layout, commas and parentheses instead, and the chat summary
  uses commas. The shaft details now show "Entered" and "Exit" as their own rows.

## 0.3.1 (2026-11-11)
- Stats tab, "Shaft types seen": one row per shaft type (Jasper, Peridot, Vanguard, ...) with its share of
  your shafts, and a smaller line underneath with how many were type 1, type 2 and crystal. Names come
  from `shaft_types.txt`; codes not listed there show as the code.
- Average spawn chart has a time axis (0, 1m, 2m, ...) with grid lines, and bars sized to fit the screen.
- "Corpses seen, per shaft" is now "Shafts with this corpse": the share of your shafts that had at least
  one corpse of that type. Hover a row for the count and the average per shaft. Moved under Personal bests.

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
