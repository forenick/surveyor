package dev.skysurveyor.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.OldSaves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OldConfigTest {
	@TestFactory
	Stream<DynamicTest> oldSettingsLoadAndSurviveSaves() {
		return OldSaves.withFile("config.json").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			Path tmp = Files.createTempDirectory("old-config");
			Path file = tmp.resolve("config.json");
			Files.copy(dir.resolve("config.json"), file);
			String before = Files.readString(file);
			JsonObject old = JsonParser.parseString(before).getAsJsonObject();

			ConfigFile config = new ConfigFile(tmp);
			config.reloadIfChanged();
			assertEquals(before, Files.readString(file), "loading never rewrites it");
			OldSaves.assertKept(old.get("money"), new Gson().toJsonTree(config.get().money()), "money", Map.of());

			boolean byDay = !byDay(config);
			saveByDay(config, byDay);
			JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			old.remove("journal");
			OldSaves.assertKept(old, saved, "config", Map.of());

			ConfigFile reloaded = new ConfigFile(tmp);
			reloaded.reloadIfChanged();
			assertEquals(byDay, byDay(reloaded), "by day comes back");
			assertEquals(config.get().money(), reloaded.get().money());

			// settings page save keeps journal + any other keys
			MoneySettings changed = config.get().money().withGemTier(MoneySettings.GemTier.FINE).withBazaarTax(1.25);
			config.save(ConfigSection.MONEY, config.get().money(), changed);
			JsonObject afterMoney = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			JsonObject expected = saved.deepCopy();
			expected.remove("money");
			JsonObject withoutMoney = afterMoney.deepCopy();
			withoutMoney.remove("money");
			assertEquals(expected, withoutMoney, "only money changes");
			ConfigFile again = new ConfigFile(tmp);
			again.reloadIfChanged();
			assertEquals(changed, again.get().money(), "money comes back");
			assertEquals(byDay, byDay(again));
		}));
	}

	@Test
	void savingMoneyKeepsUnknownKeys(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\", \"later\": 5}, \"other\": {\"x\": 1}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		config.save(ConfigSection.MONEY, config.get().money(), config.get().money().withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals(5, json.getAsJsonObject("money").get("later").getAsInt());
		assertEquals("sell_offer", json.getAsJsonObject("money").get("sellPrice").getAsString());
		assertEquals("fine", json.getAsJsonObject("money").get("gemTier").getAsString());
		assertEquals(1, json.getAsJsonObject("other").get("x").getAsInt());
	}

	// no file yet: saving by day keeps money changed on the page before
	@Test
	void firstSaveKeepsMemorySettings(@TempDir Path tmp) throws Exception {
		ConfigFile config = new ConfigFile(tmp);
		config.set(ConfigSection.MONEY, MoneySettings.DEFAULTS.withCrystals(MoneySettings.Crystals.ZERO));
		saveByDay(config, false);
		ConfigFile reloaded = new ConfigFile(tmp);
		reloaded.reloadIfChanged();
		assertEquals(MoneySettings.Crystals.ZERO, reloaded.get().money().crystals());
		assertFalse(byDay(reloaded));
	}

	@Test
	void byDayFollowsTheFile(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"journal\": {\"byDay\": false}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertFalse(byDay(config));
		Files.writeString(file, "{\"journal\": {\"byDay\": \"oops\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		config.reloadIfChanged();
		assertFalse(byDay(config), "only a real true turns it on");
	}

	// a money edit made right before toggling by day still loads
	@Test
	void byDayDoesNotSwallowAnEdit(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.get().money().gemTier());
		Files.writeString(file, "{\"money\": {\"gemTier\": \"flawless\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(2_000));
		saveByDay(config, false);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FLAWLESS, config.get().money().gemTier());
	}

	private static boolean byDay(ConfigFile config) {
		return config.get().journal().byDay();
	}

	// the page only saves a by day that changed
	private static void saveByDay(ConfigFile config, boolean value) {
		config.save(ConfigSection.JOURNAL, new JournalSettings(!value), new JournalSettings(value));
	}

	private static JsonObject money(Path file) throws Exception {
		return JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("money");
	}

	// hand edit made after the last load, page saves another setting
	@Test
	void pageSaveKeepsHandEditNotLoadedYet(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		MoneySettings before = config.get().money();
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		json.getAsJsonObject("money").addProperty("crystals", "zero");
		Files.writeString(file, json.toString());
		config.save(ConfigSection.MONEY, before, before.withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		assertEquals("zero", money(file).get("crystals").getAsString());
		assertEquals("sell_offer", money(file).get("sellPrice").getAsString());
	}

	// gson cant read "1,5", load fails, a page save still leaves it
	@Test
	void pageSaveKeepsBrokenHandEdit(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"bazaarTax\": \"1,5\", \"gemTier\": \"fine\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.get().money(), "load failed, defaults kept");
		config.save(ConfigSection.MONEY, config.get().money(), config.get().money().withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		assertEquals("1,5", money(file).get("bazaarTax").getAsString());
		assertEquals("fine", money(file).get("gemTier").getAsString());
		assertEquals("sell_offer", money(file).get("sellPrice").getAsString());
	}

	// queued saves run after memory moved on, they must not set it back
	@Test
	void savesNeverTouchMemory(@TempDir Path tmp) throws Exception {
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		MoneySettings a = MoneySettings.DEFAULTS.withGemTier(MoneySettings.GemTier.FINE);
		MoneySettings b = a.withBazaarTax(2);
		config.set(ConfigSection.MONEY, b);
		config.set(ConfigSection.JOURNAL, new JournalSettings(false));
		config.save(ConfigSection.MONEY, MoneySettings.DEFAULTS, a);
		saveByDay(config, true);
		assertEquals(b, config.get().money());
		assertFalse(byDay(config));
		config.save(ConfigSection.MONEY, a, b);
		saveByDay(config, false);
		ConfigFile reloaded = new ConfigFile(tmp);
		reloaded.reloadIfChanged();
		assertEquals(b, reloaded.get().money());
		assertFalse(byDay(reloaded));
	}

	@Test
	void deletedFileBringsBackDefaults(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\", \"bazaarTax\": 2}, \"journal\": {\"byDay\": false}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.get().money().gemTier());
		Files.delete(file);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.get().money());
		assertEquals(JournalSettings.DEFAULTS.byDay(), byDay(config));
		ConfigFile reloaded = new ConfigFile(tmp);
		reloaded.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, reloaded.get().money(), "file written w/ defaults");
	}

	@Test
	void failedSaveIsReported(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\",}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		String broken = Files.readString(file);
		config.save(ConfigSection.MONEY, config.get().money(), config.get().money().withCrystals(MoneySettings.Crystals.ZERO));
		assertNotNull(config.saveError());
		assertEquals(broken, Files.readString(file), "never overwritten");
		Files.writeString(file, "{}");
		saveByDay(config, false);
		assertNull(config.saveError());
		assertTrue(Files.readString(file).contains("byDay"));
	}

	@Test
	void summaryLoadsRepairsAndSavesOnlyChanges(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"summary\": {\"show\": \"both\", \"seconds\": 900, \"later\": 1}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		SummarySettings loaded = config.get().summary();
		assertEquals(SummarySettings.Show.BOTH, loaded.show());
		assertEquals(SummarySettings.DEFAULTS.seconds(), loaded.seconds(), "out of range = default");
		assertEquals(SummarySettings.Style.VANILLA, loaded.style());
		config.save(ConfigSection.SUMMARY, loaded, loaded.withPosition(SummarySettings.Position.MOVABLE));
		JsonObject summary = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("summary");
		assertEquals("movable", summary.get("position").getAsString());
		assertEquals(900, summary.get("seconds").getAsInt(), "unchanged hand edit stays");
		assertEquals(1, summary.get("later").getAsInt());
		assertFalse(summary.has("style"));
	}

	// what 0.22.0-alpha.15 wrote on a first start, key order + layout
	@Test
	void newFileLooksTheSame(@TempDir Path tmp) throws Exception {
		new ConfigFile(tmp).reloadIfChanged();
		assertEquals("""
				{
				  "money": {
				    "sellPrice": "insta_sell",
				    "gemTier": "flawless",
				    "bazaarTax": 1.125,
				    "oldShafts": "that_day",
				    "crystals": "perfect_minus_flawless",
				    "ironman": false
				  },
				  "journal": {
				    "byDay": true
				  },
				  "summary": {
				    "show": "toast",
				    "style": "vanilla",
				    "position": "top_right",
				    "seconds": 5.0
				  }
				}""", Files.readString(tmp.resolve("config.json")));
	}

	// a section gson cant read keeps its last values, the others still load
	@Test
	void brokenMoneyStillLoadsJournalAndSummary(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"),
				"{\"money\": {\"ironman\": 1}, \"journal\": {\"byDay\": false}, \"summary\": {\"show\": \"chat\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.get().money());
		assertFalse(byDay(config));
		assertEquals(SummarySettings.Show.CHAT, config.get().summary().show());
	}

	@Test
	void brokenSummaryKeepsMoneyAndJournal(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"),
				"{\"money\": {\"gemTier\": \"fine\"}, \"journal\": {\"byDay\": false}, \"summary\": {\"seconds\": \"1,5\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.get().money().gemTier());
		assertFalse(byDay(config));
		assertEquals(SummarySettings.DEFAULTS, config.get().summary());
	}

	// by day is read by hand, anything odd is the default + the rest still loads
	@Test
	void oddJournalNeverFailsTheLoad(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		ConfigFile config = new ConfigFile(tmp);
		long time = 1_000;
		for (String journal : new String[] {"5", "[]", "null", "{}", "{\"byDay\": null}", "{\"byDay\": []}", "{\"byDay\": {}}"}) {
			Files.writeString(file, "{\"journal\": " + journal + ", \"summary\": {\"show\": \"chat\"}}");
			Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1_000));
			config.set(ConfigSection.JOURNAL, new JournalSettings(false));
			config.set(ConfigSection.SUMMARY, SummarySettings.DEFAULTS);
			config.reloadIfChanged();
			assertTrue(byDay(config), journal);
			assertEquals(SummarySettings.Show.CHAT, config.get().summary().show(), journal);
		}
		for (String off : new String[] {"1", "\"yes\"", "false"}) {
			Files.writeString(file, "{\"journal\": {\"byDay\": " + off + "}}");
			Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1_000));
			config.reloadIfChanged();
			assertFalse(byDay(config), off);
		}
		Files.writeString(file, "{\"journal\": {\"byDay\": \"true\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1_000));
		config.reloadIfChanged();
		assertTrue(byDay(config));
	}

	@Test
	void noSummaryInOldFiles(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"), "{\"money\": {\"gemTier\": \"fine\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(SummarySettings.DEFAULTS, config.get().summary());
		assertTrue(config.get().summary().show().toast());
		assertFalse(config.get().summary().show().chat());
	}
}
