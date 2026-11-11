package dev.shafttools.screen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Display names and bar colors for shaft type codes, from {@code shafttools/shaft_types.txt}. Plain Java. */
public final class ShaftNames {
	/** Color for codes without one: Minecraft aqua. */
	public static final int DEFAULT_RGB = 0x55FFFF;

	private record Type(String name, int rgb) {
	}

	private final Map<String, Type> types = new HashMap<>();

	ShaftNames(List<String> lines) {
		for (String line : lines) {
			int eq = line.indexOf('=');
			if (eq < 0 || line.strip().startsWith("#")) continue;
			String value = line.substring(eq + 1);
			// The color itself starts with '#', so the comment starts at the second '#' after '='.
			int color = value.indexOf('#');
			int comment = color < 0 ? -1 : value.indexOf('#', color + 1);
			if (comment >= 0) value = value.substring(0, comment);
			String[] parts = value.split(",");
			int rgb = DEFAULT_RGB;
			if (parts.length > 1) {
				String hex = parts[1].strip();
				if (hex.startsWith("#")) rgb = Integer.parseInt(hex.substring(1), 16);
			}
			types.put(line.substring(0, eq).strip(), new Type(parts[0].strip(), rgb));
		}
	}

	public static ShaftNames load() {
		try (InputStream in = ShaftNames.class.getResourceAsStream("/shafttools/shaft_types.txt")) {
			if (in == null) return new ShaftNames(List.of());
			return new ShaftNames(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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
