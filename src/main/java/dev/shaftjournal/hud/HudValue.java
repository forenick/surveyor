package dev.shaftjournal.hud;

import java.util.List;
import java.util.Locale;

// ids saved in hud.json + tags, never rename
public enum HudValue {
	SESSION_SHAFTS("session_shafts", "Shafts", Kind.INTEGER, "Shafts: ", Range.SESSION, Range.ALL_TIME),
	SHAFTS_PER_HOUR("shafts_per_hour", "Shafts per hour", Kind.DECIMAL, "Shafts/h: "),
	AVERAGE_SPAWN("average_spawn", "Average spawn", Kind.TIME, "Avg spawn: "),
	SESSION_POWDER("session_powder", "Powder", Kind.INTEGER, "Powder: ", Range.values()),
	SESSION_CORPSE_FINE("session_corpse_fine", "Fine from corpses", Kind.DECIMAL, "Fine: ", Range.values()),
	SPAWN_TIMER("spawn_timer", "Spawn timer", Kind.TIME, "Spawn: "),
	SHAFT_TYPE("shaft_type", "Shaft type", Kind.TEXT, "Shaft: "),
	SHAFT_TIME("shaft_time", "Time inside", Kind.TIME, "Inside: "),
	SHAFT_CORPSES("shaft_corpses", "Corpses", Kind.TEXT, "Corpses: "),
	SHAFT_COLD("shaft_cold", "Cold", Kind.COLD, "Cold: "),
	SHAFT_CAMPFIRES("shaft_campfires", "Campfires", Kind.INTEGER, "Campfires: ");

	// names saved in hud.json, never rename
	public enum Range {
		// or the last one after leaving
		SHAFT("Shaft"),
		SESSION("Session"),
		PER_HOUR("Per hour"),
		ALL_TIME("All-time");

		private final String displayName;

		Range(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

		public String tagName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Range byTagName(String name) {
			for (Range range : values()) {
				if (range.tagName().equals(name)) return range;
			}
			return null;
		}
	}

	public enum Kind {
		// ms, Long
		TIME,
		INTEGER,
		DECIMAL,
		COLD,
		TEXT
	}

	private final String id;
	private final String displayName;
	private final Kind kind;
	private final String defaultPrefix;
	private final List<Range> ranges;

	HudValue(String id, String displayName, Kind kind, String defaultPrefix, Range... ranges) {
		this.id = id;
		this.displayName = displayName;
		this.kind = kind;
		this.defaultPrefix = defaultPrefix;
		this.ranges = List.of(ranges);
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public Kind kind() {
		return kind;
	}

	public String defaultPrefix() {
		return defaultPrefix;
	}

	public List<Range> ranges() {
		return ranges;
	}

	public boolean hasRange(Range range) {
		return range != null && ranges.contains(range);
	}

	// session so pre-range lines + tags still show the same
	public Range defaultRange() {
		return hasRange(Range.SESSION) ? Range.SESSION : ranges.isEmpty() ? null : ranges.getFirst();
	}

	public boolean hasNumber() {
		return kind != Kind.TEXT;
	}

	public static HudValue byId(String id) {
		for (HudValue value : values()) {
			if (value.id.equals(id)) return value;
		}
		return null;
	}
}
