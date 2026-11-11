package dev.shaftjournal.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParseException;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.hud.HudData.SessionTotals;
import dev.shaftjournal.hud.HudLayout.Anchor;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Visibility;
import dev.shaftjournal.hud.HudValue.Kind;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalFile;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.screen.ShaftNames;

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
		HudData empty = HudData.live(SessionTotals.NONE, null, null, 0, ShaftNames.defaults());
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
		assertEquals(Visibility.MINES, box.visibility);
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
		// A 60 px box on a 480 px screen.
		assertEquals(4, Anchor.START.start(0, 60, 480));
		assertEquals(416, Anchor.END.start(0, 60, 480), "right edge on the margin");
		assertEquals(210, Anchor.MIDDLE.start(0, 60, 480));
		assertEquals(4, Anchor.START.start(-50, 60, 480), "kept inside the margin");
		for (Anchor anchor : Anchor.values()) {
			assertEquals(100, anchor.start(anchor.offset(100, 60, 480), 60, 480), anchor + " round trip");
		}
		// The anchored edge stays put when the text gets wider.
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
		assertTrue(Visibility.MINES.visible(true, false));
		assertTrue(Visibility.MINES.visible(false, true));
		assertFalse(Visibility.MINES.visible(false, false));
		assertFalse(Visibility.IN_SHAFT.visible(true, false));
		assertFalse(Visibility.OUTSIDE_SHAFT.visible(true, true));
		assertTrue(Visibility.OUTSIDE_SHAFT.visible(true, false));
		assertTrue(Visibility.ALWAYS.visible(false, false));
	}

	@Test
	void corpseSummary() {
		assertNull(HudData.corpses(List.of()));
		assertEquals("2/4 Lapis, Umber", HudData.corpses(List.of(new Corpse("Umber", false), new Corpse("Lapis", true),
				new Corpse("Lapis", true), new Corpse("Umber", false))));
	}

	/** Nick's real session from 2026-11-11, the same numbers the journal screen shows. */
	@Test
	void sessionValuesFromTheRealJournal() throws Exception {
		Path path = Path.of(HudTest.class.getResource("/journal-2026-11-11.jsonl").toURI());
		JournalFile file = new JournalFile(path);
		List<JournalEntry> entries = file.load().join();
		file.close();
		HudData data = HudData.live(SessionTotals.of(entries, TextPatterns.defaults()), null, null, 0, ShaftNames.defaults());
		assertEquals(6L, data.get(HudValue.SESSION_SHAFTS));
		assertEquals(43_436L, data.get(HudValue.SESSION_POWDER));
		assertEquals("19.4", HudFormat.format(Kind.DECIMAL, 0, data.get(HudValue.SHAFTS_PER_HOUR)));
		// (8,889 + 55,940 + 38,186 + 193,848 + 283,594 + 185,948) / 6 ms
		assertEquals("2:07", HudFormat.format(Kind.TIME, 0, data.get(HudValue.AVERAGE_SPAWN)));
		assertNull(data.get(HudValue.SHAFT_TIME));
		// The rate counts the time since the last shaft too: the same 6 shafts over twice the time.
		long start = entries.getFirst().sessionId();
		long length = new JournalStats.SessionSummary(start, entries).lengthMillis();
		HudData later = HudData.live(SessionTotals.of(entries, TextPatterns.defaults()), null, null, start + 2 * length,
				ShaftNames.defaults());
		assertEquals("9.7", HudFormat.format(Kind.DECIMAL, 0, later.get(HudValue.SHAFTS_PER_HOUR)));
	}
}
