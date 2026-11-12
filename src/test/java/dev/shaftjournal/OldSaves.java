package dev.shaftjournal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// old-saves/<version>/ = real files that release wrote. add the new versions files every release
public final class OldSaves {
	public static final Path ROOT = Path.of("src/test/resources/old-saves");

	private OldSaves() {
	}

	// oldest first
	public static List<Path> withFile(String name) {
		try (Stream<Path> dirs = Files.list(ROOT)) {
			List<Path> found = dirs.filter(dir -> Files.isRegularFile(dir.resolve(name))).sorted(BY_VERSION).toList();
			assertTrue(!found.isEmpty(), "no old " + name + " in " + ROOT);
			return found;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Path newest(String name) {
		List<Path> all = withFile(name);
		return all.get(all.size() - 1);
	}

	public static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// missing or different = writes <name>.new next to it, check it by hand + rename
	public static void assertExpected(Path dir, String name, String actual) {
		Path file = dir.resolve(name);
		try {
			if (!Files.exists(file)) {
				Files.writeString(dir.resolve(name + ".new"), actual, StandardCharsets.UTF_8);
				fail("no " + file + " yet, wrote " + name + ".new: check it, then rename it");
			}
			String expected = Files.readString(file, StandardCharsets.UTF_8);
			if (!expected.equals(actual)) Files.writeString(dir.resolve(name + ".new"), actual, StandardCharsets.UTF_8);
			else Files.deleteIfExists(dir.resolve(name + ".new"));
			assertEquals(expected, actual, "old data shows different numbers now: " + file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// every key in old is still in now w/ the same value. new keys are fine
	public static void assertKept(JsonElement old, JsonElement now, String where, Map<String, Set<String>> convertedKeys) {
		if (old.isJsonObject()) {
			assertTrue(now != null && now.isJsonObject(), where + " is gone or not an object anymore");
			JsonObject nowObject = now.getAsJsonObject();
			Set<String> converted = convertedKeys.getOrDefault(where.replaceAll("\\[\\d+]", "[]"), Set.of());
			for (Map.Entry<String, JsonElement> e : old.getAsJsonObject().entrySet()) {
				if (converted.contains(e.getKey()) || e.getValue().isJsonNull()) continue;
				assertKept(e.getValue(), nowObject.get(e.getKey()), where + "." + e.getKey(), convertedKeys);
			}
		} else if (old.isJsonArray()) {
			assertTrue(now != null && now.isJsonArray(), where + " is gone or not a list anymore");
			JsonArray oldArray = old.getAsJsonArray();
			JsonArray nowArray = now.getAsJsonArray();
			assertEquals(oldArray.size(), nowArray.size(), where + " lost or gained items");
			for (int i = 0; i < oldArray.size(); i++) assertKept(oldArray.get(i), nowArray.get(i), where + "[" + i + "]", convertedKeys);
		} else {
			assertEquals(old, now, where + " changed");
		}
	}

	// "0.2.x" < "0.5.3" < "0.16.0"
	private static final Comparator<Path> BY_VERSION = (a, b) -> {
		String[] x = a.getFileName().toString().split("\\.");
		String[] y = b.getFileName().toString().split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int c = Integer.compare(part(x, i), part(y, i));
			if (c != 0) return c;
		}
		return 0;
	};

	private static int part(String[] parts, int i) {
		if (i >= parts.length) return -1;
		try {
			return Integer.parseInt(parts[i]);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
