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

/** Display names for shaft type codes, from {@code shafttools/shaft_types.txt}. Plain Java. */
public final class ShaftNames {
	private final Map<String, String> names = new HashMap<>();

	ShaftNames(List<String> lines) {
		for (String line : lines) {
			int hash = line.indexOf('#');
			if (hash >= 0) line = line.substring(0, hash);
			int eq = line.indexOf('=');
			if (eq < 0) continue;
			names.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
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
		return names.getOrDefault(code, code);
	}
}
