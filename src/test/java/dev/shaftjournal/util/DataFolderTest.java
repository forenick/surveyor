package dev.shaftjournal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataFolderTest {
	@Test
	void movesOldFolder(@TempDir Path game) throws IOException {
		Files.createDirectories(game.resolve("shafttools/recordings"));
		Files.writeString(game.resolve("shafttools/journal.jsonl"), "old");

		assertTrue(DataFolder.migrate(game));
		assertEquals("old", Files.readString(game.resolve("shaftjournal/journal.jsonl")));
		assertTrue(Files.isDirectory(game.resolve("shaftjournal/recordings")));
		assertFalse(Files.exists(game.resolve("shafttools")));
	}

	@Test
	void neverOverwritesNewFolder(@TempDir Path game) throws IOException {
		Files.createDirectories(game.resolve("shafttools"));
		Files.writeString(game.resolve("shafttools/journal.jsonl"), "old");
		Files.createDirectories(game.resolve("shaftjournal"));
		Files.writeString(game.resolve("shaftjournal/journal.jsonl"), "new");

		assertFalse(DataFolder.migrate(game));
		assertEquals("new", Files.readString(game.resolve("shaftjournal/journal.jsonl")));
		assertTrue(Files.exists(game.resolve("shafttools/journal.jsonl")));
	}

	@Test
	void nothingToMove(@TempDir Path game) throws IOException {
		assertFalse(DataFolder.migrate(game));
		assertFalse(Files.exists(game.resolve("shaftjournal")));
	}
}
