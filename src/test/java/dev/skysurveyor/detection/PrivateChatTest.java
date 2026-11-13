package dev.skysurveyor.detection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// lobby + friend lines from Recordings/, names swapped. party + co-op unconfirmed
class PrivateChatTest {
	private final TextPatterns patterns = TextPatterns.defaults();

	private boolean skipped(String legacy) {
		return patterns.matches("chat.private", TextPatterns.clean(legacy));
	}

	@Test
	void playerChatIsSkipped() {
		assertTrue(skipped("[89] ⚝ §b[MVP] Someone§f: royal mines"));
		assertTrue(skipped("[20] Someone: go to ice wall and then through the gate"));
		assertTrue(skipped("Friend > Someone joined."));
		assertTrue(skipped("Guild > [VIP] Someone: hi"));
		assertTrue(skipped("From [MVP+] Someone: hi"));
		assertTrue(skipped("Party > [MVP+] Someone: hi"));
	}

	@Test
	void gameTextIsKept() {
		assertFalse(skipped("§5§lWOW! §aYou found a §bGlacite Mineshaft §aportal!"));
		assertFalse(skipped("Warping..."));
		assertFalse(skipped("  FROZEN CORPSE LOOT! "));
		assertFalse(skipped("BYE! You got a +1,234 Glacite Powder bonus for leaving a Mineshaft before you died!"));
	}
}
