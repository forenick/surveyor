package dev.shaftjournal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DataFolder {
	public static final String NAME = "shaftjournal";
	static final String OLD_NAME = "shafttools";

	private DataFolder() {
	}

	public static Path in(Path gameDir) {
		return gameDir.resolve(NAME);
	}

	// skips if the new folder exists so old data never overwrites newer
	public static boolean migrate(Path gameDir) throws IOException {
		Path old = gameDir.resolve(OLD_NAME);
		Path current = in(gameDir);
		if (!Files.isDirectory(old) || Files.exists(current)) return false;
		Files.move(old, current);
		return true;
	}
}
