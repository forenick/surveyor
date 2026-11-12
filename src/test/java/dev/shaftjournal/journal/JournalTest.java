package dev.shaftjournal.journal;

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

import dev.shaftjournal.detection.RecordingReplay;
import dev.shaftjournal.detection.ShaftEvent;
import dev.shaftjournal.detection.ShaftTracker;
import dev.shaftjournal.detection.TextPatterns;

class JournalTest {
	private static final long MINUTE = 60_000;
	private static final String WOW = "§5§lWOW! §aYou found a §bGlacite Mineshaft portal!";
	private static final String NEARBY = "§b§lMINESHAFT! §7A Mineshaft portal spawned nearby!";

	private final List<JournalEntry> entries = new ArrayList<>();
	private boolean inMines = true;
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> inMines, entries::add);
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

		// timer restarts at the first hit after the shaft, not when u leave it
		at(2 * MINUTE).hit(true);
		at(2 * MINUTE + 20_000).hit(true);
		at(2 * MINUTE + 40_000).chat(WOW);
		visitShaft(2 * MINUTE + 43_000, "AMET_1");
		assertEquals(40_000, entries.get(1).searchMillis());
		assertEquals(50_000, journal.session().averageSearchMillis());
	}

	@Test
	void gapsOverFortySecondsOnlyCountFortySeconds() {
		at(0).hit(true);
		at(10_000).hit(true);
		at(110_000).hit(true); // 100 s break, only 40 s count
		at(120_000).chat(WOW);
		visitShaft(123_000, "JADE_1");
		assertEquals(60_000, entries.get(0).searchMillis());
	}

	@Test
	void spawnTimerForTheHud() {
		assertNull(journal.spawnTimerMillis(0));
		at(0).hit(true);
		at(10_000).hit(true);
		assertEquals(15_000, journal.spawnTimerMillis(15_000));
		assertEquals(50_000, journal.spawnTimerMillis(200_000), "a break counts for 40 s at most");
		at(20_000).chat(WOW);
		assertEquals(20_000, journal.spawnTimerMillis(90_000), "frozen at the found time");
		visitShaft(23_000, "JADE_1");
		assertNull(journal.spawnTimerMillis(30_000), "nothing until the next search starts");
	}

	@Test
	void spawnTimerEndsWithTheSession() {
		at(0).hit(true);
		at(60_000).hit(true);
		assertEquals(80_000, journal.spawnTimerMillis(60_000 + Journal.SESSION_GAP_MILLIS), "40 s of mining plus a 40 s break");
		assertNull(journal.spawnTimerMillis(60_001 + Journal.SESSION_GAP_MILLIS),
				"the next activity starts a new session and drops this timer, so it isn't shown");
	}

	@Test
	void aPortalLeftUnenteredIsNotTimedForTheNextShaft() {
		at(0).hit(true);
		at(20_000).chat(WOW);
		at(70_000).hit(true); // over 40 s later = new search, the found portal got abandoned
		assertEquals(0, journal.spawnTimerMillis(70_000));
		visitShaft(80_000, "JADE_1"); // someone elses portal, no WOW
		assertNull(entries.get(0).searchMillis());
	}

	@Test
	void sessionRunsOutForDisplayBeforeTheNextActivity() {
		at(0).hit(true);
		assertEquals(0, journal.activeSession(Journal.SESSION_GAP_MILLIS).id());
		assertNull(journal.activeSession(Journal.SESSION_GAP_MILLIS + 1));
		assertNull(new Journal(TextPatterns.defaults(), () -> true, e -> {
		}).activeSession(0), "no session before any activity");
	}

	@Test
	void pickobulusCountsAsMining() {
		at(0).hit(true);
		at(35_000).chat("§aYou used your §6Pickobulus §aPickaxe Ability!");
		at(70_000).chat("§aYou used your §6Pickobulus §aPickaxe Ability!");
		at(71_000).chat(WOW);
		visitShaft(74_000, "JADE_1");
		assertEquals(71_000, entries.get(0).searchMillis());
	}

	@Test
	void miningBetweenWowAndEnteringDoesNotStartATimer() {
		at(0).hit(true);
		at(10_000).chat(WOW);
		at(12_000).hit(true);
		assertTrue(!journal.searchRunning());
		visitShaft(15_000, "JADE_1");
		assertEquals(10_000, entries.get(0).searchMillis());

		// a portal u dont enter within 40 s counts as skipped
		at(MINUTE).hit(true);
		at(MINUTE + 5_000).chat(WOW);
		at(MINUTE + 50_000).hit(true);
		assertTrue(journal.searchRunning());
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
		assertEquals(40_000, entries.get(0).searchMillis());
	}

	@Test
	void resumesRecentSessionAfterRestart() {
		at(0).hit(true);
		at(MINUTE).chat(WOW);
		visitShaft(MINUTE + 3_000, "JADE_1");

		Journal restarted = new Journal(TextPatterns.defaults(), () -> true, entry -> {
		});
		restarted.resume(entries);
		assertEquals(journal.session(), restarted.session());
		restarted.onBlockHit(MINUTE + 5 * MINUTE);
		assertEquals(0, restarted.session().id());
		restarted.onBlockHit(MINUTE + 30 * MINUTE);
		assertEquals(MINUTE + 30 * MINUTE, restarted.session().id());
	}

	@Test
	void fileRoundTrip(@TempDir Path dir) throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_14-13-53.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		replay(recording);
		assertEquals(2, entries.size());

		JournalFile file = new JournalFile(dir.resolve("shaftjournal").resolve("journal.jsonl"));
		entries.forEach(file::append);
		file.close();
		assertEquals(entries, file.read());
	}

	// Jade shaft from recording 2, checked by hand in Docs/research/06. 379.1 Fine reported, ~385 in total
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
		assertTrue(text.contains("§9Lapis 2/2§7, §fTungsten 0/1"), text);
		assertTrue(text.contains("§7Powder §f+12,060 §8(corpses 10,550, exit 1,510)"), text);
	}

	// recording 4 (Docs/research/08), spawn times w/ the 40 s pause rule, checked by hand
	@Test
	void recording4SpawnTimes() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_17-16-21.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		ShaftTracker replayTracker = new ShaftTracker(TextPatterns.defaults(), event -> journal.onEvent(event, now));
		inMines = false;
		RecordingReplay.replay(recording, replayTracker, t -> {
			now = t;
			inMines = replayTracker.inMiningArea();
		}, journal::onBlockHit);

		assertEquals(List.of("PERI_1", "JADE_1", "AMET_1", "TITA_1", "AQUA_C", "ONYX_1"),
				entries.stream().map(JournalEntry::shaftId).toList());
		List<Long> seconds = entries.stream().map(entry -> Math.round(entry.searchMillis() / 1000.0)).toList();
		assertEquals(List.of(10L, 56L, 38L, 157L, 84L, 164L), seconds);
	}

	@Test
	void durations() {
		assertEquals("45s", SummaryText.duration(45_999));
		assertEquals("12m 27s", SummaryText.duration(747_000));
		assertEquals("1h 03m", SummaryText.duration(3_780_000));
	}

	private void replay(Path recording) throws IOException {
		// recordings have no block hits so spawns never get timed here
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
		inMines = inMiningArea;
		journal.onBlockHit(now);
		return this;
	}

	private JournalTest chat(String legacy) {
		tracker.onChat(now, legacy, List.of());
		return this;
	}
}
