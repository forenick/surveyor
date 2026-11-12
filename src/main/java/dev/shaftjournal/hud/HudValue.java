package dev.shaftjournal.hud;

import java.util.List;
import java.util.Locale;

/**
 * Every value a HUD line can show. The ID is what the config file and (later) text tags use, so
 * never rename one. Plain Java.
 */
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

	/**
	 * Which shafts a countable value adds up. The config file stores the name, so never rename one.
	 */
	public enum Range {
		/** The shaft the player is in, or the last one after leaving it. */
		SHAFT("Shaft"),
		SESSION("Session"),
		/** The session's amount per hour of session, counted up to now. */
		PER_HOUR("Per hour"),
		ALL_TIME("All-time");

		private final String displayName;

		Range(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

		/** The name in free-text tags, e.g. "all_time" in {session_powder:all_time}. */
		public String tagName() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The range with this tag name, or null. */
		public static Range byTagName(String name) {
			for (Range range : values()) {
				if (range.tagName().equals(name)) return range;
			}
			return null;
		}
	}

	/** How a value is stored and which number formats it offers. */
	public enum Kind {
		/** Milliseconds, as a Long. */
		TIME,
		/** A Long. */
		INTEGER,
		/** A Double. */
		DECIMAL,
		/** Cold, as a Long; shown with ❄ like the scoreboard, e.g. "-12❄". */
		COLD,
		/** A ready-made String. */
		TEXT
	}

	private final String id;
	private final String displayName;
	private final Kind kind;
	private final String defaultPrefix;
	private final List<Range> ranges;

	/** @param ranges the time ranges a line can pick; none for values that aren't counted over shafts */
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

	/** The name in the editor's line list. */
	public String displayName() {
		return displayName;
	}

	public Kind kind() {
		return kind;
	}

	public String defaultPrefix() {
		return defaultPrefix;
	}

	/** The ranges a line can pick, in {@link Range} order; empty when the value has none. */
	public List<Range> ranges() {
		return ranges;
	}

	/** Whether a line can pick this range; false for null. */
	public boolean hasRange(Range range) {
		return range != null && ranges.contains(range);
	}

	/**
	 * The range a new line and a {value_id} tag use: Session, as these values were session-only
	 * before ranges, so old lines and tags keep showing the same. Null when the value has none.
	 */
	public Range defaultRange() {
		return hasRange(Range.SESSION) ? Range.SESSION : ranges.isEmpty() ? null : ranges.getFirst();
	}

	/** Whether colour thresholds can compare it: every value that isn't text. */
	public boolean hasNumber() {
		return kind != Kind.TEXT;
	}

	/** The value with this ID, or null. */
	public static HudValue byId(String id) {
		for (HudValue value : values()) {
			if (value.id.equals(id)) return value;
		}
		return null;
	}
}
