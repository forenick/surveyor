package dev.shaftjournal.money;

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

import dev.shaftjournal.OldSaves;

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

			MoneyConfig config = new MoneyConfig(tmp);
			config.reloadIfChanged();
			assertEquals(before, Files.readString(file), "loading never rewrites it");
			OldSaves.assertKept(old.get("money"), new Gson().toJsonTree(config.settings()), "money", Map.of());

			boolean byDay = !config.byDay();
			config.saveByDay(byDay);
			JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			old.remove("journal");
			OldSaves.assertKept(old, saved, "config", Map.of());

			MoneyConfig reloaded = new MoneyConfig(tmp);
			reloaded.reloadIfChanged();
			assertEquals(byDay, reloaded.byDay(), "by day comes back");
			assertEquals(config.settings(), reloaded.settings());

			// settings page save keeps journal + any other keys
			MoneySettings changed = config.settings().withGemTier(MoneySettings.GemTier.FINE).withBazaarTax(1.25);
			config.saveMoney(config.settings(), changed);
			JsonObject afterMoney = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			JsonObject expected = saved.deepCopy();
			expected.remove("money");
			JsonObject withoutMoney = afterMoney.deepCopy();
			withoutMoney.remove("money");
			assertEquals(expected, withoutMoney, "only money changes");
			MoneyConfig again = new MoneyConfig(tmp);
			again.reloadIfChanged();
			assertEquals(changed, again.settings(), "money comes back");
			assertEquals(byDay, again.byDay());
		}));
	}

	@Test
	void savingMoneyKeepsUnknownKeys(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\", \"later\": 5}, \"other\": {\"x\": 1}}");
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		config.saveMoney(config.settings(), config.settings().withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals(5, json.getAsJsonObject("money").get("later").getAsInt());
		assertEquals("sell_offer", json.getAsJsonObject("money").get("sellPrice").getAsString());
		assertEquals("fine", json.getAsJsonObject("money").get("gemTier").getAsString());
		assertEquals(1, json.getAsJsonObject("other").get("x").getAsInt());
	}

	// no file yet: saving by day keeps money changed on the page before
	@Test
	void firstSaveKeepsMemorySettings(@TempDir Path tmp) throws Exception {
		MoneyConfig config = new MoneyConfig(tmp);
		config.settings(MoneySettings.DEFAULTS.withCrystals(MoneySettings.Crystals.ZERO));
		config.saveByDay(false);
		MoneyConfig reloaded = new MoneyConfig(tmp);
		reloaded.reloadIfChanged();
		assertEquals(MoneySettings.Crystals.ZERO, reloaded.settings().crystals());
		assertFalse(reloaded.byDay());
	}

	@Test
	void byDayFollowsTheFile(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"journal\": {\"byDay\": false}}");
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		assertFalse(config.byDay());
		Files.writeString(file, "{\"journal\": {\"byDay\": \"oops\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		config.reloadIfChanged();
		assertFalse(config.byDay(), "only a real true turns it on");
	}

	// a money edit made right before toggling by day still loads
	@Test
	void byDayDoesNotSwallowAnEdit(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.settings().gemTier());
		Files.writeString(file, "{\"money\": {\"gemTier\": \"flawless\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(2_000));
		config.saveByDay(false);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FLAWLESS, config.settings().gemTier());
	}

	private static JsonObject money(Path file) throws Exception {
		return JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("money");
	}

	// hand edit made after the last load, page saves another setting
	@Test
	void pageSaveKeepsHandEditNotLoadedYet(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		MoneySettings before = config.settings();
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		json.getAsJsonObject("money").addProperty("crystals", "zero");
		Files.writeString(file, json.toString());
		config.saveMoney(before, before.withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		assertEquals("zero", money(file).get("crystals").getAsString());
		assertEquals("sell_offer", money(file).get("sellPrice").getAsString());
	}

	// gson cant read "1,5", load fails, a page save still leaves it
	@Test
	void pageSaveKeepsBrokenHandEdit(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"bazaarTax\": \"1,5\", \"gemTier\": \"fine\"}}");
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.settings(), "load failed, defaults kept");
		config.saveMoney(config.settings(), config.settings().withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		assertEquals("1,5", money(file).get("bazaarTax").getAsString());
		assertEquals("fine", money(file).get("gemTier").getAsString());
		assertEquals("sell_offer", money(file).get("sellPrice").getAsString());
	}

	// queued saves run after memory moved on, they must not set it back
	@Test
	void savesNeverTouchMemory(@TempDir Path tmp) throws Exception {
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		MoneySettings a = MoneySettings.DEFAULTS.withGemTier(MoneySettings.GemTier.FINE);
		MoneySettings b = a.withBazaarTax(2);
		config.settings(b);
		config.byDay(false);
		config.saveMoney(MoneySettings.DEFAULTS, a);
		config.saveByDay(true);
		assertEquals(b, config.settings());
		assertFalse(config.byDay());
		config.saveMoney(a, b);
		config.saveByDay(false);
		MoneyConfig reloaded = new MoneyConfig(tmp);
		reloaded.reloadIfChanged();
		assertEquals(b, reloaded.settings());
		assertFalse(reloaded.byDay());
	}

	@Test
	void deletedFileBringsBackDefaults(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\", \"bazaarTax\": 2}, \"journal\": {\"byDay\": false}}");
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.settings().gemTier());
		Files.delete(file);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.settings());
		assertEquals(LiveMoney.BY_DAY_DEFAULT, config.byDay());
		MoneyConfig reloaded = new MoneyConfig(tmp);
		reloaded.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, reloaded.settings(), "file written w/ defaults");
	}

	@Test
	void failedSaveIsReported(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\",}");
		MoneyConfig config = new MoneyConfig(tmp);
		config.reloadIfChanged();
		String broken = Files.readString(file);
		config.saveMoney(config.settings(), config.settings().withCrystals(MoneySettings.Crystals.ZERO));
		assertNotNull(config.saveError());
		assertEquals(broken, Files.readString(file), "never overwritten");
		Files.writeString(file, "{}");
		config.saveByDay(false);
		assertNull(config.saveError());
		assertTrue(Files.readString(file).contains("byDay"));
	}
}
