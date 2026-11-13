package dev.skysurveyor.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DataFolder {
	public static final String NAME = "skysurveyor";
	// newest first: shaftjournal 0.4.0 to 0.20.x, shafttools before that
	static final List<String> OLD_NAMES = List.of("shaftjournal", "shafttools");

	private DataFolder() {
	}

	// old folder while it couldnt be moved, so data never splits in two
	public static Path in(Path gameDir) {
		Path current = gameDir.resolve(NAME);
		if (Files.exists(current)) return current;
		Path old = old(gameDir);
		return old == null ? current : old;
	}

	// skips if the new folder exists so old data never overwrites newer
	// plain move = one rename, a non-empty dir never gets half copied
	public static String migrate(Path gameDir) throws IOException {
		Path current = gameDir.resolve(NAME);
		Path old = old(gameDir);
		if (old == null || Files.exists(current)) return null;
		Files.move(old, current);
		return old.getFileName().toString();
	}

	private static Path old(Path gameDir) {
		for (String name : OLD_NAMES) {
			Path old = gameDir.resolve(name);
			if (Files.isDirectory(old)) return old;
		}
		return null;
	}
}
