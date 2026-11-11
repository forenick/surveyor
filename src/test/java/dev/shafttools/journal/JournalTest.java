package dev.shafttools.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.shafttools.detection.RecordingReplay;
import dev.shafttools.detection.ShaftEvent;
import dev.shafttools.detection.ShaftTracker;
import dev.shafttools.detection.TextPatterns;

class JournalTest {
	private static final long MINUTE = 60_000;
	private static final String WOW = "§5§lWOW! §aYou found a §bGlacite Mineshaft portal!";
	private static final String NEARBY = "§b§lMINESHAFT! §7A Mineshaft portal spawned nearby!";

	private final List<JournalEntry> entries = new ArrayList<>();
	private final Journal journal = new Journal(TextPatterns.defaults(), entries::add);
	private long now;
	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> journal.onEvent(event, now));

	@Test
	void timesSpawnFromFirstBlockToWow() {
		at(0).hit(true);
		at(30_000).hit(true);
		at(MINUTE).chat(WOW).chat(NEARBY);
		visitShaft(MINUTE + 3_000, "JADE_1");

		assertEquals(1, entries.size());
		assertEquals(MINUTE, entries.get(0).searchMillis());
		assertEquals("JADE_1", entries.get(0).shaftId());
		assertEquals(new Journal.Session(0, 1, 1, MINUTE), journal.session());

		// The timer restarts at the first hit after the shaft, not when leaving it.
		at(2 * MINUTE).hit(true);
		at(3 * MINUTE + 30_000).chat(WOW);
		visitShaft(3 * MINUTE + 33_000, "AMET_1");
		assertEquals(90_000, entries.get(1).searchMillis());
		assertEquals(75_000, journal.session().averageSearchMillis());
	}

	@Test
	void doesNotTimeOtherPlayersPortalsOrHitsOutsideTheMines() {
		at(0).hit(false);
		at(MINUTE).chat(WOW);
		visitShaft(MINUTE + 3_000, "JADE_1");
		assertNull(entries.get(0).searchMillis());

		at(2 * MINUTE).hit(true);
		at(3 * MINUTE).chat(NEARBY);
		visitShaft(3 * MINUTE + 3_000, "OPAL_1");
		assertNull(entries.get(1).searchMillis());
		assertEquals(null, journal.session().averageSearchMillis());
	}

	@Test
	void longBreakStartsNewSessionAndDropsTheTimer() {
		at(0).hit(true);
		at(16 * MINUTE).hit(true);
		at(17 * MINUTE).chat(WOW);
		visitShaft(17 * MINUTE + 3_000, "JADE_1");

		assertEquals(16 * MINUTE, entries.get(0).sessionId());
		assertEquals(MINUTE, entries.get(0).searchMillis());
	}

	@Test
	void resumesRecentSessionAfterRestart() {
		at(0).hit(true);
		at(MINUTE).chat(WOW);
		visitShaft(MINUTE + 3_000, "JADE_1");

		Journal restarted = new Journal(TextPatterns.defaults(), entry -> {
		});
		restarted.resume(entries);
		assertEquals(journal.session(), restarted.session());
		restarted.onBlockHit(MINUTE + 5 * MINUTE, true);
		assertEquals(0, restarted.session().id());
		restarted.onBlockHit(MINUTE + 30 * MINUTE, true);
		assertEquals(MINUTE + 30 * MINUTE, restarted.session().id());
	}

	@Test
	void fileRoundTrip(@TempDir Path dir) throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_14-13-53.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		assertEquals(2, entries.size());

		JournalFile file = new JournalFile(dir.resolve("shafttools").resolve("journal.jsonl"));
		entries.forEach(file::append);
		file.close();
		assertEquals(entries, file.read());
	}

	/** Jade shaft from recording 2, checked by hand in Docs/research/06: 379.1 Fine reported, ~385 in total. */
	@Test
	void jadeShaftGems() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-08_22-41-09.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		GemYield jade = entries.get(14).gems();

		assertEquals("Jade", jade.gem());
		assertEquals(135_434, jade.rough());
		assertEquals(28_635, jade.flawed());
		assertEquals(379.1, jade.reportedFine(), 0.05);
		assertTrue(jade.fineEquivalent() > 382 && jade.fineEquivalent() < 386, "total " + jade.fineEquivalent());
	}

	@Test
	void summaryOfRecording3() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_14-13-53.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		JournalEntry aqua = entries.get(0);
		assertNull(aqua.gems(), "Nick can't mine Aquamarine yet, so there are no Aquamarine gems");
		assertEquals(5759 + 4791, aqua.corpsePowder());

		String text = String.join("\n", SummaryText.lines(aqua, null));
		assertTrue(text.contains("§b§lAQUA_C  §7inside §f14s"), text);
		assertTrue(text.contains("§9Lapis 2/2 §8· §fTungsten 0/1"), text);
		assertTrue(text.contains("§7Powder §f+12,060 §8(corpses 10,550, exit 1,510)"), text);
	}

	@Test
	void durations() {
		assertEquals("45s", SummaryText.duration(45_999));
		assertEquals("12m 27s", SummaryText.duration(747_000));
		assertEquals("1h 03m", SummaryText.duration(3_780_000));
	}

	private void replay(Path recording) throws IOException {
		// Recordings have no block hits, so spawns are never timed here.
		for (ShaftEvent event : RecordingReplay.replay(recording)) journal.onEvent(event, 0);
	}

	private void visitShaft(long enteredAt, String id) {
		at(enteredAt).tracker.onWorldChange(now);
		tracker.onScoreboard(now, List.of("§e§lSKYBLOCK", "§711/11/26 §8m19AB " + id));
		at(enteredAt + 15_000).tracker.onWorldChange(now);
	}

	private JournalTest at(long time) {
		now = time;
		return this;
	}

	private JournalTest hit(boolean inMiningArea) {
		journal.onBlockHit(now, inMiningArea);
		return this;
	}

	private JournalTest chat(String legacy) {
		tracker.onChat(now, legacy, List.of());
		return this;
	}
}
