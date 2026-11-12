package dev.shaftjournal.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.shaftjournal.screen.SkyblockItems.Info;

class SkyblockItemsTest {
	private final SkyblockItems items = SkyblockItems.defaults();

	@Test
	void readsIdModelAndGlint() {
		assertEquals(new Info("FINE_JADE_GEM", "hypixel_skyblock:item/collections/gemstone/jade/fine_jade_gem", false),
				items.get("Fine Jade Gemstone"));
		assertEquals(new Info("ENCHANTED_UMBER", "hypixel_skyblock:item/glacite/umber/umber", true),
				items.get("Enchanted Umber"));
		assertEquals(new Info("SHATTERED_PENDANT", null, false), items.get("Shattered Locket"));
		assertEquals(new Info("DEAD_MANS_CHESTPLATE", null, true), items.get("Dead Man's Chestplate"));
		assertNull(items.get("Something New"));
	}

	@Test
	void skipsCommentsAndBrokenLines() {
		SkyblockItems parsed = new SkyblockItems(List.of("# comment", "", "No Equals", "Only Id = ONLY_ID",
				"Goblin Egg = GOBLIN_EGG hypixel_skyblock:item/x"));
		assertNull(parsed.get("Only Id"));
		assertEquals(new Info("GOBLIN_EGG", "hypixel_skyblock:item/x", false), parsed.get("Goblin Egg"));
	}

	// every loot item in the real journal fixture needs an entry, so its icon can get textured
	@Test
	void coversRecordedLoot() throws Exception {
		Path journal = Path.of("src/test/resources/journal-2026-11-11.jsonl");
		var names = java.util.regex.Pattern.compile("\"item\":\"([^\"]+)\"").matcher(Files.readString(journal))
				.results().map(m -> m.group(1)).distinct().toList();
		for (String name : names) assertNotNull(items.get(name), name);
	}
}
