package dev.skysurveyor.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IconRulesTest {
	private final IconRules rules = IconRules.defaults();

	@Test
	void picksTheFirstMatchingRule() {
		assertEquals("minecraft:amethyst_shard", rules.itemId("Flawed Peridot Gemstone"));
		assertEquals("minecraft:emerald", rules.itemId("Flawless Ruby Gemstone"));
		assertEquals("minecraft:egg", rules.itemId("Red Goblin Egg"));
		assertEquals("minecraft:egg", rules.itemId("Goblin Egg"));
		assertEquals("minecraft:clay_ball", rules.itemId("Enchanted Tungsten"));
	}

	@Test
	void ignoresTrailingComments() {
		IconRules rules = new IconRules(java.util.List.of("Enchanted Umber = minecraft:brown_terracotta  # stand-in"));
		assertEquals("minecraft:brown_terracotta", rules.itemId("Enchanted Umber"));
	}

	@Test
	void unknownItemsUseTheDefault() {
		assertEquals("minecraft:paper", rules.itemId("Something New"));
	}
}
