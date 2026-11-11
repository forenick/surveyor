package dev.shaftjournal.hud;

/**
 * Every value a HUD line can show. The ID is what the config file and (later) text tags use, so
 * never rename one. Plain Java.
 */
public enum HudValue {
	SESSION_SHAFTS("session_shafts", "Shafts", Kind.INTEGER, "Shafts: "),
	SHAFTS_PER_HOUR("shafts_per_hour", "Shafts per hour", Kind.DECIMAL, "Shafts/h: "),
	AVERAGE_SPAWN("average_spawn", "Average spawn", Kind.TIME, "Avg spawn: "),
	SESSION_POWDER("session_powder", "Powder", Kind.INTEGER, "Powder: "),
	SESSION_CORPSE_FINE("session_corpse_fine", "Fine from corpses", Kind.DECIMAL, "Fine: "),
	SPAWN_TIMER("spawn_timer", "Spawn timer", Kind.TIME, "Spawn: "),
	SHAFT_TYPE("shaft_type", "Shaft type", Kind.TEXT, "Shaft: "),
	SHAFT_TIME("shaft_time", "Time inside", Kind.TIME, "Inside: "),
	SHAFT_CORPSES("shaft_corpses", "Corpses", Kind.TEXT, "Corpses: "),
	SHAFT_COLD("shaft_cold", "Cold", Kind.TEXT, "Cold: "),
	SHAFT_CAMPFIRES("shaft_campfires", "Campfires", Kind.INTEGER, "Campfires: ");

	/** How a value is stored and which number formats it offers. */
	public enum Kind {
		/** Milliseconds, as a Long. */
		TIME,
		/** A Long. */
		INTEGER,
		/** A Double. */
		DECIMAL,
		/** A ready-made String. */
		TEXT
	}

	private final String id;
	private final String displayName;
	private final Kind kind;
	private final String defaultPrefix;

	HudValue(String id, String displayName, Kind kind, String defaultPrefix) {
		this.id = id;
		this.displayName = displayName;
		this.kind = kind;
		this.defaultPrefix = defaultPrefix;
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

	/** The value with this ID, or null. */
	public static HudValue byId(String id) {
		for (HudValue value : values()) {
			if (value.id.equals(id)) return value;
		}
		return null;
	}
}
