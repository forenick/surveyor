# Changelog

Versions follow [Semantic Versioning](https://semver.org): while the mod is pre-release (0.x), a new feature
bumps the minor number (0.2.0 → 0.3.0) and a fix or small tweak bumps the patch number (0.2.0 → 0.2.1).
The version lives in `gradle.properties` and ends up in the jar name and in `fabric.mod.json`.

## 0.9.0 (2026-11-12)
HUD slice 3: free text and colours that follow the value.
- Every line in Edit HUD has a **✎** button that opens the line's own page; **Back** returns to the box.
- **Ready-made colour rules** to tick: the spawn timer turns red above the session's average spawn, and Cold
  turns yellow at -50❄ and red at -80❄ (your numbers).
- **Your own colour rules:** "Over" or "Under" a number, then a colour, e.g. green under 1:30 and red over
  3:00. Times can be typed as 1:30 or 90. The first rule that matches wins, before the ready-made one.
- **Free text:** tick "Use free text" and the line becomes text you type, starting from how it looks now,
  e.g. `&7Spawn &8» &e{spawn_timer} &7(avg {average_spawn})`.
  - `&0`-`&f` are the 16 colours, `&l` bold, `&o` italic, `&n` underline, `&m` strikethrough, and `&r` goes
    back to the box's label colour.
  - Tags can pick a range and number format: `{session_powder:all_time:short}`. Hover the text field for
    every tag and its options. A tag with a typo stays as typed, so you can spot it.
  - A colour rule colours only the line's own value; the rest of the text keeps your colours.
  - Box titles take the same tag options.
  - Lines with free text show their name in aqua; their format and range are set in the tags.

## 0.8.0 (2026-11-12)
HUD slice 2: your own boxes, any value in any box, and time ranges.
- **+ New box** in the Edit HUD toolbar adds an empty box in a free spot and selects it. **Delete box** at the
  bottom of the side panel removes the selected one (it asks first, and Ctrl+Z brings it back).
- **+ Add line** lists every value; one click adds it to the box. The same value can be in several boxes, or
  twice in one box with different ranges. The **×** at the end of a line removes it.
- **Time ranges:** Powder and Fine from corpses can show this shaft, the session, per hour or all-time;
  Shafts can show the session or all-time. The Range button sits under the line's name; click to change it.
  - **Shaft** is the shaft you're in, or the last one after you leave, so you can read it while searching.
  - **Per hour** is per hour of the session, counted up to now like Shafts/h, and shows "/h".
  - Lines from older `hud.json` files keep showing the session, as before.
- The side panel scrolls when it's taller than the screen (a box with many lines, or a small window).

## 0.7.0 (2026-11-12)
Edit HUD: Nick's fixes and wishes after trying 0.6.1.
- **Titles now save with Caxton.** The Caxton font mod cancels the text field's "changed" call, so a
  typed box title never reached the box and was lost on close. The editor now reads the field itself.
- **First letter of the title:** the title field scrolls back to its start once the panel is laid
  out, so the first letter can't stay hidden (seen with Caxton).
- **Changes save by themselves** a moment after each change, not only when the editor closes. The
  hint and the Done button's tooltip say so.
- **Drag lines to reorder them:** grab a line by the dots on its left (or its name); a yellow bar
  shows where it drops. The ▲/▼ buttons are gone.
- **"Show in" checkboxes** replace the Visible button: Shafts, Mines (Dwarven Mines outside a shaft)
  and Elsewhere, in any combination. `hud.json` moves to version 3; old choices convert on load.
- **Undo and redo:** arrow buttons in the toolbar, or Ctrl+Z and Ctrl+Shift+Z (also while typing a
  title). Every change can be undone, Reset included; a drag, a slider move or a burst of scrolling
  is one step. The history lasts until the editor closes.
- Bold: the toggle works, but with the metropolis font pack regular and bold text use the same
  (bold) font file, so it can't look different. Turn the pack off to see it.

## 0.6.1 (2026-11-11)
Code review fixes for the HUD and the editor.
- Boxes are now placed from an edge or the middle of the screen (the "anchor"), plus a distance in
  pixels. The anchored edge stays put when the window, the GUI scale or the box's text changes, so
  boxes lined up on the left, right or middle stay lined up in game, and stacked boxes keep their
  gap on any screen size.
  - Snapping to the screen picks the anchor; lining up with a box anchored on the same edge takes
    its anchor; anywhere else a box is anchored to the third of the screen it's in.
  - `hud.json` moves to version 2. Old files are converted on load: boxes at an edge or the middle
    stay exactly where they were, others can move a few pixels once.
- Snapping skips spots a box can't reach without crossing the margin, so the guide never points
  somewhere the box isn't.
- The gap number is drawn beside both boxes instead of over their text.
- Scrolling on a box changes its size once per whole notch, so touchpads don't jump straight to
  0.5× or 2×.
- The toolbar stays put while Reset asks "Really reset?". When no spot is free it picks the one
  that covers the least, and never a whole box if it can help it.
- The spawn timer clears once the session has run out (15 minutes without activity) instead of
  showing a time that the next block hit will throw away.
- Saving the HUD can no longer replace your layout with the defaults while it is still loading,
  and saves happen one at a time in order.
- A `hud.json` that can't be parsed is kept as `hud.json.<date-time>.bad`, so a second broken file
  doesn't replace the first backup. One that only can't be read for a moment stays where it is; the
  first save copies it first.
- Missing or duplicate box ids in `hud.json` are repaired on load.

## 0.6.0 (2026-11-11)
Snapping in Edit HUD (mockup `Docs/mockups/hud-snapping.html`, approved by Nick).
- While you drag, a box snaps when an edge or its middle comes within 4 pixels of:
  - the screen margin on any side, or the middle of the screen (e.g. right under the crosshair)
  - another box's left, right, top, bottom or middle (lined up)
  - the spot right next to or under another box, with a 4 pixel gap (stacked)
- Pink guide lines show what it lined up with; stacked boxes show the gap as a small "4".
- Hold Ctrl while dragging to place a box freely.
- A box snapped to the screen edge or middle stays there at any window size or GUI scale.
- Boxes now keep 4 pixels from the screen edge (was 2), the same as the gap between boxes.
- The side panel hides while you drag, so it can't cover the box you're moving.

## 0.5.3 (2026-11-11)
Edit HUD tweaks.
- The toolbar moves out of the way: when a box or the side panel is under it, it goes to the bottom
  middle or a free corner, so no box can get stuck under it. It doesn't move while you drag.
- Arrow keys nudge the selected box by 1 pixel, or 10 with Shift (not while typing a title).
- With no finished shaft this session, the editor previews the Session box with example numbers,
  instead of "Shafts: 0" next to an example "Shafts/h: 19.4". The HUD itself still shows 0.
- The hint is two lines, so it fits on small screens.

## 0.5.2 (2026-11-11)
Edit HUD tweaks.
- Scroll on the selected box to change its size, in steps of 0.1 from 0.5× to 2×. The Size slider
  follows, and scrolling on the slider itself now also moves exactly one step per notch.
- Reset asks first: the first click turns the button into "Really reset?", the second resets, and
  clicking anywhere else cancels.
- The hint at the bottom mentions scrolling.

## 0.5.1 (2026-11-11)
Code review fixes for the HUD.
- A portal you found but didn't enter within 40 s no longer gives its spawn time to the next
  shaft (HUD and journal).
- The Session box no longer shows an old session after a break of over 15 minutes or a restart.
- Shafts per hour now counts the time since the last shaft too, so it drops during a long search.
- A `hud.json` that can't be read is kept as `hud.json.bad` instead of being overwritten by the
  defaults.
- Changes made in the editor right after startup are no longer replaced by the saved layout loading late.
- The Current shaft box is titled "Shaft" instead of "- Shaft" until the shaft type is known.
- The short number format shows 1.0M instead of 1000.0k from 999,950 up.
- A number format out of range in `hud.json` is reset to the first one.
- The HUD adds up the session only when a shaft is saved, and builds its text only when the values
  refresh instead of every frame.

## 0.5.0 (2026-11-11)
HUD, slice 1 of the design in `Docs/hud-design.md` (mockup `Docs/mockups/hud-v1.html`).
- Three HUD boxes:
  - **Session:** shafts, shafts per hour, powder and Fine from corpses. Average spawn is there
    but hidden.
  - **Spawn:** the spawn timer and the session average, under the crosshair.
  - **Current shaft:** time inside, corpses looted/total with their types, cold and campfires.
    Only shows inside a shaft.
- The spawn timer runs while you search, stops at "WOW!" and shows that time until you leave the
  shaft, then waits for the next search. A break counts for 40 s at most, as in the journal.
- **Edit HUD** in the journal's top bar opens the editor:
  - drag boxes anywhere, then click one to change it
  - per box: title, when it shows, background, size, alignment, and the label and value colours
    (16 Minecraft colours, plus bold)
  - per line: show/hide, order and number format
  - **Reset** brings back the defaults
  - values that aren't known right now show example numbers in the editor
- Saved to `.minecraft/shaftjournal/hud.json` when the editor closes. A hand edit that breaks
  something is repaired on load where possible.
- Box titles can contain `{shaft_type}`, which the Current shaft box uses for e.g. "Jade Shaft".

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
