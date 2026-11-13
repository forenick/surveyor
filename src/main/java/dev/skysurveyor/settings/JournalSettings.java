package dev.skysurveyor.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// "journal" in config.json, names never rename
public record JournalSettings(boolean byDay) {
	// journal groups sessions by day, nicks pick (2026-11-12)
	public static final JournalSettings DEFAULTS = new JournalSettings(true);

	// null = default. by hand not gson, a weird byDay is the default, never a failed load
	static JournalSettings read(JsonElement json) {
		if (!json.isJsonObject()) return null;
		JsonObject journal = json.getAsJsonObject();
		boolean saved = journal.has("byDay") && journal.get("byDay").isJsonPrimitive();
		return saved ? new JournalSettings(journal.get("byDay").getAsBoolean()) : null;
	}

	public JournalSettings withByDay(boolean value) {
		return new JournalSettings(value);
	}
}
