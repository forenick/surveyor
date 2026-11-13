# Changelog

Versions follow [Semantic Versioning](https://semver.org): while the mod is pre-release (0.x), a new feature
bumps the minor number (0.2.0 → 0.3.0) and a fix or small tweak bumps the patch number (0.2.0 → 0.2.1).
The version lives in `gradle.properties` and ends up in the jar name and in `fabric.mod.json`.

## 0.21.0 (2026-11-13)
ShaftJournal is now **Surveyor**, with a new icon (a brass theodolite). Shafts come first, more mining features
are planned. The journal itself is still called the Shaft Journal.
- **Remove the old `shaftjournal-*.jar` from your mods folder.** The new jar is `skysurveyor-0.21.0.jar`. The game
  won't start with both and says Surveyor is incompatible with ShaftJournal, so they can never both write your data.
- **Your data moves over by itself.** On the first start, `.minecraft/shaftjournal/` is renamed to
  `.minecraft/skysurveyor/` (or the old `shafttools/` folder, if that's the only one you have). Journal, HUD,
  settings and recordings come through exactly as they were. If a `skysurveyor` folder already exists, nothing is
  moved. If the move fails, Surveyor keeps using the old folder and tries again on the next start.
- **New commands:** `/surveyor` and `/sv`. `/sj` and `/shaftjournal` still work. All of them open the journal.
- **Your journal hotkey stays bound.** It's under "Surveyor" in the Controls screen now.
- The settings page says "Surveyor Settings". New "Surveyor version" HUD lines show `Surveyor 0.21.0`; a version
  line you already have keeps its text, since that's your own label.
- The mod id is `skysurveyor` (plain `surveyor` is taken by Surveyor Map Framework).

## 0.20.0 (2026-11-13)
Stats tab feedback round.
- **Bars or a line.** Both charts have their own `< >` arrows (with dots showing which one you're on) to switch
  between bars and a line chart. Hover or click anywhere in a session's column in line mode.
- **Hover an average line** (or its "Yellow line" / "White line" legend) for a closer look. Spawn time: median
  shaft, the middle half of your shafts, how many sessions and shafts beat the average, fastest and slowest
  session, and your latest session vs the average in %. Coins / h: median, best and worst session, sessions above
  average, sessions that made coins, how much of all coins your best session made, and your latest session vs the
  average in %.
- **The range picker is clearer.** It reads `Range All time` with the range in yellow on a light panel, dots show
  where you are in the cycle, and the tooltip says what it does instead of naming the next range.
- **Avg coins per shaft bars show each shaft type's share of all coins made in shafts**, so they no longer fill up
  for the best type. The share is in the tooltip too, and its "Per shaft" and "Per variant" parts say "(avg)".
- Chart legends read `Yellow line: all-time avg`.

## 0.19.0 (2026-11-13)
More on the Stats tab.
- **Pick a range.** The `<` `>` arrows next to the left column's title switch the whole Stats tab between
  **All time**, **Last 7 days** and **Last 5 sessions**. Everything follows it: totals, corpses, personal bests,
  both charts and the shaft types. In a range, the spawn column compares its average to your all-time average.
  The range is remembered until you close the game.
- **Average coins per shaft type** under "Shaft types seen", best first. Hover a type for its average, best and
  total, the average per part (mined gems, corpse loot, keys and so on) and per variant (type 1, type 2, crystal).
- **Drop chances in the corpse tooltips.** Each loot item shows how many of that type's corpses dropped it, like
  `7.0% 12 Flawless Ruby Gemstone +20.7M`. Rare drops get a decimal (`0.6%`). These are counted from your own
  corpses, so they get better the more you loot. Works without prices too.
- **Coins / h per session chart** under the corpse bars: one bar per session, gold above 0 and red below, with a
  white line for your average. Hover a bar for its coins, length and shafts, click it to open the session.

## 0.18.4 (2026-11-13)
Stats tooltips are easier to read.
- **Small gaps between groups** in the Coins and corpse tooltips, so each part (and its items) stands on its own.
  The gaps are half a line, so they don't push items off small screens much.
- **Part names are white**, their items stay gray underneath, and Net is white too.
- **The Shift hint has a Shift arrow icon** and reads `Hold Shift for the short version` with Shift in white.

## 0.18.3 (2026-11-13)
Stats tab tweaks.
- **All time is Coins, Powder, then a gap, then Shafts, Sessions and Time shafting.**
- **The Coins tooltip is a full breakdown now.** Each part (mined gems, other drops, corpse loot, keys, campfires and
  so on) shows its share of what you earned and its biggest items, corpse loot is split per corpse type, and when
  there are costs you get earned, spent and net apart. Hold **Shift** for the short version with just the parts.
  Long lists are cut to fit your screen, with an "N more" line for the rest.
- **Corpse rows only show the average**, f.e. `Lapis 172 (+178.6k avg.)`. The total is in the tooltip, which now
  also lists that corpse type's loot items by coins.

## 0.18.2 (2026-11-13)
Settings page tweaks.
- **Choices are a list of radio options** instead of a row of wide buttons. Every setting's control (choices, the tax
  box, toggles) starts at the same spot, so they line up from card to card. Hover an option for what it does.
- **Reset all** sits at the top right of the category, next to its name, in red with a reset icon. It only shows when
  something in that category isn't on its default, like the reset icon next to each setting.

## 0.18.1 (2026-11-13)
Fixes from a code review of 0.18.0.
- **Saving from the settings page only writes the setting you changed.** Before, every money setting was written
  from memory, so a hand edit to `config.json` that hadn't loaded yet (or couldn't load, f.e. a tax of `"1,5"`) was
  quietly undone by the next click on the page.
- **Opening the settings page no longer rewrites a long tax.** The tax box held 12 characters, so a hand-written tax
  like `1.1666666666666667` was cut short and saved just by opening the page.
- **Two quick changes can't undo each other.** A save waiting behind a price fetch could put an older setting back for
  a moment, and a click in that moment built on it and lost the newer change.
- **Tax box**: the tax applies half a second after you stop typing or when you click away, not on every key, so the
  file and every coin number aren't redone per key. Text that isn't a number goes back to the saved tax when you click
  away, so the red note doesn't stay up. `5f`, `1e1` and the like no longer count as numbers.
- **Settings page says when it can't save.** If `config.json` can't be written (f.e. a broken hand edit), the footer
  turns red instead of saying it saved.
- **Deleting `config.json` while the game runs starts over** with the default settings again, as before 0.18.0.
- **Back from settings** shows the journal with the new Group by day right away, without the old grouping for a tick.
- The Net tooltip shows the tax as typed (`2%`, not `2.0%`).
- Price ages on the settings page only redraw when the text changes. A new settings category names its own icon, so
  one without an icon can't crash the page. Group by day's default lives in one place.
- Old-save tests now include a `config.json` written by 0.18.0's settings page.

## 0.18.0 (2026-11-13)
Settings page (money slice 3).
- **Settings button** in the journal's top bar, next to Edit HUD, and `/sj settings`. Back (or Esc) returns to the
  journal.
- **A real settings page**: categories on the left (Money, Journal), a search box that looks through every category,
  and a card per setting with its description. A reset arrow shows next to a setting once it's changed, and each
  category has a "Reset to defaults" button. Hover a choice for what it means and which one is the default.
- **Money**: sell price, Bazaar tax, gem tier, Gemstone Crystals and prices for old shafts, the settings that were
  only in `config.json` before. A tax that isn't a number from 0 to 100 shows a red note and the last good value stays.
- **Prices**: when the Bazaar and lowest BIN prices were last fetched.
- **Group by day** moved from the Overview tab to the settings page (Journal).
- Every change saves to `config.json` right away and every coin number in the journal and HUD updates. Other keys
  in the file stay as they are, and hand edits still work.

## 0.17.3 (2026-11-13)
Fixes from a code review of 0.15.0 to 0.17.2.
- **Sessions only count Mines and shaft time.** Hitting blocks on the Garden, the Hub or anywhere else no longer starts
  a session or keeps one going, so the Session tab doesn't say your session has started while you're farming, and
  a session's length and coins per hour aren't stretched by time spent elsewhere. Mining inside a shaft still counts.
- **Overview with By day off** sizes Coins and Coins / h for the rows it shows, so columns no longer drop out early
  on narrow screens.
- **New prices no longer scroll the journal back to the top.** The tables, the shaft view and the Stats columns keep
  where you scrolled when prices arrive after opening it.
- **HUD coins**: all-time coins are added up off the game thread when prices change, so a big journal no longer
  causes a hitch on shaft entry. "This shaft" is only re-valued when something in the shaft changes. A price update
  that arrived at just the wrong moment could be missed until the next one; fixed.
- **config.json**: a hand edit to the money settings made right before clicking By day now loads instead of being
  skipped.
- **Top bar** works out the room for the date from the real button sizes, so the "< Back" button keeps fitting at
  GUI scale 4.
- Old-save tests now include Nick's journal, HUD and config written by 0.17.0, and check that By day loads back.

## 0.17.2 (2026-11-13)
- **Shaft view: the net sits in the Coins heading** (`Coins +32.2M`) with the parts indented under it, so it reads
  as a breakdown. The extra Coins line near the top is gone. Hover the heading for how coins are counted.
- **Crystal shafts** show a gray `(crystal)` after the ID instead of a pink "Crystal".
- **The Back button fits at GUI scale 4.** On an older session the "Shaft Journal" title steps aside when Back,
  the arrows and the date need the room, instead of Back running into the Stats tab.

## 0.17.1 (2026-11-12)
Old shafting data is now checked on every build, so an update can't wipe or corrupt it.
- **Old-save tests.** `src/test/resources/old-saves/<version>/` holds real files older versions wrote: Nick's
  journal, HUD and config from 0.16.0, the 0.2.x journal, and HUD files written by the 0.5.3, 0.6.1, 0.9.0 and 0.12.0
  jars (all three HUD formats). The build fails if any old shaft, box, line or setting is lost or changed, or if old
  shafts show different numbers than 0.16.0 did (`expected-journal.txt`, also checked against a clean 0.16.0 build).
- **HUD backup before converting.** When `hud.json` is in an older format (or a hand edit got repaired), the original
  is copied to `hud.json.<time>.old` before the first save overwrites it.
- **A journal line that's just `null`** is skipped instead of loading as an empty shaft.

## 0.17.0 (2026-11-12)
Journal changes from Nick's feedback on 0.16.0.
- **Session tab shows only the session you're in.** With no session going it says so (and that older sessions are in
  the Overview).
