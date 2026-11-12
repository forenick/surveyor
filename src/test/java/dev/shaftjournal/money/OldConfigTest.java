package dev.shaftjournal.money;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import dev.shaftjournal.OldSaves;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

			config.saveByDay(!config.byDay());
			JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			old.remove("journal");
			OldSaves.assertKept(old, saved, "config", Map.of());
		}));
	}
}
