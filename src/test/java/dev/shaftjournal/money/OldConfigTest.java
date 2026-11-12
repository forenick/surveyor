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
		}));
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
}
