package dev.shaftjournal.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.shaftjournal.util.Resources;

/** Display names and bar colors for shaft type codes, from {@code shaftjournal/shaft_types.txt}. Plain Java. */
public final class ShaftNames {
	/** Color for codes without one: Minecraft aqua. */
	public static final int DEFAULT_RGB = 0x55FFFF;

	/** The color after the comma: '#' and six hex digits. Anything else keeps the default color. */
	private static final Pattern COLOR = Pattern.compile("\\s*#([0-9A-Fa-f]{6})(?![0-9A-Za-z])");

	private static ShaftNames defaults;

	private record Type(String name, int rgb) {
	}

	private final Map<String, Type> types = new HashMap<>();

	ShaftNames(List<String> lines) {
		for (String line : lines) {
			int eq = line.indexOf('=');
			if (eq < 0 || line.strip().startsWith("#")) continue;
			String value = line.substring(eq + 1);
			int comma = value.indexOf(',');
			String name = comma < 0 ? value : value.substring(0, comma);
			// Without a color, a '#' in the name part starts a comment.
			int comment = name.indexOf('#');
			if (comment >= 0) name = name.substring(0, comment);
			int rgb = DEFAULT_RGB;
			if (comma >= 0) {
				Matcher color = COLOR.matcher(value.substring(comma + 1));
				if (color.lookingAt()) rgb = Integer.parseInt(color.group(1), 16);
			}
			types.put(line.substring(0, eq).strip(), new Type(name.strip(), rgb));
		}
	}

	/** The names bundled with the mod, read once. */
	public static synchronized ShaftNames defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/shaftjournal/shaft_types.txt");
			defaults = new ShaftNames(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	/** The name for a code like "JASP", or the code itself when it isn't listed. */
	public String name(String code) {
		Type type = types.get(code);
		return type == null ? code : type.name();
	}

	/** The bar color as 0xRRGGBB. */
	public int rgb(String code) {
		Type type = types.get(code);
		return type == null ? DEFAULT_RGB : type.rgb();
	}
}
