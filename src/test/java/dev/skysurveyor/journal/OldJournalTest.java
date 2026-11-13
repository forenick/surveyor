package dev.skysurveyor.journal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalStats.AllTime;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.ItemPrice;
import dev.skysurveyor.money.MoneySettings;
import dev.skysurveyor.money.ShaftValue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OldJournalTest {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	@TestFactory
	Stream<DynamicTest> everyOldLineLoadsWithNothingLost() {
		return OldSaves.withFile("journal.jsonl").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			List<String> lines = Files.readAllLines(dir.resolve("journal.jsonl"), StandardCharsets.UTF_8).stream()
					.filter(line -> !line.isBlank()).toList();
			List<JournalEntry> entries = new JournalFile(dir.resolve("journal.jsonl")).read();
			assertEquals(lines.size(), entries.size(), "every shaft loads");
			for (int i = 0; i < lines.size(); i++) {
				JournalEntry entry = entries.get(i);
				String where = "line[" + (i + 1) + "]";
				assertNotNull(entry, where);
				JsonObject old = JsonParser.parseString(lines.get(i)).getAsJsonObject();
				OldSaves.assertKept(old, GSON.toJsonTree(entry), where, CONVERTED);
				if (old.has("prices")) assertPricesKept(old.getAsJsonObject("prices"), entry.prices(), where);
			}
		}));
	}

	// 0.14.0 + 0.14.1 wrote {"buyOrder": ..}, now [buyOrder, sellOffer, lowestBin, npcSell]
	private static final Map<String, Set<String>> CONVERTED = Map.of("line[]", Set.of("prices"));
	private static final String[] PRICE_FIELDS = {"buyOrder", "sellOffer", "lowestBin", "npcSell"};

	private static void assertPricesKept(JsonObject old, Map<String, ItemPrice> now, String where) {
		assertEquals(old.keySet(), now.keySet(), where + " prices lost or gained items");
		old.entrySet().forEach(e -> {
			ItemPrice price = now.get(e.getKey());
			Double[] values = {price.buyOrder(), price.sellOffer(), price.lowestBin(), price.npcSell()};
			for (int i = 0; i < PRICE_FIELDS.length; i++) {
				JsonElement value = e.getValue().isJsonObject()
						? e.getValue().getAsJsonObject().get(PRICE_FIELDS[i])
						: i < e.getValue().getAsJsonArray().size() ? e.getValue().getAsJsonArray().get(i) : null;
				Double expected = value == null || value.isJsonNull() ? null : value.getAsDouble();
				assertEquals(expected, values[i], where + ".prices." + e.getKey() + "." + PRICE_FIELDS[i]);
			}
		});
	}

	@TestFactory
	Stream<DynamicTest> oldShaftsShowTheSameNumbers() {
		return OldSaves.withFile("journal.jsonl").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			List<JournalEntry> entries = new JournalFile(dir.resolve("journal.jsonl")).read();
			OldSaves.assertExpected(dir, "expected-journal.txt", numbers(entries, settings(dir)));
		}));
	}

	@Test
	void newShaftsAppendBehindOldOnes(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("journal.jsonl");
		Files.copy(OldSaves.newest("journal.jsonl").resolve("journal.jsonl"), file);
		byte[] before = Files.readAllBytes(file);
		JournalFile journal = new JournalFile(file);
		List<JournalEntry> old = journal.load().get();
		journal.append(old.get(old.size() - 1));
		journal.close();
		byte[] after = Files.readAllBytes(file);
		byte[] start = new byte[before.length];
		System.arraycopy(after, 0, start, 0, Math.min(after.length, before.length));
		assertArrayEquals(before, start, "old lines untouched");
		assertEquals(old.size() + 1, new JournalFile(file).read().size());
	}

	@Test
	void brokenLinesAreSkippedButKept(@TempDir Path tmp) throws IOException {
		Path file = tmp.resolve("journal.jsonl");
		String good = Files.readAllLines(OldSaves.newest("journal.jsonl").resolve("journal.jsonl")).get(0);
		String text = "null\n{\"version\":1,\"corpses\":[{\"ty\n" + good + "\n\n";
		Files.writeString(file, text);
		assertEquals(1, new JournalFile(file).read().size());
		assertEquals(text, Files.readString(file));
	}

	private static MoneySettings settings(Path dir) {
		Path config = dir.resolve("config.json");
		if (!Files.exists(config)) return MoneySettings.DEFAULTS;
		JsonObject json = JsonParser.parseString(OldSaves.read(config)).getAsJsonObject();
		return new Gson().fromJson(json.get("money"), MoneySettings.class).repaired();
	}

	// saved prices only, todays change every day
	private static String numbers(List<JournalEntry> entries, MoneySettings settings) {
		ItemIds ids = ItemIds.bundled(Map.of());
		TextPatterns patterns = TextPatterns.defaults();
		AllTime all = JournalStats.allTime(entries);
		StringBuilder out = new StringBuilder();
		out.append("shafts ").append(all.shafts()).append('\n');
		out.append("sessions ").append(all.sessions()).append('\n');
		out.append("time ").append(all.timeMillis()).append('\n');
		out.append("powder ").append(all.powder()).append('\n');
		out.append("looted ").append(all.looted()).append('\n');
		out.append("seen ").append(all.corpsesSeen()).append('\n');
		out.append("types ").append(all.types()).append('\n');
		out.append("average spawn ").append(all.averageSearchMillis()).append('\n');
		double total = 0;
		StringBuilder shafts = new StringBuilder();
		for (JournalEntry entry : entries) {
			double coins = ShaftValue.of(entry, ShaftValue.layered(entry.prices(), id -> null), settings, ids, patterns).net();
			total += coins;
			shafts.append(String.format(Locale.ROOT, "%d %s %s powder=%d corpses=%s gems=%s coins=%.1f%n", entry.enteredAt(),
					entry.shaftId(), entry.exit(), JournalStats.powder(entry), JournalStats.corpses(entry),
					entry.gems(), coins));
		}
		out.append(String.format(Locale.ROOT, "coins %.1f%n", total));
		return out.append(shafts).toString();
	}
}
