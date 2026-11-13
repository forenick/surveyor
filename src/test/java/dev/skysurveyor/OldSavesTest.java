package dev.skysurveyor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OldSavesTest {
	@Test
	void foldersSortLikeReleases() {
		List<String> sorted = List.of("0.2.x", "0.5.3", "0.16.0", "0.22.0-alpha.8", "0.22.0-alpha.10", "0.22.0-beta.1",
				"0.22.0-beta.2", "0.22.0", "0.22.1");
		List<String> shuffled = new ArrayList<>(sorted);
		Collections.reverse(shuffled);
		shuffled.sort(OldSaves::compareVersions);
		assertEquals(sorted, shuffled);
	}
}
