package dev.shafttools.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IconRulesTest {
	private final IconRules rules = IconRules.load();

	@Test
	void picksTheFirstMatchingRule() {
		assertEquals("minecraft:amethyst_shard", rules.itemId(IconRules.GEMSTONES));
		assertEquals("minecraft:emerald", rules.itemId("Flawless Ruby Gemstone"));
		assertEquals("minecraft:egg", rules.itemId("Red Goblin Egg"));
		assertEquals("minecraft:egg", rules.itemId("Goblin Egg"));
		assertEquals("minecraft:clay_ball", rules.itemId("Enchanted Tungsten"));
	}

	@Test
	void unknownItemsUseTheDefault() {
		assertEquals("minecraft:paper", rules.itemId("Something New"));
	}
}
