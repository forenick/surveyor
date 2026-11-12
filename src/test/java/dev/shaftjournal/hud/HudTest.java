package dev.shaftjournal.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.RecordingReplay;
import dev.shaftjournal.detection.ShaftEvent;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.hud.HudData.SessionTotals;
import dev.shaftjournal.hud.HudData.Totals;
import dev.shaftjournal.hud.HudLayout.Anchor;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.hud.HudValue.Kind;
import dev.shaftjournal.hud.HudValue.Range;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalFile;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.screen.ShaftNames;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HudTest {
	@Test
	void formatsEveryKind() {
		assertEquals("1:42", HudFormat.format(Kind.TIME, 0, 102_900L));
		assertEquals("1m 42s", HudFormat.format(Kind.TIME, 1, 102_000L));
		assertEquals("42s", HudFormat.format(Kind.TIME, 1, 42_000L));
		assertEquals("102s", HudFormat.format(Kind.TIME, 2, 102_000L));
		assertEquals("43,436", HudFormat.format(Kind.INTEGER, 0, 43_436L));
		assertEquals("43.4k", HudFormat.format(Kind.INTEGER, 1, 43_436L));
		assertEquals("1.2M", HudFormat.format(Kind.INTEGER, 1, 1_204_310L));
		assertEquals("999", HudFormat.format(Kind.INTEGER, 1, 999L));
		assertEquals("999.9k", HudFormat.format(Kind.INTEGER, 1, 999_949L));
		assertEquals("1.0M", HudFormat.format(Kind.INTEGER, 1, 999_950L), "not 1000.0k");
		assertEquals("43436", HudFormat.format(Kind.INTEGER, 2, 43_436L));
		assertEquals("19.4", HudFormat.format(Kind.DECIMAL, 0, 19.42));
		assertEquals("19", HudFormat.format(Kind.DECIMAL, 1, 19.42));
		assertEquals("19.42", HudFormat.format(Kind.DECIMAL, 2, 19.42));
		assertEquals("-", HudFormat.format(Kind.DECIMAL, 0, null));
		assertEquals("19.4", HudFormat.format(Kind.DECIMAL, 9, 19.42), "an unknown format falls back to the first");
	}

	@Test
	void defaultBoxesMatchTheMockup() {
		HudData example = HudData.EXAMPLE;
		List<Box> boxes = HudLayout.defaults().boxes;
		assertEquals(List.of("§e§lSession", "§7Shafts: §f6", "§7Shafts/h: §f19.4", "§7Powder: §f43,436", "§7Fine: §f15.6"),
				HudBoxText.lines(boxes.get(0), example, false));
		assertEquals(List.of("§7Spawn: §f§l1:42", "§7Avg: §f§l1:25"), HudBoxText.lines(boxes.get(1), example, false));
		assertEquals(List.of("§e§lJade Shaft", "§7Inside: §f2:14", "§7Corpses: §f2/4 Lapis, Umber", "§7Cold: §f-12❄",
				"§7Campfires: §f1"), HudBoxText.lines(boxes.get(2), example, false));
	}

	@Test
	void missingValuesShowADashOrTheExample() {
		Box spawn = HudLayout.defaults().boxes.get(1);
		HudData empty = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertEquals(List.of("§7Spawn: §f§l-", "§7Avg: §f§l-"), HudBoxText.lines(spawn, empty, false));
		assertEquals(List.of("§7Spawn: §f§l1:42", "§7Avg: §f§l1:25"), HudBoxText.lines(spawn, empty, true));
		assertEquals("Shaft", HudBoxText.tags("{shaft_type} Shaft", empty, false).strip(), "a missing tag is left out");
		Box session = HudLayout.defaults().boxes.get(0);
		assertEquals("§7Shafts: §f0", HudBoxText.lines(session, empty, false).get(1), "the HUD shows the real zero");
		assertEquals(List.of("§e§lSession", "§7Shafts: §f6", "§7Shafts/h: §f19.4", "§7Powder: §f43,436", "§7Fine: §f15.6"),
				HudBoxText.lines(session, empty, true), "the editor previews a whole example session, not 0 shafts at 19.4/h");
	}

	@Test
	void layoutSurvivesTheFile() {
		HudLayout layout = HudLayout.defaults();
		layout.boxes.get(0).labelColor = "b";
		layout.boxes.get(0).lines.get(2).shown = true;
		HudLayout read = HudLayout.fromJson(layout.toJson());
		assertEquals(layout.toJson(), read.toJson());
	}

	@Test
	void repairsAHandEditedFile() {
		HudLayout read = HudLayout.fromJson("""
				{"boxes": [{"id": "mine", "labelColor": "z", "scale": 9, "anchorX": "SIDEWAYS",
				  "lines": [{"value": "nope"}, {"value": "spawn_timer", "format": -5}]}, null]}""");
		Box box = read.boxes.getFirst();
		assertEquals(1, read.boxes.size());
		assertEquals("7", box.labelColor);
		assertEquals(2, box.scale);
		assertEquals(Anchor.START, box.anchorX, "an unknown anchor becomes the default");
		assertTrue(box.showInShaft && box.showInMines && !box.showElsewhere, "shown in the mines and shafts by default");
		assertEquals(1, box.lines.size());
		assertEquals("Spawn: ", box.lines.getFirst().prefix);
		assertEquals(0, box.lines.getFirst().format);
		assertTrue(HudLayout.fromJson("{}").boxes.isEmpty(), "deleting every box is allowed");
		assertThrows(JsonParseException.class, () -> HudLayout.fromJson("[]"));
		assertThrows(JsonParseException.class, () -> HudLayout.fromJson("{\"boxes\": null}"));
	}

	@Test
	void convertsVersion1Positions() {
		HudLayout read = HudLayout.fromJson("""
				{"version": 1, "boxes": [{"id": "a", "x": 1.0, "y": 0.0}, {"id": "b", "x": 0.5, "y": 0.58},
				  {"id": "c", "x": 0.0, "y": 1.0}, {"id": "d", "x": 0.25, "y": 0.9}]}""");
		List<Box> boxes = read.boxes;
		assertEquals(List.of(Anchor.END, Anchor.START), List.of(boxes.get(0).anchorX, boxes.get(0).anchorY));
		assertEquals(List.of(0, 0), List.of(boxes.get(0).offsetX, boxes.get(0).offsetY), "edges convert exactly");
		assertEquals(List.of(Anchor.MIDDLE, Anchor.MIDDLE), List.of(boxes.get(1).anchorX, boxes.get(1).anchorY));
		assertEquals(0, boxes.get(1).offsetX);
		assertEquals(21, boxes.get(1).offsetY, "8% of the 262 usable pixels below the middle");
		assertEquals(List.of(Anchor.START, Anchor.END), List.of(boxes.get(2).anchorX, boxes.get(2).anchorY));
		assertEquals(List.of(118, 26), List.of(boxes.get(3).offsetX, boxes.get(3).offsetY));
		assertEquals(HudLayout.VERSION, read.version);
		assertFalse(read.toJson().contains("\"x\""), "the old fields aren't written back");
	}

	@Test
	void anchorsPlaceBoxesFromTheirEdge() {
		assertEquals(4, Anchor.START.start(0, 60, 480));
		assertEquals(416, Anchor.END.start(0, 60, 480), "right edge on the margin");
		assertEquals(210, Anchor.MIDDLE.start(0, 60, 480));
		assertEquals(4, Anchor.START.start(-50, 60, 480), "kept inside the margin");
		for (Anchor anchor : Anchor.values()) {
			assertEquals(100, anchor.start(anchor.offset(100, 60, 480), 60, 480), anchor + " round trip");
		}
		assertEquals(416 + 60, Anchor.END.start(0, 60, 480) + 60);
		assertEquals(386 + 90, Anchor.END.start(0, 90, 480) + 90);
		assertEquals(Anchor.START, Anchor.forPosition(10, 60, 480));
		assertEquals(Anchor.MIDDLE, Anchor.forPosition(200, 60, 480));
		assertEquals(Anchor.END, Anchor.forPosition(400, 60, 480));
	}

	@Test
	void repairsMissingAndDuplicateIds() {
		HudLayout read = HudLayout.fromJson("{\"boxes\": [{\"id\": \"box1\"}, {}, {\"id\": \"box1\"}, {\"id\": \" \"}]}");
		assertEquals(List.of("box1", "box2", "box3", "box4"), read.boxes.stream().map(b -> b.id).toList());
	}

	@Test
	void visibility() {
		Box box = new Box();
		assertTrue(box.visible(true, false));
		assertTrue(box.visible(false, true));
		assertFalse(box.visible(false, false));
		box.showInMines = false;
		assertFalse(box.visible(true, false));
		assertTrue(box.visible(true, true), "a shaft counts as a shaft, not as the mines");
		box.showElsewhere = true;
		assertTrue(box.visible(false, false));
	}

	@Test
	void convertsVersion2Visibility() {
		HudLayout read = HudLayout.fromJson("""
				{"version": 2, "boxes": [{"id": "a", "visibility": "ALWAYS"}, {"id": "b", "visibility": "MINES"},
				  {"id": "c", "visibility": "IN_SHAFT"}, {"id": "d", "visibility": "OUTSIDE_SHAFT"}]}""");
		List<List<Boolean>> places = read.boxes.stream()
				.map(b -> List.of(b.showInShaft, b.showInMines, b.showElsewhere)).toList();
		assertEquals(List.of(List.of(true, true, true), List.of(true, true, false), List.of(true, false, false),
				List.of(false, true, false)), places);
		assertFalse(read.toJson().contains("visibility"), "the old field isn't written back");
	}

	@Test
	void movesLinesToAGap() {
		Box box = HudLayout.defaults().boxes.get(2);
		List<String> start = box.lines.stream().map(l -> l.value).toList();
		box.moveLine(0, 2);
		assertEquals(List.of(start.get(1), start.get(0), start.get(2), start.get(3)), box.lines.stream().map(l -> l.value).toList());
		box.moveLine(3, 0);
		assertEquals(List.of(start.get(3), start.get(1), start.get(0), start.get(2)), box.lines.stream().map(l -> l.value).toList());
		box.moveLine(1, 4);
		assertEquals(start.get(1), box.lines.getLast().value, "the gap below the last line");
		List<String> before = box.lines.stream().map(l -> l.value).toList();
		box.moveLine(1, 1);
		box.moveLine(1, 2);
		box.moveLine(0, 9);
		assertEquals(before, box.lines.stream().map(l -> l.value).toList(), "dropping on itself or out of range does nothing");
	}

	@Test
	void undoAndRedo() {
		HudHistory history = new HudHistory("a");
		assertFalse(history.canUndo());
		assertFalse(history.record("a"), "no change, no step");
		assertTrue(history.record("b"));
		history.record("c");
		assertEquals("b", history.undo());
		assertEquals("a", history.undo());
		assertNull(history.undo());
		assertEquals("b", history.redo());
		history.record("d");
		assertFalse(history.canRedo(), "a new change clears redo");
		assertEquals("b", history.undo());
		for (int i = 0; i < HudHistory.LIMIT + 10; i++) history.record("x" + i);
		int steps = 0;
		while (history.undo() != null) steps++;
		assertEquals(HudHistory.LIMIT, steps);
	}

	@Test
	void corpseSummary() {
		assertNull(HudData.corpses(List.of()));
		assertEquals("2/4 Lapis, Umber", HudData.corpses(List.of(new Corpse("Umber", false), new Corpse("Lapis", true),
				new Corpse("Lapis", true), new Corpse("Umber", false))));
	}

	// nicks real session 2026-11-11
	@Test
	void sessionValuesFromTheRealJournal() throws Exception {
		List<JournalEntry> entries = realJournal();
		HudData data = live(entries, 0);
		assertEquals(6L, data.get(HudValue.SESSION_SHAFTS));
		assertEquals(43_436L, data.get(HudValue.SESSION_POWDER));
		assertEquals("19.4", HudFormat.format(Kind.DECIMAL, 0, data.get(HudValue.SHAFTS_PER_HOUR)));
		// (8,889+55,940+38,186+193,848+283,594+185,948) / 6 ms
		assertEquals("2:07", HudFormat.format(Kind.TIME, 0, data.get(HudValue.AVERAGE_SPAWN)));
		assertNull(data.get(HudValue.SHAFT_TIME));
		// rate counts time since last shaft too, 2x time = half rate
		long start = entries.getFirst().sessionId();
		long length = new JournalStats.SessionSummary(start, entries).lengthMillis();
		HudData later = live(entries, start + 2 * length);
		assertEquals("9.7", HudFormat.format(Kind.DECIMAL, 0, later.get(HudValue.SHAFTS_PER_HOUR)));
	}

	@Test
	void timeRanges() throws Exception {
		List<JournalEntry> entries = realJournal();
		long start = entries.getFirst().sessionId();
		long end = new JournalStats.SessionSummary(start, entries).end();
		HudData data = live(entries, end);
		assertEquals(6L, data.get(HudValue.SESSION_SHAFTS, Range.ALL_TIME));
		assertEquals(43_436L, data.get(HudValue.SESSION_POWDER, Range.ALL_TIME));
		JournalEntry last = entries.getLast();
		assertEquals(JournalStats.powder(last), data.get(HudValue.SESSION_POWDER, Range.SHAFT), "the last shaft outside one");
		double hours = 6 / (Double) data.get(HudValue.SHAFTS_PER_HOUR);
		assertEquals(Math.round(43_436 / hours), data.get(HudValue.SESSION_POWDER, Range.PER_HOUR));
		assertEquals((Double) data.get(HudValue.SESSION_CORPSE_FINE) / hours,
				(Double) data.get(HudValue.SESSION_CORPSE_FINE, Range.PER_HOUR), 1e-9);
		assertEquals(data.get(HudValue.SESSION_SHAFTS), data.get(HudValue.SESSION_SHAFTS, Range.PER_HOUR),
				"shafts have no per hour range (shafts per hour is its own value), so the default shows");

		Box box = new Box();
		Line perHour = new Line(HudValue.SESSION_POWDER);
		perHour.range = Range.PER_HOUR;
		perHour.format = 1;
		box.lines.add(perHour);
		Line campfires = new Line(HudValue.SHAFT_CAMPFIRES);
		box.lines.add(campfires);
		assertNull(campfires.range, "values that aren't counted over shafts have no range");
		assertEquals(List.of("§7Powder: §f137.2k/h", "§7Campfires: §f1"), HudBoxText.lines(box, HudData.EXAMPLE, false));
		HudData empty = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertEquals(List.of("§7Powder: §f.", "§7Campfires: §f-"), HudBoxText.lines(box, empty, false),
				"a rate thats not ready yet shows waiting dots, no /h");
		HudData later = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 1_200, ShaftNames.defaults());
		assertEquals("§7Powder: §f...", HudBoxText.lines(box, later, false).getFirst(), "the dots count up over time");
		assertEquals(List.of(".", "..", "...", "."), List.of(HudBoxText.waiting(0), HudBoxText.waiting(600),
				HudBoxText.waiting(1_200), HudBoxText.waiting(1_800)));
		perHour.range = Range.ALL_TIME;
		assertEquals("§7Powder: §f0", HudBoxText.lines(box, empty, false).getFirst(), "the HUD shows the real zero");
		assertEquals("§7Powder: §f1.2M", HudBoxText.lines(box, empty, true).getFirst(), "the editor shows the example");
	}

	@Test
	void linesGetARangeWhenRead() {
		HudLayout read = HudLayout.fromJson("""
				{"version": 3, "boxes": [{"id": "a", "lines": [{"value": "session_powder"},
				  {"value": "session_shafts", "range": "SHAFT"}, {"value": "session_corpse_fine", "range": "ALL_TIME"},
				  {"value": "spawn_timer", "range": "SESSION"}, {"value": "session_powder", "range": "SOMETIME"}]}]}""");
		List<Range> ranges = read.boxes.getFirst().lines.stream().map(l -> l.range).toList();
		assertEquals(java.util.Arrays.asList(Range.SESSION, Range.SESSION, Range.ALL_TIME, null, Range.SESSION), ranges,
				"old lines were session values; a range the value doesn't have, or none at all, falls back");
		assertEquals(read.toJson(), HudLayout.fromJson(read.toJson()).toJson());
	}

	private static List<JournalEntry> realJournal() throws Exception {
		Path path = Path.of(HudTest.class.getResource("/journal-2026-11-11.jsonl").toURI());
		JournalFile file = new JournalFile(path);
		List<JournalEntry> entries = file.load().join();
		file.close();
		return entries;
	}

	private static HudData live(List<JournalEntry> entries, long now) {
		TextPatterns patterns = TextPatterns.defaults();
		return HudData.live(SessionTotals.of(entries, patterns), Totals.of(entries, patterns),
				Totals.of(List.of(entries.getLast()), patterns), null, null, now, ShaftNames.defaults());
	}

	@Test
	void freeText() {
		Box box = HudLayout.defaults().boxes.get(1);
		Line spawn = box.lines.getFirst();
		assertEquals("Spawn: &v{spawn_timer}", HudBoxText.basicText(box, spawn));
		spawn.advanced = true;
		spawn.text = "Spawn &8» &e{spawn_timer} &7(avg {average_spawn:seconds}) {nope} {spawn_timer:all_time} &lB&aG &rx";
		assertEquals("§7Spawn §8» §e1:42 §7(avg 85s) {nope} {spawn_timer:all_time} §lB§aG §r§7x",
				HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"plain text starts in the label colour; unknown tags and options stay as typed; a colour ends bold");
		Line powder = new Line(HudValue.SESSION_POWDER);
		powder.range = Range.ALL_TIME;
		powder.format = 1;
		assertEquals("Powder: &v{session_powder:all_time:short}", HudBoxText.basicText(new Box(), powder));
		powder.advanced = true;
		powder.text = "{session_powder:per_hour} / {session_powder:shaft:plain}";
		Box one = new Box();
		one.lines.add(powder);
		assertEquals("§7137,166/h / 7120", HudBoxText.lines(one, HudData.EXAMPLE, false).getFirst());
		assertEquals("Jade 1.2M", HudBoxText.tags("{shaft_type} {session_powder:all_time:short}", HudData.EXAMPLE, false),
				"titles take options too");
	}

	@Test
	void lineColours() {
		Box box = HudLayout.defaults().boxes.get(1);
		Line spawn = box.lines.getFirst();
		spawn.labelColor = "b";
		spawn.valueColor = "a";
		spawn.prefix = "Next: ";
		assertEquals("§bNext: §a§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		spawn.readyRule = true;
		assertEquals("§bNext: §c§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "a rule beats the line colour");
		spawn.readyRule = false;
		spawn.valueColor = null;
		assertEquals("§bNext: §f§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "no own colour = box colour");
		spawn.shown = false;
		assertEquals("§bNext: §f§l1:42", HudBoxText.line(box, spawn, HudData.EXAMPLE, false), "hidden lines still render for the editor");
	}

	@Test
	void basicTextLooksTheSame() {
		for (Box box : HudLayout.defaults().boxes) {
			box.lines.getFirst().labelColor = "6";
			// typed labels w/ & and { stay as typed
			box.lines.getLast().prefix = "R&D &&{spawn_timer} {x: ";
			for (Line line : box.lines) {
				String basic = HudBoxText.line(box, line, HudData.EXAMPLE, false);
				line.text = HudBoxText.basicText(box, line);
				line.advanced = true;
				assertEquals(visible(basic), visible(HudBoxText.line(box, line, HudData.EXAMPLE, false)),
						"free text starts out looking like the line: " + line.text);
			}
		}
	}

	// each char w/ its colour + bold, so §7§6a == §6a
	private static String visible(String text) {
		StringBuilder out = new StringBuilder();
		char color = 'f';
		boolean bold = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				char code = text.charAt(++i);
				if (code == 'l') bold = true;
				else if (code != 'r') {
					color = code;
					bold = false;
				}
				continue;
			}
			out.append(c).append(color).append(bold ? 'B' : '.');
		}
		return out.toString();
	}

	@Test
	void freeTextFollowsTheBox() {
		Box box = new Box();
		Line powder = new Line(HudValue.SESSION_POWDER);
		powder.text = HudBoxText.basicText(box, powder);
		powder.advanced = true;
		box.lines.add(powder);
		box.labelColor = "e";
		box.valueColor = "b";
		box.valueBold = true;
		assertEquals("§ePowder: §b§l43,436", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"&v and the start follow the box theme later on");
		powder.text = "&aX &rY &vZ";
		assertEquals("§e§aX §r§eY §b§lZ", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		powder.text = "R&&D &{spawn_timer} &a!";
		assertEquals("§eR&D {spawn_timer} §a!", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
	}

	@Test
	void unknownTags() {
		assertEquals(List.of("{gems_hr}", "{session_powder:weekly}", "{Spawn}"),
				HudBoxText.unknownTags("{spawn_timer} {gems_hr} {session_powder:weekly} {session_powder:per_hour:short} {Spawn}"));
		assertEquals(List.of(), HudBoxText.unknownTags("no tags { here"));
		assertEquals(List.of("{nope}"), HudBoxText.unknownTags("&{gems_hr} &&{nope}"), "&{ is a plain {, && a plain &");
	}

	@Test
	void colourRules() {
		Box box = HudLayout.defaults().boxes.get(1);
		Line spawn = box.lines.getFirst();
		assertEquals("§7Spawn: §f§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		spawn.readyRule = true;
		assertEquals("§7Spawn: §c§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"1:42 is above the 1:25 average");
		HudLayout.ColorRule fast = new HudLayout.ColorRule();
		fast.when = HudLayout.ColorRule.When.UNDER;
		fast.value = 120;
		fast.color = "a";
		spawn.rules = new java.util.ArrayList<>(List.of(fast));
		assertEquals("§7Spawn: §a§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"own thresholds come first; times compare in seconds");
		spawn.advanced = true;
		spawn.text = "&8» &e&l{spawn_timer} &7avg {average_spawn} {spawn_timer:seconds}";
		assertEquals("§7§8» §e§l§a§l1:42§e§l §7avg 1:25 §a102s§7", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"the line's own value takes the rule's colour in any format, the rest keeps its colour");

		Line cold = new Line(HudValue.SHAFT_COLD);
		cold.readyRule = true;
		HudData data = HudData.EXAMPLE;
		assertNull(HudColors.color(cold, data, false), "-12 is fine");
		assertEquals(-12.0, data.number(HudValue.SHAFT_COLD, null, false));
		Line campfires = new Line(HudValue.SHAFT_CAMPFIRES);
		campfires.readyRule = true;
		assertNull(HudColors.color(campfires, data, false), "no ready-made rule for campfires");
		HudData empty = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertNull(HudColors.color(spawn, empty, false), "nothing to compare");
	}

	@Test
	void coldThresholds() {
		Line cold = new Line(HudValue.SHAFT_COLD);
		cold.readyRule = true;
		for (int[] c : new int[][] {{-49, 0}, {-50, 'e'}, {-79, 'e'}, {-80, 'c'}, {-99, 'c'}}) {
			HudData data = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
					.cold(c[0]);
			assertEquals(c[1] == 0 ? null : String.valueOf((char) c[1]), HudColors.color(cold, data, false), "cold " + c[0]);
		}
	}

	@Test
	void thresholdsAsTyped() {
		assertEquals(90.0, HudFormat.parseThreshold(Kind.TIME, "1:30"));
		assertEquals(90.0, HudFormat.parseThreshold(Kind.TIME, " 90 "));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "1:3"));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "1:75"));
		assertEquals(-50.0, HudFormat.parseThreshold(Kind.TEXT, "-50"));
		assertEquals(43436.0, HudFormat.parseThreshold(Kind.INTEGER, "43,436"));
		assertNull(HudFormat.parseThreshold(Kind.DECIMAL, "abc"));
		assertEquals("1:30", HudFormat.thresholdText(Kind.TIME, 90));
		assertEquals("-50", HudFormat.thresholdText(Kind.TEXT, -50));
		assertEquals("2.5", HudFormat.thresholdText(Kind.DECIMAL, 2.5));
	}

	@Test
	void colourSettingsAreRepaired() {
		HudLayout read = HudLayout.fromJson("""
				{"boxes": [{"id": "a", "lines": [
				  {"value": "spawn_timer", "readyRule": true, "rules": [null, {"when": "SIDEWAYS", "value": 90, "color": "z"}]},
				  {"value": "shaft_corpses", "readyRule": true, "rules": [{"value": 1}]},
				  {"value": "shaft_cold", "advanced": true}]}]}""");
		List<Line> lines = read.boxes.getFirst().lines;
		assertTrue(lines.get(0).readyRule);
		assertEquals(1, lines.get(0).rules.size());
		assertEquals(HudLayout.ColorRule.When.OVER, lines.get(0).rules.getFirst().when);
		assertEquals("c", lines.get(0).rules.getFirst().color);
		assertFalse(lines.get(1).readyRule, "corpses have no ready-made rule");
		assertNull(lines.get(1).rules, "corpses aren't a number");
		assertFalse(lines.get(2).advanced, "free text without text falls back to the basic line");
		assertEquals(read.toJson(), HudLayout.fromJson(read.toJson()).toJson());
	}

	@Test
	void freeTextRuleFollowsTheRangeInItsText() {
		Line powder = new Line(HudValue.SESSION_POWDER);
		HudLayout.ColorRule big = new HudLayout.ColorRule();
		big.value = 1_000_000;
		powder.rules = new java.util.ArrayList<>(List.of(big));
		Box box = new Box();
		box.lines.add(powder);
		assertEquals("§7Powder: §f43,436", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "the session is under 1M");
		powder.advanced = true;
		powder.text = "Powder {session_powder:all_time} ({session_powder})";
		assertEquals(Range.ALL_TIME, HudBoxText.ruleRange(powder));
		assertEquals("§7Powder §c1,204,310§7 (43,436)", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"the first tag of the line's value sets the range the rule looks at and colours");
		powder.text = "Powder {shaft_type}";
		assertEquals(Range.SESSION, HudBoxText.ruleRange(powder), "no tag of its own: the line's range");
	}

	@Test
	void timeRulesCompareTheShownSeconds() {
		Line spawn = new Line(HudValue.SPAWN_TIMER);
		HudLayout.ColorRule slow = new HudLayout.ColorRule();
		slow.value = 90;
		spawn.rules = new java.util.ArrayList<>(List.of(slow));
		HudData at90 = HudData.live(SessionTotals.NONE, Totals.NONE, null, 90_900L, null, 0, ShaftNames.defaults());
		assertNull(HudColors.color(spawn, at90, false), "1:30.9 shows as 1:30, which isn't over 1:30");
		HudData at91 = HudData.live(SessionTotals.NONE, Totals.NONE, null, 91_000L, null, 0, ShaftNames.defaults());
		assertEquals("c", HudColors.color(spawn, at91, false));
	}

	@Test
	void coldIsANumber() {
		HudData data = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults()).cold(-12);
		assertEquals("-12❄", HudBoxText.text(HudValue.SHAFT_COLD, null, 0, data, false));
		assertEquals(-12.0, data.number(HudValue.SHAFT_COLD, null, false));
		assertNull(data.number(HudValue.SHAFT_TYPE, null, true), "text has no number");
	}

	@Test
	void thresholdCommasAndMinus() {
		assertEquals(2.5, HudFormat.parseThreshold(Kind.DECIMAL, "2,5"), "a lone comma is a decimal point");
		assertEquals(1500.0, HudFormat.parseThreshold(Kind.INTEGER, "1,500"), "groups of three are thousands");
		assertEquals(1_204_310.0, HudFormat.parseThreshold(Kind.INTEGER, "1,204,310"));
		assertNull(HudFormat.parseThreshold(Kind.INTEGER, "1,2,3"));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "1-2:30"));
		assertEquals(-30.0, HudFormat.parseThreshold(Kind.TIME, "-0:30"));
		assertNull(HudFormat.parseThreshold(Kind.DECIMAL, "1e5"));
		assertNull(HudFormat.parseThreshold(Kind.DECIMAL, "NaN"));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "99999999999999999999:00"));
		assertEquals(0.5, HudFormat.parseThreshold(Kind.DECIMAL, ".5"));
	}

	@Test
	void totalsAddUp() {
		assertEquals(new Totals(3, 30, 1.5), new Totals(1, 10, 0.5).plus(new Totals(2, 20, 1)));
	}

	// recording 3
	@Test
	void shaftTotalsMatchTheSavedEntry() throws Exception {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_14-13-53.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		TextPatterns patterns = TextPatterns.defaults();
		List<Totals> inside = new java.util.ArrayList<>();
		for (ShaftEvent event : RecordingReplay.replay(recording)) {
			if (!(event instanceof ShaftEvent.ShaftLeft left)) continue;
			JournalEntry entry = JournalEntry.of(left.shaft(), 0, null, patterns);
			inside.add(Totals.of(left.shaft(), patterns));
			assertEquals(Totals.of(List.of(entry), patterns), inside.getLast());
		}
		assertEquals(2, inside.size());
		assertEquals(5759 + 4791 + 1510, inside.getFirst().powder(), "the Aquamarine shaft: corpses plus the exit bonus");
	}
}
