package dev.skysurveyor.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.util.Resources;

public final class ShaftNames {
	public static final int DEFAULT_RGB = 0x55FFFF;

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
			// '#' in name part = comment
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

	public static synchronized ShaftNames defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/shaft_types.txt");
			defaults = new ShaftNames(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	public String name(String code) {
		Type type = types.get(code);
		return type == null ? code : type.name();
	}

	public int rgb(String code) {
		Type type = types.get(code);
		return type == null ? DEFAULT_RGB : type.rgb();
	}
}
