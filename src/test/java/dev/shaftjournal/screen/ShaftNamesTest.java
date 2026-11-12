package dev.shaftjournal.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaftNamesTest {
	@Test
	void namesKnownCodesAndKeepsUnknownOnes() {
		ShaftNames names = ShaftNames.defaults();
		assertEquals("Jasper", names.name("JASP"));
		assertEquals("Vanguard", names.name("FAIR"));
		assertEquals("ABCD", names.name("ABCD"));
		assertEquals(0xE0303A, names.rgb("RUBY"));
		assertEquals(ShaftNames.DEFAULT_RGB, names.rgb("ABCD"));
	}

	@Test
	void commentWithoutColorAndBadColorsAreHandled() {
		ShaftNames names = new ShaftNames(java.util.List.of(
				"TUNG = Tungsten   # unconfirmed",
				"UMBE = Umber, #8A93AG   # typo",
				"RUBY = Ruby, #E0303A # seen"));
		assertEquals("Tungsten", names.name("TUNG"));
		assertEquals(ShaftNames.DEFAULT_RGB, names.rgb("TUNG"));
		assertEquals("Umber", names.name("UMBE"));
		assertEquals(ShaftNames.DEFAULT_RGB, names.rgb("UMBE"));
		assertEquals(0xE0303A, names.rgb("RUBY"));
	}
}
