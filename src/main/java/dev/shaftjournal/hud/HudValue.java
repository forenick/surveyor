package dev.shaftjournal.hud;

import java.util.List;
import java.util.Locale;

// ids saved in hud.json + tags, never rename
public enum HudValue {
	SESSION_SHAFTS("session_shafts", "Shafts", Group.SESSION, Kind.INTEGER, "Shafts: ", Range.SESSION, Range.ALL_TIME),
	SHAFTS_PER_HOUR("shafts_per_hour", "Shafts per hour", Group.SESSION, Kind.DECIMAL, "Shafts/h: "),
	AVERAGE_SPAWN("average_spawn", "Average spawn", Group.SPAWN, Kind.TIME, "Avg spawn: "),
	SESSION_POWDER("session_powder", "Powder", Group.SESSION, Kind.INTEGER, "Powder: ", Range.values()),
	SESSION_CORPSE_FINE("session_corpse_fine", "Fine from corpses", Group.SESSION, Kind.DECIMAL, "Fine: ", Range.values()),
	SPAWN_TIMER("spawn_timer", "Spawn timer", Group.SPAWN, Kind.TIME, "Spawn: "),
	SHAFT_TYPE("shaft_type", "Shaft type", Group.SHAFT, Kind.TEXT, "Shaft: "),
	SHAFT_TIME("shaft_time", "Time inside", Group.SHAFT, Kind.TIME, "Inside: "),
	SHAFT_CORPSES("shaft_corpses", "Corpses", Group.SHAFT, Kind.TEXT, "Corpses: "),
	SHAFT_COLD("shaft_cold", "Cold", Group.SHAFT, Kind.COLD, "Cold: "),
	SHAFT_CAMPFIRES("shaft_campfires", "Campfires", Group.SHAFT, Kind.INTEGER, "Campfires: "),
	// net, shaft = live while inside
	COINS("coins", "Coins", Group.MONEY, Kind.COINS, "Coins: ", Range.values()),
	COINS_PER_SHAFT("coins_per_shaft", "Coins per shaft", Group.MONEY, Kind.COINS, "Coins/shaft: ", Range.SESSION,
			Range.ALL_TIME),
	FPS("fps", "FPS", Group.GAME, Kind.INTEGER, "FPS: "),
	PING("ping", "Ping", Group.GAME, Kind.INTEGER, "Ping: ", "ms"),
	TPS("tps", "TPS", Group.GAME, Kind.DECIMAL, "TPS: "),
	CLOCK("clock", "Clock", Group.GAME, Kind.TEXT, "Time: "),
	MOD_VERSION("mod_version", "ShaftJournal version", Group.GAME, Kind.TEXT, "ShaftJournal "),
	MINECRAFT_VERSION("minecraft_version", "Minecraft version", Group.GAME, Kind.TEXT, "Minecraft ");

	// picker headings, display only
	public enum Group {
		SPAWN("Spawn"),
		SESSION("Session"),
		SHAFT("This shaft"),
		MONEY("Money"),
		GAME("Game");

		private final String displayName;

		Group(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

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
		// Double, 1.2M
		COINS,
		TEXT
	}

	private final String id;
	private final String displayName;
	private final Group group;
	private final Kind kind;
	private final String defaultPrefix;
	private final List<Range> ranges;
	private final String unit;

	HudValue(String id, String displayName, Group group, Kind kind, String defaultPrefix, Range... ranges) {
		this(id, displayName, group, kind, defaultPrefix, "", ranges);
	}

	HudValue(String id, String displayName, Group group, Kind kind, String defaultPrefix, String unit, Range... ranges) {
		this.id = id;
		this.displayName = displayName;
		this.group = group;
		this.kind = kind;
		this.defaultPrefix = defaultPrefix;
		this.unit = unit;
		this.ranges = List.of(ranges);
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public Group group() {
		return group;
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

	public String unit() {
		return unit;
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
