package dev.shafttools.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShaftNamesTest {
	@Test
	void namesKnownCodesAndKeepsUnknownOnes() {
		ShaftNames names = ShaftNames.load();
		assertEquals("Jasper", names.name("JASP"));
		assertEquals("Vanguard", names.name("FAIR"));
		assertEquals("ABCD", names.name("ABCD"));
		assertEquals(0xE0303A, names.rgb("RUBY"));
		assertEquals(ShaftNames.DEFAULT_RGB, names.rgb("ABCD"));
	}
}
