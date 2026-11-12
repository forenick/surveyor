package dev.shaftjournal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// our folder in .minecraft (was shafttools before the rename). plain java
public final class DataFolder {
	public static final String NAME = "shaftjournal";
	static final String OLD_NAME = "shafttools";

	private DataFolder() {
	}

	public static Path in(Path gameDir) {
		return gameDir.resolve(NAME);
	}

	// moves the old pre rename folder over, once. does nothing if the new folder already exists so
	// old data never overwrites newer stuff. true if it moved it
	public static boolean migrate(Path gameDir) throws IOException {
		Path old = gameDir.resolve(OLD_NAME);
		Path current = in(gameDir);
		if (!Files.isDirectory(old) || Files.exists(current)) return false;
		Files.move(old, current);
		return true;
	}
}
