package dev.skysurveyor.money;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.util.SafeWrite;

// "money" + "journal" in config.json. memory set from any thread, file io thread only, hand edits picked up by mtime
final class MoneyConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private volatile MoneySettings settings = MoneySettings.DEFAULTS;
	private volatile boolean byDay = LiveMoney.BY_DAY_DEFAULT;
	private volatile String saveError;
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

	// any thread, so the page + journal see it before the save ran
	void settings(MoneySettings value) {
		settings = value;
	}

	// null = last save worked
	String saveError() {
		return saveError;
	}

	// never sets memory, a queued older save would undo a newer one
	void saveByDay(boolean value) {
		save("journal", journal -> journal.addProperty("byDay", value));
	}

	// changed keys only, hand edits not loaded yet (or broken) + unknown keys stay
	void saveMoney(MoneySettings before, MoneySettings after) {
		JsonObject old = GSON.toJsonTree(before).getAsJsonObject();
		save("money", money -> GSON.toJsonTree(after).getAsJsonObject().entrySet().forEach(e -> {
			if (!Objects.equals(old.get(e.getKey()), e.getValue())) money.add(e.getKey(), e.getValue());
		}));
	}

	// rewrites the file, other values kept but json comments + layout arent
	private void save(String key, Consumer<JsonObject> edit) {
		try {
			JsonObject json = Files.exists(file)
					? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
					: defaults();
			JsonObject part = json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
			edit.accept(part);
			json.add(key, part);
			// loadedMtime stays, so the next reload picks up other edits made before this
			SafeWrite.write(file, GSON.toJson(json));
			saveError = null;
		} catch (IOException | RuntimeException e) {
			// broken hand edit, never overwrite it
			LOGGER.warn("Could not save {}: {}", file, e.toString());
			saveError = e.toString();
		}
	}

	// memory, so a first save keeps page changes made before it
	private JsonObject defaults() {
		return file(settings, byDay);
	}

	private static JsonObject file(MoneySettings money, boolean byDay) {
		JsonObject json = new JsonObject();
		json.add("money", GSON.toJsonTree(money));
		JsonObject journal = new JsonObject();
		journal.addProperty("byDay", byDay);
		json.add("journal", journal);
		return json;
	}

	void reloadIfChanged() {
		try {
			// deleted = start over
			if (!Files.exists(file)) {
				settings = MoneySettings.DEFAULTS;
				byDay = LiveMoney.BY_DAY_DEFAULT;
				SafeWrite.write(file, GSON.toJson(file(settings, byDay)));
			}
			long mtime = Files.getLastModifiedTime(file).toMillis();
			if (mtime == loadedMtime) return;
			loadedMtime = mtime;
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			MoneySettings loaded = json.has("money") ? GSON.fromJson(json.get("money"), MoneySettings.class) : null;
			settings = loaded == null ? MoneySettings.DEFAULTS : loaded.repaired();
			JsonObject journal = json.has("journal") && json.get("journal").isJsonObject() ? json.getAsJsonObject("journal") : null;
			boolean saved = journal != null && journal.has("byDay") && journal.get("byDay").isJsonPrimitive();
			byDay = saved ? journal.get("byDay").getAsBoolean() : LiveMoney.BY_DAY_DEFAULT;
			// fixed by hand, next save should work
			saveError = null;
		} catch (IOException | RuntimeException e) {
			// broken hand edit, keep the last good settings + never overwrite their file
			LOGGER.warn("Could not read {}: {}", file, e.toString());
		}
	}
}
