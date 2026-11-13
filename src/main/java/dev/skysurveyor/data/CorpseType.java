package dev.skysurveyor.data;

import java.util.List;

// enum order = display order, colours a display choice. keys from research/01
public enum CorpseType {
	LAPIS("Lapis", "§9", 0xFF5555FF, null),
	UMBER("Umber", "§6", 0xFFFFAA00, "Umber Key"),
	TUNGSTEN("Tungsten", "§7", 0xFFAAAAAA, "Tungsten Key"),
	VANGUARD("Vanguard", "§b", 0xFF55FFFF, "Skeleton Key");

	public static final List<String> ORDER = List.of(LAPIS.label, UMBER.label, TUNGSTEN.label, VANGUARD.label);

	private final String label;
	private final String color;
	private final int argb;
	private final String key;

	CorpseType(String label, String color, int argb, String key) {
		this.label = label;
		this.color = color;
		this.argb = argb;
		this.key = key;
	}

	// tab list name, f.e. "Lapis"
	public String label() {
		return label;
	}

	// null = unknown type
	public static CorpseType of(String label) {
		for (CorpseType type : values()) {
			if (type.label.equals(label)) return type;
		}
		return null;
	}

	public static String color(String label) {
		CorpseType type = of(label);
		return type == null ? "§f" : type.color;
	}

	public static int argb(String label) {
		CorpseType type = of(label);
		return type == null ? 0xFFFFFFFF : type.argb;
	}

	// null = no key needed
	public static String key(String label) {
		CorpseType type = of(label);
		return type == null ? null : type.key;
	}
}
