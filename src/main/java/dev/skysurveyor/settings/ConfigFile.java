package dev.skysurveyor.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.util.SafeWrite;

// config.json, a ConfigSection per object in it. memory set from any thread, file io thread only, hand edits picked up by mtime
final class ConfigFile {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final AtomicReference<Settings> memory = new AtomicReference<>(Settings.DEFAULTS);
	private volatile String saveError;
	private long loadedMtime = Long.MIN_VALUE;

	ConfigFile(Path folder) {
		this.file = folder.resolve("config.json");
	}

	Settings get() {
		return memory.get();
	}

	// any thread, so the page + journal see it before the save ran. one section only, the others keep what another thread just set
	<T> void set(ConfigSection<T> section, T value) {
		memory.updateAndGet(settings -> section.in(settings, value));
	}

	// null = last save worked
	String saveError() {
		return saveError;
	}

	// changed keys only, hand edits not loaded yet (or broken) + unknown keys stay.
	// never sets memory, a queued older save would undo a newer one
	<T> void save(ConfigSection<T> section, T before, T after) {
		JsonObject old = GSON.toJsonTree(before).getAsJsonObject();
		save(section.key(), part -> GSON.toJsonTree(after).getAsJsonObject().entrySet().forEach(e -> {
			if (!Objects.equals(old.get(e.getKey()), e.getValue())) part.add(e.getKey(), e.getValue());
		}));
	}

	// rewrites the file, other values kept but json comments + layout arent
	private void save(String key, Consumer<JsonObject> edit) {
		try {
			JsonObject json = Files.exists(file)
					? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
					// memory, so a first save keeps page changes made before it
					: json(memory.get());
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

	private static JsonObject json(Settings settings) {
		JsonObject json = new JsonObject();
		for (ConfigSection<?> section : ConfigSection.ALL) json.add(section.key(), GSON.toJsonTree(section.of(settings)));
		return json;
	}

	void reloadIfChanged() {
		try {
			// deleted = start over
			if (!Files.exists(file)) {
				memory.set(Settings.DEFAULTS);
				SafeWrite.write(file, GSON.toJson(json(memory.get())));
			}
			long mtime = Files.getLastModifiedTime(file).toMillis();
			if (mtime == loadedMtime) return;
			loadedMtime = mtime;
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			// one by one, a broken section keeps its last values, the others still load
			for (ConfigSection<?> section : ConfigSection.ALL) load(section, json);
			// fixed by hand, next save should work
			saveError = null;
		} catch (IOException | RuntimeException e) {
			// broken hand edit, keep the last good settings + never overwrite their file
			LOGGER.warn("Could not read {}: {}", file, e.toString());
		}
	}

	private <T> void load(ConfigSection<T> section, JsonObject json) {
		try {
			T loaded = json.has(section.key()) ? section.read().apply(json.get(section.key())) : null;
			set(section, loaded == null ? section.of(Settings.DEFAULTS) : loaded);
		} catch (RuntimeException e) {
			LOGGER.warn("Could not read \"{}\" in {}: {}", section.key(), file, e.toString());
		}
	}
}