- **Older sessions open from the Overview** (or a Stats record arrow) with a **Back** button to where you came from,
  plus < > to step through sessions. The tab you came from stays highlighted.
- **By day toggle** next to Edit HUD on the Overview: a green check when sessions are grouped by day, a red cross
  for one plain row per session. On by default, and remembered in `config.json` (`"journal": {"byDay": true}`).
- **More room between days**, and the day row's Coins / h says `(avg)`.
- **Icons on every tab**: a calendar for Overview and a pickaxe for Session, next to the Stats chart.

## 0.16.0 (2026-11-12)
Journal changes from Nick's feedback on 0.15.0.
- **Overview tab** (was Sessions) comes first. It groups your sessions by day: each day gets a row with its total
  time, shafts, powder, coins and coins per hour, and the sessions under it show their start time.
- **The journal opens on Overview**, or on Session while that session is still going.
- **Coins / h is gray** in the Overview, so only the coin totals are gold.
- **Stats tab** has a bar chart icon.
- **Corpses looted** reads `Lapis 172 +30.7M (+178.6k avg.)`. On narrow screens the total moves to the tooltip.
- **All-time Coins tooltip** now shows where the coins came from (mined gems, corpse loot, keys, campfires and so
  on), then the average per shaft and per hour shafting.

## 0.15.0 (2026-11-12)
Coins everywhere, slice 2 of the money tracker (design in `Docs/money-design.md`).
- **Journal tables**: the Session table gets a Coins column per shaft, the Sessions table Coins and Coins / h per
  session. Losses show in red, a shaft worth nothing as a gray 0. At GUI scale 4 the Session table now drops Powder
  to make room (it's still in the tiles and the shaft view).
- **Session tab** gets a Coins tile. Hover it for coins per shaft and per hour.
- **Stats tab**: the all-time Coins total (hover for the average per shaft and per hour), coins per corpse type
  ("Umber 12 +250k each", loot minus the key, hover for both), and the Coins / shaft and Coins / h records with an
  arrow to that shaft or session.
- **HUD values** in a new Money group: Coins (this shaft, session, per hour, all-time) and Coins per shaft (session,
  all-time). Formats 1.2M, 1,204,310 or 1204310. "This shaft" counts live while you're inside. Colour rules for
  coins take `1.5M`, `250k` or `2b`.
- **Money preset** under + Preset: this shaft, session, per hour and per shaft in gold.
- The journal redraws every tab when new prices arrive, not just the shaft view.

## 0.14.2 (2026-11-12)
Code review fixes for 0.14.0 and 0.14.1.
- **Crystals you didn't get don't count.** When `You have already obtained this Crystal!` follows the
  `✦ CRYSTAL FOUND` block, no crystal was given, so it's no longer worth Perfect minus 5 Flawless.
- **Only the 12 gem crystals use the crystal setting.** Any other item named "... Crystal" gets its own price.
- **Broken price answers are ignored.** A Bazaar or lowest BIN answer with no prices in it no longer wipes the saved
  prices, and the second lowest BIN source is tried. A failed first source no longer pauses refreshes when the
  second one worked.
- **Old prices aren't saved as that day's.** A shaft only saves its prices when the Bazaar prices are at most an hour
  old (f.e. not when the refresh failed after a long break), and never when there are no Bazaar prices. Otherwise
  it uses today's prices.
- **The journal updates when prices arrive.** The shaft view redraws when prices or `config.json` settings finish
  loading, so the first open no longer shows "No prices yet" or old settings until you reopen it.
- **Shaft gems on Turkish systems.** Citrine and Peridot gems were missed when the computer's language was Turkish.
- **Shafts from before 0.14.0** take Sacks removals off their other drops, so a Supercrafted item counts once.
- **Inside fits any font.** The Inside column is measured from the times in the session instead of a fixed width,
  so Caxton and bold font packs no longer wrap it. Sessions of short shafts can get the # column back at GUI scale 3.
- `journal.jsonl` saves each shaft's prices as short lists, so the file grows slower. Older entries still load.
- Files are written through a temp file everywhere, `config.json` included.

## 0.14.1 (2026-11-12)
- **Inside times fit at GUI scale 3.** The Session table's Inside column was too narrow for times of a minute or
  more, so "3m 20s" broke over two lines and ran into the rows around it. It's wide enough for "10m 00s" now. At
  scale 3 the # column makes room for it; scales 2 and 4 look the same as before.

## 0.14.0 (2026-11-12)
Coins, slice 1 of the money tracker (design in `Docs/money-design.md`, Nick's picks from 2026-11-12).
- **Every shaft gets a coin value**: mined gems, other drops (mob drops and the like from your Sacks), corpse
  loot, Suspicious Scrap and the crystal of a `_C` shaft, minus keys and campfires.
  - Gems count as Flawless (or Fine), sold by insta-sell (or sell offer), minus 1.125% Bazaar tax.
  - Keys cost their insta-buy price, a campfire costs Refined Umber + 16 Match-Sticks at insta-buy.
  - AH-only drops (Caged Wisp, Dead Man's Chestplate, ...) use the lowest BIN. Things with no market at all
    (soulbound mob drops) use the NPC sell price.
  - A crystal counts as Perfect minus 5 Flawless of its gem, or 0.
- **Chat summary** gets a Coins line, f.e. `Coins +5.7M (gems 11.0M, corpses 103.6k, keys -2.8M, campfires -2.6M)`.
- **Journal shaft view** shows Coins near the top and a Coins section with each part. Hover a part for its items.
- **Prices load** from the Hypixel Bazaar, a lowest BIN list and Hypixel's item list when you enter a shaft or
  open the journal, never on a timer. They're kept in `.minecraft/shaftjournal/prices.json` and `items.json`, so
  coins work offline with the last prices. Nothing about you is sent.
- **Each shaft saves the prices it was valued at**, so old shafts keep their coins when prices move. Shafts from
  before 0.14.0 use today's prices.
- **Settings** live in `.minecraft/shaftjournal/config.json` until there's a Settings screen: `sellPrice`
  (`insta_sell`, `sell_offer`), `gemTier` (`flawless`, `fine`), `bazaarTax` (in %), `oldShafts` (`that_day`,
  `today`) and `crystals` (`perfect_minus_flawless`, `zero`). Changes apply at the next shaft or journal open.
- Not yet: coin columns, Stats coins and HUD money values (slice 2). A key saved by Resourceful still counts as
  used, that message hasn't been recorded yet.

## 0.13.2 (2026-11-12)
Checked at GUI scale 2 on 1080p (960×540), what most SkyBlock players use, plus scales 3 and 4.
- **Journal tables use the width.** At scale 2 the Session and Sessions columns sat in the left half; now spare
  room is spread over the columns (up to twice their width) and the spawn bars grow with it.
- **Spawn chart bars are wider** (up to 24 px) with gaps that grow with them, so a few sessions fill the chart.
- **Session date fits at 480 wide.** "Yesterday 17:16 to 17:35" ran under the tabs and hid the ‹ button; it
  now drops "to 17:35" (or the time too) when there's no room.
- **Edit HUD panel grows on wide screens**: 30% of the width, between 204 and 280 px, so the add-a-line list
  shows full names ("Fine from corpses", "ShaftJournal version") at scale 2. Scales 3 and 4 stay the same.
  Changing the GUI scale with the panel open rebuilds it.
- **The panel is opaque**: a box under it no longer shows through its text.

## 0.13.1 (2026-11-12)
Fixes from a code review of 0.12.0 to 0.13.0, plus a Ping that works on Hypixel.
- **Ping works on Hypixel.** Hypixel puts a fixed 1 in the tab list for you, so Ping was stuck at 1ms. Now it
  times your block breaks and clicks: the game already sends those to the server and gets an answer back, the
  mod only measures the gap and sends nothing itself. It shows the middle of the last 10, updates while you
  mine and goes back to "-" a minute after you stop. It may read up to 50 ms high if Hypixel answers on its
  next tick. Still to check in game.
- **TPS waits again after every server switch**, also when the new server has the same dimension (that used
  to skip the 5 s wait).
- **Format tooltips show the unit**: "Shows as 42ms" on Ping, "/h" on per hour values.
- **Colour rules only take values that fit**: times against times, cold against cold, numbers against
  numbers. Anything else turns the field red, and a saved file with one falls back to the number. The
  tooltip only suggests `{average_spawn}` where it makes sense.
- Waiting dots for rates can't show up in full in the editor's labels anymore (only the HUD itself hides
  the dots that aren't shown yet).

## 0.13.0 (2026-11-12)
Nick's asks: game values, presets and clearer number formats.
- **New "Game" values** in the line picker and as tags: **FPS** (`{fps}`), **Ping** (`{ping}`, in ms),
  **TPS** (`{tps}`), **Clock** (`{clock}`, your computer's time in your system's 12h/24h style),
  **ShaftJournal version** (`{mod_version}`) and **Minecraft version** (`{minecraft_version}`).
  - Ping is the number the server puts in the tab list for you. Measuring it ourselves would mean sending
    packets, which the mod never does. It may update slowly or be off on Hypixel; still to check in game.
  - TPS times the "ping" packets Hypixel's anticheat sends once per server tick (SkyHanni's method). It
    waits 5 s after joining or switching servers, shows 0 when the server stops sending them, and "-" on
    servers that don't send them. Still to check in game.
- **"+ Preset" on the Edit HUD toolbar** adds a ready-made box: Spawn, Session or Current shaft (the
  defaults, f.e. to get a deleted one back), All-time totals (shafts, powder and Fine, all-time) or
  Performance (FPS, ping and TPS, shown everywhere). A preset goes to its own spot if that's free, or the
  first free spot otherwise. Hover an entry to see its lines.
- **Format buttons show format codes** instead of example numbers, spreadsheet style: `#,##0`, `0.0k`, `0`
  for numbers, `0.0`, `0`, `0.00` for decimals and `m:ss`, `m"m" s"s"`, `s"s"` for times. Hovering a
  button shows an example, f.e. "Shows as 43.4k, 1.2M". The "+ Value ▾" menu in custom text shows both.

## 0.12.2 (2026-11-12)
No more dot separators in the editor (Nick doesn't want them anywhere).
- The hint at the bottom of the editor uses commas.
- The "+ Value ▾" format menu's title is just the value's name, without its range.
- The custom text help lists each value's options with commas only.

## 0.12.1 (2026-11-12)
- The line page's breadcrumb no longer shows the range after the value's name. Nick doesn't want dot separators.

## 0.12.0 (2026-11-12)
Nick's asks after trying 0.11.2.
- **Colour rules can compare against another value.** Type a value like `{average_spawn}` in a rule's
  Over/Under field instead of a number, f.e. Spawn timer over `{average_spawn}`. Ranges work too
  (`{session_powder:all_time}`). The rule is skipped while that value isn't there yet. The field turns red
  when it's neither a number nor a value.
- **Breadcrumbs** on top of the side panel: **HUD › Spawn › Spawn timer**. Click "HUD" to close the panel,
  or the box name to go back to the box. The line page's title (with its range) now lives there.
- **Waiting dots don't change the box's width** anymore. The dots that aren't shown yet still take their
  space, so a box whose widest line is a waiting rate stays still.

## 0.11.2 (2026-11-12)
Fixes from a code review of 0.11.1, plus waiting dots for rates.
- **Per hour values that aren't ready yet** (Powder, Fine, Shafts per hour) show **. .. ...** counting up
  instead of "-". A rate needs 1 saved shaft and a session of at least 5 minutes. Nick's pick.
- The side panel keeps its full height, but **only the part with content is drawn and clickable**. Boxes
  under the empty part show through and can be clicked and dragged again.
- The **Custom text** heading is cut short when a wide font (Caxton) would push the Turn on/off button out of
  the panel.
- The pinned **custom text help** also keeps clear of a toolbar at the top of the screen.
- The line page shows when a line is **hidden**: a greyed preview with a "hidden" note.
- The **pencil** in the line list is drawn as pixels too, like the Turn on/off button.
- Icon buttons (undo, redo, colours, remove, Reset, Turn on/off, Delete box) have names for the narrator.
- The gap between an icon and its text is the same 4 px on every button.

## 0.11.1 (2026-11-12)
Edit HUD: Nick's tweaks after trying 0.11.0.
- The side panel always runs the **full height of the screen**, so it doesn't jump around with its content.
  The toolbar sits beside it and no longer covers the box you're editing.
- **No more folding** of the Lines and Box sections.
- **Box section:** Size sits above Background; the three Show in places are stacked under each other, lined
  up with the controls above; Labels and Values colours are on their own rows.
- **Custom text** turns on and off with a **✎ Turn on / Turn off** button next to its heading instead of a
  checkbox, and the note under it is gone (the button's tooltip explains it). **Start again from the line** is
  now **↻ Reset**. Both icons are drawn as pixels, so every font shows them the same.
- The line page no longer has its own **Show this line** box; the checkbox in the line list does that.
- **+ Add colour rule** is now **+ Colour rule**.

## 0.11.0 (2026-11-12)
Edit HUD on one page, bigger buttons, and tooltips that fit. Built from `Docs/mockups/hud-one-page.html`.
- **One page per box.** No more Lines and Box tabs: a box opens with its **Lines** on top and its **Box**
  settings under them. Click "▾ Lines" or "▾ Box" to fold a section away (remembered while the editor is
  open). **+ Add line** sits next to "Lines", so it's at the top even with many lines.
- **Bigger buttons:** 16 px for buttons that do something (toolbar, Back, Remove, + Add line, Delete box…),
  14 px for choices and line rows. Before, everything was 12 px, which looked squashed with resource packs
  that have a thick button border.
- **Colours are one button each** that shows the colour and opens a grid with bigger swatches and "Box
  colour", instead of 16 tiny swatches in a row. The picked colour has a yellow outline. A colour rule now
  fits on one row.
- **Delete box** is red with an ✕ icon (vanilla's own sprite, so Caxton can't break it). Line **Remove** and
  the rule ✕ use the same icon. **Reset** is now **Reset all**, set apart from Done.
- **Tooltips** wrap and always stay on screen. The tag help behind **?** fits at 480×270, splits each tag's
  options into ranges, then formats, and **clicking ?** keeps it open beside the panel while you type.
- **Custom text** (was "Free text"): the section says "(ADVANCED)", the box reads "Type this line with codes",
  and a short note explains what it's for before you tick it.
- **Line page:** Range is a 2×2 grid and Range/Format/Colour have row names again, so "All-time" no longer
  wraps onto its own row. The preview has no field border, so it doesn't look like a text field. Sections
  have dividers and white headings.
- **Line list:** rows show a "›" instead of a ✎ button (the whole row opens the line); custom-text lines get an
  aqua ✎. Checkbox labels have a gap and can be clicked too.
- **Dragging a line** near the top or bottom of the panel scrolls it, so lines can be moved past what's shown.
- **Esc** closes a menu, then the pinned help, then goes back from a line or the line picker, and only then
  closes the editor.
- "Already in this box" ticks in the line picker are green instead of nearly invisible.
- At 480×270 the toolbar drops its "Edit HUD" title when the panel needs the full height.

## 0.10.1 (2026-11-12)
Code review fixes for the Edit HUD lines v2.
- **Remove** no longer deletes more lines when Enter or Space is pressed after it. Clicked buttons and
  checkboxes in the side panel let go of the keyboard when the panel changes, so Enter or Space can't press
  them again (this also covers Reset and Delete box, which went past "Really?").
- **Insert menus:** picking a value or colour no longer selects text when the mouse moves during the click,
  so the next pick or keystroke can't delete part of the line. Clicks and the mouse wheel on a menu's headings
  or empty space stay in the menu instead of reaching the page below. The menu opens in the right place
  straight away and closes when the panel is scrolled.
- **Scroll position:** each page really keeps it now, also after clicking a choice or swatch, switching tabs
  or undoing (0.9.1 and 0.10.0 promised this, but the panel always went back to the top).
- **Clicks:** a double click no longer acts on the page it opened (a line, colour rule or range nobody
  picked), and right or middle clicks don't add or open lines any more.
- The mouse wheel scrolls the side panel over the free-text field too, unless the text itself needs scrolling.
- Undo or redo that brings back or moves lines goes back to the line list instead of showing another line.
- A label with `&` or `{` (like "R&D: ") looks the same after "Write this line yourself": free text gets `&&`
  and `&{` for a plain `&` and `{`.
- The format choices in "+ Value" show their own examples ("43.4k"), not the current value, which could look
  the same for all three.
- An insert that doesn't fit in the 256 characters is skipped with a note instead of typing half a tag.
- Pasting text with a line break keeps the cursor where you pasted.
- Line grips scrolled out of view no longer catch clicks meant for the toolbar.
- The line preview only measures its text when it changes.

## 0.10.0 (2026-11-12)
Edit HUD: lines are easier to change, without typing codes. Built from `Docs/mockups/hud-lines-v2.html`.
- **Lines and Box tabs.** A box opens on its lines; title, background, size, align, Show in, colours and
  Delete box moved to the Box tab.
- **Lines show how they look**, in their own colours, so two "Fine" lines with different ranges can be told
  apart. Click a line (or ✎) to open it. Hidden lines are grey. Free-text lines have an aqua ✎.
- **Line page:** a live preview, a **Label** field (the text before the value), range and format as buttons
  you can all see (formats show an example like "43.4k"), and a colour for the label and the value: "Box"
  follows the box's colours, or pick one of the 16. Colour rules are still there and beat the value colour.
  Remove sits at the top.
- **Add line** groups the values (Spawn, Session, This shaft), shows what each one looks like, ticks the
  ones already in the box, and opens the new line straight away. **+ New box** opens it too.
- **Free text** ("Write this line yourself") gets a bigger field that wraps, **+ Value** (value, then range,
  then format) and **Colour** menus that type the tag or code at the cursor, a red note for a tag it doesn't
  know, and the full tag list behind **?**. New code `&v` is the box's value colour; `&r` is the box's label
  colour (now with its bold). Free text started from a line uses `&v`, so it keeps following the box theme.
- Each page keeps its scroll position, and going back scrolls to the line you were on.
- Boxes that don't show where you are now get a light dim and a "hidden here" badge instead of a dark cover,
  so their text stays readable while you edit.
- On narrow screens (1080p auto GUI scale) the side panel leaves room at the top for the toolbar, so Done,
  Reset and + New box aren't covered.

## 0.9.3 (2026-11-12)
Comments cut down to the ones that matter (where a number comes from, gotchas), now keyword-short. Added
Spotless, which fixes unused imports, import order and trailing whitespace; `./gradlew build` fails until
`./gradlew spotlessApply` has run. No changes to how the mod works.

## 0.9.2 (2026-11-12)
Comments rewritten in a shorter, casual style. No changes to how the mod works.

## 0.9.1 (2026-11-12)
Code review fixes for HUD slices 2 and 3.
- **Colour rules on free-text lines** follow the range in the text: with `{session_powder:all_time}`, the rule
  looks at and colours the all-time powder. Before, it kept watching the hidden range and coloured nothing.
- **Time rules** compare the seconds the line shows, so "Over 1:30" no longer turns a "1:30" red.
- **Typing a rule's number:** "2,5" is 2.5 (a comma between groups of three, like 43,436, is still thousands),
  and odd input like "1-2:30" or "1e5" is ignored instead of read as something else.
- **Ctrl+Z while typing** in Edit HUD no longer leaves the removed text field active, which stopped changes
  from saving until the next click and swallowed arrow keys and typing.
- The side panel keeps its scroll position after clicking or dragging the box and after an undo, and no
  longer shows an empty band when it gets shorter.
- All-time totals only add the newly saved shaft instead of adding up the whole journal again.
- Cold is stored as a number, the same as the other values (it still shows as "-12❄").

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
