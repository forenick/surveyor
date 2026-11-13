package dev.skysurveyor.journal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.HudData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalFileTest {
	// lines 12 and 4 of old-saves/0.22.0-alpha.10/journal.jsonl
	private static final String JADE = "{\"version\":1,\"sessionId\":1794174082851,\"shaftId\":\"JADE_1\",\"enteredAt\":1794174160153,"
			+ "\"durationMillis\":7296,\"exit\":\"BYE\",\"exitPowder\":1000,\"searchMillis\":36069,\"corpses\":[{\"type\":\"Umber\","
			+ "\"looted\":false},{\"type\":\"Tungsten\",\"looted\":false},{\"type\":\"Tungsten\",\"looted\":false},{\"type\":\"Tungsten\","
			+ "\"looted\":false}],\"extraCorpses\":1,\"loots\":[],\"mayhemBuffs\":[\"Cold Resistance\"],\"campfiresAt\":[],\"coldAtExit\":0,"
			+ "\"scrapFound\":0,\"pristineProcs\":0,\"pristineItems\":{},\"sacksAdded\":{},\"sacksRemoved\":{},\"crystals\":[],"
			+ "\"otherDrops\":{},\"prices\":{},\"campfireColds\":[]}";
	private static final String TOPAZ = "{\"version\":1,\"sessionId\":1794174082851,\"shaftId\":\"TOPA_1\",\"enteredAt\":1794174760566,"
			+ "\"durationMillis\":6753,\"exit\":\"BYE\",\"exitPowder\":1000,\"searchMillis\":21840,\"corpses\":[{\"type\":\"Lapis\","
			+ "\"looted\":false},{\"type\":\"Umber\",\"looted\":false},{\"type\":\"Tungsten\",\"looted\":false}],\"extraCorpses\":1,"
			+ "\"loots\":[],\"mayhemBuffs\":[\"Suspicious Scrap chance\"],\"campfiresAt\":[],\"coldAtExit\":0,\"scrapFound\":0,"
			+ "\"pristineProcs\":0,\"pristineItems\":{},\"sacksAdded\":{},\"sacksRemoved\":{},\"crystals\":[],\"otherDrops\":{},"
			+ "\"prices\":{},\"campfireColds\":[]}";
	// line 1 of old-saves/0.2.x/journal.jsonl, oldest format
	private static final String PERIDOT_0_2 = "{\"version\":1,\"sessionId\":1794413789542,\"shaftId\":\"PERI_1\",\"enteredAt\":1794413802805,"
			+ "\"durationMillis\":6709,\"exit\":\"BYE\",\"exitPowder\":1000,\"searchMillis\":8889,\"corpses\":[{\"type\":\"Umber\","
			+ "\"looted\":false},{\"type\":\"Umber\",\"looted\":false},{\"type\":\"Umber\",\"looted\":false}],\"extraCorpses\":0,"
			+ "\"loots\":[],\"mayhemBuffs\":[\"Mining Fortune\"],\"campfiresAt\":[],\"coldAtExit\":0,\"scrapFound\":0,\"pristineProcs\":0,"
			+ "\"pristineItems\":{},\"sacksAdded\":{},\"sacksRemoved\":{}}";

	@Test
	void linesMissingFieldsAreSkippedAndKept(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		// topaz line cut by hand after exitPowder
		String cut = TOPAZ.substring(0, TOPAZ.indexOf(",\"searchMillis\"")) + "}";
		String noExit = JADE.replace("\"exit\":\"BYE\",", "");
		String text = String.join("\n", JADE, "{}", cut, "{\"corpses\":null,\"loots\":[]}", noExit, PERIDOT_0_2, TOPAZ) + "\n";
		Files.writeString(path, text, StandardCharsets.UTF_8);

		List<JournalEntry> entries = new JournalFile(path).read();
		assertEquals(List.of("JADE_1", "PERI_1", "TOPA_1"), entries.stream().map(JournalEntry::shaftId).toList());
		// what crashed before: hud refresh + journal open
		JournalStats.allTime(entries);
		HudData.Totals.of(entries, TextPatterns.defaults());
		assertEquals(text, Files.readString(path, StandardCharsets.UTF_8), "read never changes the file");
	}

	@Test
	void badUtf8OnlyLosesItsOwnLine(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		// write died inside a ❄ (e2 9d 84)
		byte[] torn = {'{', '"', 'x', '"', ':', '"', (byte) 0xE2, (byte) 0x9D};
		byte[] jade = (JADE + "\n").getBytes(StandardCharsets.UTF_8);
		byte[] topaz = ("\n" + TOPAZ + "\n").getBytes(StandardCharsets.UTF_8);
		byte[] all = new byte[jade.length + torn.length + topaz.length];
		System.arraycopy(jade, 0, all, 0, jade.length);
		System.arraycopy(torn, 0, all, jade.length, torn.length);
		System.arraycopy(topaz, 0, all, jade.length + torn.length, topaz.length);
		Files.write(path, all);

		assertEquals(List.of("JADE_1", "TOPA_1"), new JournalFile(path).read().stream().map(JournalEntry::shaftId).toList());
	}

	@Test
	void appendAfterCutOffLineStartsNewLine(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("journal.jsonl");
		// write died halfway through the topaz line
		String torn = JADE + "\n" + TOPAZ.substring(0, 300);
		Files.writeString(path, torn, StandardCharsets.UTF_8);

		JournalFile file = new JournalFile(path);
		JournalEntry next = file.read().get(0).withPrices(null);
		file.append(EntryBuilder.from(next).shaftId("AMBE_1").enteredAt(1794175000000L).build());
		file.close();

		assertEquals(List.of("JADE_1", "AMBE_1"), file.read().stream().map(JournalEntry::shaftId).toList());
		String now = Files.readString(path, StandardCharsets.UTF_8);
		assertTrue(now.startsWith(torn + "\n{"), "cut off line stays, next shaft on its own line");
		assertTrue(now.endsWith("}\n"));
	}

	@Test
	void appendAddsNoBlankLines(@TempDir Path dir) throws IOException {
		Path path = dir.resolve("skysurveyor").resolve("journal.jsonl");
		JournalFile file = new JournalFile(path);
		file.append(EntryBuilder.entry().shaftId("JADE_1").build());
		file.append(EntryBuilder.entry().shaftId("TOPA_1").build());
		file.close();

		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		assertEquals(2, lines.size());
		assertTrue(lines.stream().noneMatch(String::isBlank));
		assertEquals(2, file.read().size());
	}
}
