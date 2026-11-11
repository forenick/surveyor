package dev.shaftjournal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** The mod's folder in {@code .minecraft}, which held {@code shafttools} before the rename. Plain Java. */
public final class DataFolder {
	public static final String NAME = "shaftjournal";
	static final String OLD_NAME = "shafttools";

	private DataFolder() {
	}

	/** The mod's folder inside this game directory. */
	public static Path in(Path gameDir) {
		return gameDir.resolve(NAME);
	}

	/**
	 * Moves the pre-rename folder to the new name, once. Does nothing when the new folder already
	 * exists, so an old folder never overwrites newer data.
	 *
	 * @return true if the old folder was moved
	 */
	public static boolean migrate(Path gameDir) throws IOException {
		Path old = gameDir.resolve(OLD_NAME);
		Path current = in(gameDir);
		if (!Files.isDirectory(old) || Files.exists(current)) return false;
		Files.move(old, current);
		return true;
	}
}
