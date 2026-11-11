package dev.shafttools.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.shafttools.detection.Shaft.ExitReason;
import dev.shafttools.detection.ShaftEvent.ShaftLeft;

/** Replays Nick's recordings and checks the results worked out by hand in Docs/research/05 and 06. */
class RecordingReplayTest {
	@Test
	void recording1() throws IOException {
		List<Shaft> shafts = shafts("2026-11-08_20-00-11.jsonl");
		assertEquals(List.of("AQUA_C", "JASP_1"), ids(shafts));

		Shaft aqua = shafts.get(0);
		assertEquals(ExitReason.BYE, aqua.exitReason());
		assertEquals(1260, aqua.exitPowder());
		assertEquals(List.of(new Corpse("Lapis", true), new Corpse("Tungsten", false)), aqua.corpses());
		assertEquals(1, aqua.loots().size());

		Shaft jasper = shafts.get(1);
		assertEquals(ExitReason.DEATH, jasper.exitReason());
		assertEquals(0, jasper.exitPowder());
		assertEquals(1, jasper.campfiresPlaced().size());
		assertEquals(936, jasper.campfiresPlaced().get(0) / 1000);
		assertEquals(List.of("Mining Speed"), jasper.mayhemBuffs());
		assertEquals(682, jasper.pristineProcs());
		assertEquals(28_876, jasper.pristineItems().get("Flawed Jasper Gemstone"));
		assertTrue(jasper.caveInAt() > 0);
	}

	@Test
	void recording2() throws IOException {
		List<Shaft> shafts = shafts("2026-11-08_22-41-09.jsonl");
		assertEquals(List.of("PERI_2", "JADE_1", "TOPA_1", "RUBY_2", "SAPP_1", "ONYX_2", "TOPA_1", "TITA_1",
				"TOPA_1", "TOPA_1", "LITT_L", "ONYX_1", "AQUA_1", "RUBY_2", "JADE_1"), ids(shafts));
		assertEquals(ExitReason.WARP, shafts.get(2).exitReason());

		// Docs/research/06 section 3: 45 corpses in 15 shafts. It says 4 "MORE!" shafts, but the
		// recording has 5 (JADE_1, RUBY_2, TOPA_1, TOPA_1, JADE_1).
		assertEquals(45, shafts.stream().mapToInt(shaft -> shaft.corpses().size()).sum());
		assertEquals(5, shafts.stream().filter(shaft -> shaft.extraCorpses() > 0).count());

		Shaft jade = shafts.get(14);
		assertEquals(ExitReason.DEATH, jade.exitReason());
		assertEquals(747, jade.durationMillis() / 1000);
		assertEquals(135_434, jade.sacksAdded().get("Rough Jade Gemstone"));
		assertEquals(28_635, jade.sacksAdded().get("Flawed Jade Gemstone"));
		assertEquals(1836, Math.round(jade.lastSacksAt() / 1000.0));
		// Whole shaft, including the unreported tail after the last Sacks message. Docs/research/06
		// gives 25,462 for 614 messages, but those 614 messages add up to 25,870.
		assertEquals(25_870, jade.pristineItems().get("Flawed Jade Gemstone"));
		assertEquals(614, jade.pristineProcs());
	}

	private static List<Shaft> shafts(String recording) throws IOException {
		Path file = RecordingReplay.RECORDINGS.resolve(recording);
		assumeTrue(Files.exists(file), "Recording not available: " + file);
		return RecordingReplay.replay(file).stream()
				.filter(ShaftLeft.class::isInstance)
				.map(event -> ((ShaftLeft) event).shaft())
				.toList();
	}

	private static List<String> ids(List<Shaft> shafts) {
		return shafts.stream().map(shaft -> shaft.id().id()).toList();
	}
}
