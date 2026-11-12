package dev.shaftjournal.money;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.util.SafeWrite;

// "money" + "journal" in config.json. io thread only, hand edits picked up by mtime
final class MoneyConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private volatile MoneySettings settings = MoneySettings.DEFAULTS;
	// journal groups sessions by day, nicks pick (2026-11-12)
	private volatile boolean byDay = true;
	private long loadedMtime = Long.MIN_VALUE;

	MoneyConfig(Path folder) {
		this.file = folder.resolve("config.json");
	}

	MoneySettings settings() {
		return settings;
	}

	boolean byDay() {
		return byDay;
	}

	// any thread, so a reopened journal sees it before the save ran
	void byDay(boolean value) {
		byDay = value;
	}

	// rewrites only "journal", rest of the file stays as typed
	void saveByDay(boolean value) {
		byDay = value;
		try {
			JsonObject json = Files.exists(file)
					? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
					: defaults();
			JsonObject journal = json.has("journal") && json.get("journal").isJsonObject() ? json.getAsJsonObject("journal") : new JsonObject();
			journal.addProperty("byDay", value);
			json.add("journal", journal);
			SafeWrite.write(file, GSON.toJson(json));
			loadedMtime = Files.getLastModifiedTime(file).toMillis();
		} catch (IOException | RuntimeException e) {
			// broken hand edit, never overwrite it
			LOGGER.warn("Could not save {}: {}", file, e.toString());
		}
	}

	private JsonObject defaults() {
		JsonObject json = new JsonObject();
		json.add("money", GSON.toJsonTree(MoneySettings.DEFAULTS));
		JsonObject journal = new JsonObject();
		journal.addProperty("byDay", byDay);
		json.add("journal", journal);
		return json;
	}

	void reloadIfChanged() {
		try {
			if (!Files.exists(file)) SafeWrite.write(file, GSON.toJson(defaults()));
			long mtime = Files.getLastModifiedTime(file).toMillis();
			if (mtime == loadedMtime) return;
			loadedMtime = mtime;
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			MoneySettings loaded = json.has("money") ? GSON.fromJson(json.get("money"), MoneySettings.class) : null;
			settings = loaded == null ? MoneySettings.DEFAULTS : loaded.repaired();
			JsonObject journal = json.has("journal") && json.get("journal").isJsonObject() ? json.getAsJsonObject("journal") : null;
			byDay = journal == null || !journal.has("byDay") || !journal.get("byDay").isJsonPrimitive() || journal.get("byDay").getAsBoolean();
		} catch (IOException | RuntimeException e) {
			// broken hand edit, keep the last good settings + never overwrite their file
			LOGGER.warn("Could not read {}: {}", file, e.toString());
		}
	}
}
