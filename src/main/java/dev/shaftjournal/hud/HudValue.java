package dev.shaftjournal.hud;

import java.util.List;
import java.util.Locale;

// every value a HUD line can show. the ID is what the config file and (later) text tags use,
// so never rename one. plain java
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

	// which shafts a countable value adds up. the config file stores the name so never rename one
	public enum Range {
		// the shaft ur in, or the last one after u left it
		SHAFT("Shaft"),
		SESSION("Session"),
		// the session's amount per hour of session, counted up to now
		PER_HOUR("Per hour"),
		ALL_TIME("All-time");

		private final String displayName;

		Range(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

		// the name in free text tags, f.e. "all_time" in {session_powder:all_time}
		public String tagName() {
			return name().toLowerCase(Locale.ROOT);
		}

		// the range w/ this tag name, or null
		public static Range byTagName(String name) {
			for (Range range : values()) {
				if (range.tagName().equals(name)) return range;
			}
			return null;
		}
	}

	// how a value is stored and which number formats it has
	public enum Kind {
		// milliseconds, as a Long
		TIME,
		// a Long
		INTEGER,
		// a Double
		DECIMAL,
		// cold as a Long. shown w/ ❄ like the scoreboard, f.e. "-12❄"
		COLD,
		// a ready-made String
		TEXT
	}

	private final String id;
	private final String displayName;
	private final Kind kind;
	private final String defaultPrefix;
	private final List<Range> ranges;

	// ranges = the time ranges a line can pick. none for values that arent counted over shafts
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

	// the name in the editor's line list
	public String displayName() {
		return displayName;
	}

	public Kind kind() {
		return kind;
	}

	public String defaultPrefix() {
		return defaultPrefix;
	}

	// the ranges a line can pick, in Range order. empty if the value has none
	public List<Range> ranges() {
		return ranges;
	}

	// if a line can pick this range. false for null
	public boolean hasRange(Range range) {
		return range != null && ranges.contains(range);
	}

	// the range a new line and a {value_id} tag use. Session, bc these values were session only before
	// ranges existed, so old lines and tags keep showing the same thing. null if the value has none
	public Range defaultRange() {
		return hasRange(Range.SESSION) ? Range.SESSION : ranges.isEmpty() ? null : ranges.getFirst();
	}

	// if colour thresholds can compare it, so every value thats not text
	public boolean hasNumber() {
		return kind != Kind.TEXT;
	}

	// the value w/ this ID, or null
	public static HudValue byId(String id) {
		for (HudValue value : values()) {
			if (value.id.equals(id)) return value;
		}
		return null;
	}
}
