package dev.shafttools.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Reads the mod's bundled text files. Plain Java. */
public final class Resources {
	private Resources() {
	}

	/** The lines of a bundled file, e.g. {@code "/shafttools/patterns.txt"}; null when it is missing. */
	public static List<String> lines(String path) {
		try (InputStream in = Resources.class.getResourceAsStream(path)) {
			if (in == null) return null;
			return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
