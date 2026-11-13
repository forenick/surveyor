package dev.skysurveyor.hud;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import dev.skysurveyor.OldSaves;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OldHudTest {
	// v1 x/y -> anchors, v2 visibility -> show in. HudTest checks those conversions
	private static final Map<String, Set<String>> CONVERTED = Map.of("boxes[]", Set.of("x", "y", "visibility"));

	@TestFactory
	Stream<DynamicTest> oldLayoutsKeepEveryBoxAndLine() {
		return OldSaves.withFile("hud.json").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			String json = OldSaves.read(dir.resolve("hud.json"));
			JsonObject old = JsonParser.parseString(json).getAsJsonObject();
			JsonObject now = JsonParser.parseString(HudLayout.fromJson(json).toJson()).getAsJsonObject();
			OldSaves.assertKept(old.get("boxes"), now.get("boxes"), "boxes", CONVERTED);
			// moved toast spot, 0.22.0+
			if (old.has("toast")) OldSaves.assertKept(old.get("toast"), now.get("toast"), "toast", CONVERTED);
		}));
	}

	// else every start makes a needless hud.json backup
	@Test
	void newestLayoutLoadsUnchanged() {
		Path dir = OldSaves.newest("hud.json");
		String json = OldSaves.read(dir.resolve("hud.json"));
		assertEquals(json, HudLayout.fromJson(json).toJson());
	}
}
