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

// "money" in config.json. io thread only, hand edits picked up by mtime
final class MoneyConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private volatile MoneySettings settings = MoneySettings.DEFAULTS;
	private long loadedMtime = Long.MIN_VALUE;

	MoneyConfig(Path folder) {
		this.file = folder.resolve("config.json");
	}

	MoneySettings settings() {
		return settings;
	}

	void reloadIfChanged() {
		try {
			if (!Files.exists(file)) {
				JsonObject json = new JsonObject();
				json.add("money", GSON.toJsonTree(MoneySettings.DEFAULTS));
				SafeWrite.write(file, GSON.toJson(json));
			}
			long mtime = Files.getLastModifiedTime(file).toMillis();
			if (mtime == loadedMtime) return;
			loadedMtime = mtime;
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			MoneySettings loaded = json.has("money") ? GSON.fromJson(json.get("money"), MoneySettings.class) : null;
			settings = loaded == null ? MoneySettings.DEFAULTS : loaded.repaired();
		} catch (IOException | RuntimeException e) {
			// broken hand edit, keep the last good settings + never overwrite their file
			LOGGER.warn("Could not read {}: {}", file, e.toString());
		}
	}
}
