package dev.skysurveyor.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.OldSaves;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DataFolderTest {
	@Test
	void movesShaftjournalFolderByteForByte(@TempDir Path game) throws IOException {
		Path old = realFolder(game.resolve("shaftjournal"));
		Map<String, byte[]> before = files(old);

		assertEquals("shaftjournal", DataFolder.migrate(game));
		assertFalse(Files.exists(old));
		assertEquals(game.resolve("skysurveyor"), DataFolder.in(game));
		assertSame(before, files(game.resolve("skysurveyor")));
	}

	@Test
	void movesShafttoolsFolderStraightToNew(@TempDir Path game) throws IOException {
		Path old = realFolder(game.resolve("shafttools"));
		Map<String, byte[]> before = files(old);

		assertEquals("shafttools", DataFolder.migrate(game));
		assertFalse(Files.exists(old));
		assertSame(before, files(game.resolve("skysurveyor")));
	}

	@Test
	void bothOldFoldersMovesTheNewerOne(@TempDir Path game) throws IOException {
		Files.createDirectories(game.resolve("shafttools"));
		Files.writeString(game.resolve("shafttools/journal.jsonl"), "oldest");
		Files.createDirectories(game.resolve("shaftjournal"));
		Files.writeString(game.resolve("shaftjournal/journal.jsonl"), "old");

		assertEquals("shaftjournal", DataFolder.migrate(game));
		assertEquals("old", Files.readString(game.resolve("skysurveyor/journal.jsonl")));
		assertEquals("oldest", Files.readString(game.resolve("shafttools/journal.jsonl")));
	}

	@Test
	void neverOverwritesNewFolder(@TempDir Path game) throws IOException {
		Files.createDirectories(game.resolve("shaftjournal"));
		Files.writeString(game.resolve("shaftjournal/journal.jsonl"), "old");
		Files.createDirectories(game.resolve("shafttools"));
		Files.writeString(game.resolve("shafttools/journal.jsonl"), "oldest");
		Files.createDirectories(game.resolve("skysurveyor"));
		Files.writeString(game.resolve("skysurveyor/journal.jsonl"), "new");

		assertNull(DataFolder.migrate(game));
		assertEquals("new", Files.readString(game.resolve("skysurveyor/journal.jsonl")));
		assertEquals("old", Files.readString(game.resolve("shaftjournal/journal.jsonl")));
		assertEquals("oldest", Files.readString(game.resolve("shafttools/journal.jsonl")));
		assertEquals(game.resolve("skysurveyor"), DataFolder.in(game));
	}

	@Test
	void failedMoveKeepsOldFolderInUse(@TempDir Path game) throws IOException {
		Path old = realFolder(game.resolve("shaftjournal"));
		Map<String, byte[]> before = files(old);
		// needs posix + non-root, else the folder stays writable
		assumeTrue(game.getFileSystem().supportedFileAttributeViews().contains("posix"));
		Files.setPosixFilePermissions(game, PosixFilePermissions.fromString("r-xr-xr-x"));
		try {
			assumeFalse(Files.isWritable(game));
			assertThrows(IOException.class, () -> DataFolder.migrate(game));
		} finally {
			Files.setPosixFilePermissions(game, PosixFilePermissions.fromString("rwxr-xr-x"));
		}
		assertFalse(Files.exists(game.resolve("skysurveyor")));
		assertSame(before, files(old));
		assertEquals(old, DataFolder.in(game), "keeps using the old folder, no empty new one");
	}

	@Test
	void nothingToMove(@TempDir Path game) throws IOException {
		assertNull(DataFolder.migrate(game));
		assertFalse(Files.exists(game.resolve("skysurveyor")));
		assertEquals(game.resolve("skysurveyor"), DataFolder.in(game));
	}

	// real 0.20.0 files + a recording
	private static Path realFolder(Path folder) throws IOException {
		Path saves = OldSaves.ROOT.resolve("0.20.0");
		Files.createDirectories(folder.resolve("recordings"));
		for (String name : new String[] {"journal.jsonl", "hud.json", "config.json"}) {
			Files.copy(saves.resolve(name), folder.resolve(name));
		}
		Files.writeString(folder.resolve("recordings/2026-11-11_14-13-53.jsonl"), "{\"t\":1}\n");
		return folder;
	}

	private static Map<String, byte[]> files(Path folder) throws IOException {
		Map<String, byte[]> files = new TreeMap<>();
		try (Stream<Path> walk = Files.walk(folder)) {
			for (Path file : walk.filter(Files::isRegularFile).toList()) {
				files.put(folder.relativize(file).toString(), Files.readAllBytes(file));
			}
		}
		return files;
	}

	private static void assertSame(Map<String, byte[]> before, Map<String, byte[]> after) {
		assertEquals(before.keySet(), after.keySet());
		assertTrue(before.size() >= 4);
		before.forEach((name, bytes) -> assertArrayEquals(bytes, after.get(name), name));
	}
}
