# Changelog

[SemVer](https://semver.org). Betas are `X.Y.Z-beta.N` (GitHub pre-releases).

## Unreleased
- Bars / Line and range arrows keep the Stats tab's scroll.
- Closing the game inside a shaft saves that shaft.
- Settings changed while prices load are kept when you quit.
- Price downloads can't get stuck.
- GitHub links in Mod Menu.
- Recordings leave out player chat.
- Small Stats tooltip fixes.
- An old ShaftTools jar can't run next to Surveyor.
- Square mod icon with a plain green background.

## 0.21.1 (2026-11-13)
- Open source under the LGPL-3.0.

## 0.21.0 (2026-11-13)
- Renamed to Surveyor, new icon. Remove the old `shaftjournal` jar.
- Data moves from `.minecraft/shaftjournal/` to `.minecraft/skysurveyor/` on first start.
- New commands `/surveyor` and `/sv`. `/sj` and `/shaftjournal` still work.
- Journal hotkey stays bound.

## 0.20.0 (2026-11-13)
- Stats charts can switch between bars and a line.
- Hover an average line for median, best, worst and more.
- Clearer range picker.
- Coins per shaft type bars show each type's share of all coins.

## 0.19.0 (2026-11-13)
- Stats range: All time, Last 7 days, Last 5 sessions.
- Average coins per shaft type.
- Drop chances in corpse tooltips.
- Coins / h per session chart.

## 0.18.4 (2026-11-13)
- Stats tooltips: gaps between groups, white part names, Shift icon.

## 0.18.3 (2026-11-13)
- Coins tooltip shows a full breakdown. Hold Shift for the short version.
- Corpse rows show only the average, total in the tooltip.
- Reordered All time totals.

## 0.18.2 (2026-11-13)
- Settings choices are radio options.
- Reset all button per category.

## 0.18.1 (2026-11-13)
- Settings page no longer undoes hand edits to `config.json`.
- Tax box applies after you stop typing.
- Footer turns red when settings can't be saved.
- Small settings page fixes.

## 0.18.0 (2026-11-13)
- Settings page (`/sj settings` or the journal's Settings button) with search.
- Money settings, price ages and Group by day are on it.

## 0.17.3 (2026-11-13)
- Sessions only count time in the Mines and shafts.
- New prices no longer scroll the journal to the top.
- Fixed a hitch on shaft entry with big journals.
- Small layout fixes.

## 0.17.2 (2026-11-13)
- Shaft view: net coins in the Coins heading.
- Back button fits at GUI scale 4.

## 0.17.1 (2026-11-13)
- `hud.json` is backed up before converting an old format.
- Builds now test that old saves still load.

## 0.17.0 (2026-11-12)
- Session tab shows only the current session. Older ones open from Overview with Back and < >.
- By day toggle.
- Tab icons.

## 0.16.0 (2026-11-12)
- Overview tab groups sessions by day with day totals.
- Journal opens on Overview, or Session while one is going.
- All-time Coins tooltip shows where coins came from.

## 0.15.0 (2026-11-12)
- Coins in the journal tables, Session tab and Stats.
- Money HUD values and a Money preset.

## 0.14.2 (2026-11-12)
- Fixed crystals counted when you already had them.
- Fixed bad price data wiping saved prices.
- Fixed Citrine and Peridot on Turkish systems.
- Smaller `journal.jsonl`.

## 0.14.1 (2026-11-12)
- Fixed Inside times wrapping at GUI scale 3.

## 0.14.0 (2026-11-12)
- Every shaft gets a coin value (gems, drops, corpse loot, scrap, crystals, minus keys and campfires).
- Coins line in the chat summary and a Coins section in the shaft view.
- Prices from the Bazaar and lowest BIN, cached for offline use.
- Each shaft saves the prices it was valued at.

## 0.13.2 (2026-11-12)
- Journal tables and charts use the full width at GUI scale 2.
- Edit HUD panel grows on wide screens and is opaque.

## 0.13.1 (2026-11-12)
- Ping works on Hypixel.
- Format tooltips show the unit.
- Colour rules only accept values that fit.

## 0.13.0 (2026-11-12)
- HUD values: FPS, Ping, TPS, Clock, mod and Minecraft version.
- + Preset button in Edit HUD.
- Number formats shown as format codes.

## 0.12.2 (2026-11-12)
- Small text tweaks in Edit HUD.

## 0.12.1 (2026-11-12)
- Small text tweak in Edit HUD.

## 0.12.0 (2026-11-12)
- Colour rules can compare against another value, like `{average_spawn}`.
- Breadcrumbs in the Edit HUD panel.

## 0.11.2 (2026-11-12)
- Per hour values show waiting dots until they're ready.
- Empty part of the side panel no longer blocks clicks.

## 0.11.1 (2026-11-12)
- Edit HUD panel is full height, no folding sections.
- Small layout tweaks.

## 0.11.0 (2026-11-12)
- Edit HUD on one page per box.
- Bigger buttons, colour picker grid, tooltips that fit the screen.
- Drag lines past the edge to scroll.

## 0.10.1 (2026-11-12)
- Fixed Enter or Space removing extra lines.
- Edit HUD keeps its scroll position.
- Many small Edit HUD fixes.

## 0.10.0 (2026-11-12)
- Edit HUD lines show how they look. Each line has its own page.
- Label, range, format and colour per line.
- Value and colour menus for custom text.

## 0.9.3 (2026-11-12)
- No changes in game.

## 0.9.2 (2026-11-12)
- No changes in game.

## 0.9.1 (2026-11-12)
- Fixed colour rules on custom text lines.
- Fixed Ctrl+Z while typing in Edit HUD.

## 0.9.0 (2026-11-12)
- Custom text lines with `&` colour codes and `{value}` tags.
- Colour rules: ready-made ones and your own.

## 0.8.0 (2026-11-12)
- Add your own HUD boxes and any value to any box.
- Time ranges: this shaft, session, per hour, all-time.

## 0.7.0 (2026-11-12)
- Fixed box titles not saving with Caxton.
- Edit HUD saves by itself.
- Drag lines to reorder.
- Show in: Shafts, Mines, Elsewhere.
- Undo and redo.

## 0.6.1 (2026-11-11)
- Boxes stay lined up at any window size or GUI scale.
- Broken `hud.json` is backed up instead of overwritten.
- Many small HUD fixes.

## 0.6.0 (2026-11-11)
- Edit HUD snaps boxes to the screen and to each other. Hold Ctrl to place freely.

## 0.5.3 (2026-11-11)
- Edit HUD toolbar moves out of the way.
- Arrow keys nudge the selected box.

## 0.5.2 (2026-11-11)
- Scroll on a box to resize it.
- Reset asks first.

## 0.5.1 (2026-11-11)
- Fixed an unentered portal's spawn time going to the next shaft.
- Fixed old sessions showing after a break.
- Small HUD fixes.

## 0.5.0 (2026-11-11)
- HUD with Session, Spawn and Current shaft boxes.
- Edit HUD to move and style boxes.

## 0.4.1 (2026-11-11)
- Fixed a crash when hovering next to journal text.

## 0.4.0 (2026-11-11)
- Renamed to ShaftJournal. Commands `/shaftjournal` and `/sj`.
- Corpse loot icons are real SkyBlock items (with texture packs).

## 0.3.6 (2026-11-11)
- Shafts / h best needs at least 5 shafts.

## 0.3.5 (2026-11-11)
- Journal re-lays itself out on resize.
- Small journal fixes.

## 0.3.4 (2026-11-11)
- Shorter personal best labels.

## 0.3.3 (2026-11-11)
- Reworked personal bests with arrows to open them.
- Spawn chart average line and Last 5 sessions average.

## 0.3.2 (2026-11-11)
- Shaft type bars in the gem's colour.

## 0.3.1 (2026-11-11)
- Shaft types seen and spawn chart axis on the Stats tab.

## 0.3.0 (2026-11-11)
- Journal screen with Session, Sessions and Stats tabs.

## 0.2.1 (2026-11-11)
- Spawn timer counts pickaxe abilities and caps breaks at 40 s.

## 0.2.0 (2026-11-11)
- Shaft journal, chat summary after each shaft, spawn timer and sessions.

## 0.1.0 (2026-11-08)
- First version: shaft detection.
