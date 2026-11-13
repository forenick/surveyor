package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.journal.JournalStats.SessionSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

// made up sessions, 1 per day
class StatsRangeTest {
	private static final long DAY = 24 * 3_600_000L;

	private static JournalEntry entry(long session, long entered) {
		return new JournalEntry(JournalEntry.VERSION, session, "JADE_1", entered, 60_000, "BYE", 0, null, List.of(), 0,
				List.of(), List.of(), List.of(), null, 0, 0, Map.of(), Map.of(), Map.of(), null, List.of(), Map.of(), null);
	}

	private static List<SessionSummary> tenDays() {
		List<JournalEntry> entries = new ArrayList<>();
		for (int day = 0; day < 10; day++) {
			entries.add(entry(day * DAY, day * DAY));
			entries.add(entry(day * DAY, day * DAY + 120_000));
		}
		return JournalStats.sessions(entries);
	}

	@Test
	void picksWholeSessions() {
		List<SessionSummary> sessions = tenDays();
		long now = 9 * DAY + 3_600_000;
		assertSame(sessions, StatsRange.ALL_TIME.sessions(sessions, now));
		// days 3 to 9 started in the last 7 days
		assertEquals(7, StatsRange.LAST_7_DAYS.sessions(sessions, now).size());
		assertEquals(3 * DAY, StatsRange.LAST_7_DAYS.sessions(sessions, now).getLast().start());
		List<SessionSummary> recent = StatsRange.LAST_5_SESSIONS.sessions(sessions, now);
		assertEquals(5, recent.size());
		assertSame(sessions.getFirst(), recent.getFirst());
	}

	@Test
	void entriesOldestFirst() {
		List<SessionSummary> recent = StatsRange.LAST_5_SESSIONS.sessions(tenDays(), 0);
		List<JournalEntry> entries = StatsRange.entries(recent);
		assertEquals(10, entries.size());
		assertEquals(5 * DAY, entries.getFirst().enteredAt());
		assertEquals(9 * DAY + 120_000, entries.getLast().enteredAt());
	}

	@Test
	void arrowsWrapAround() {
		assertEquals(StatsRange.LAST_5_SESSIONS, StatsRange.ALL_TIME.next(-1));
		assertEquals(StatsRange.ALL_TIME, StatsRange.LAST_5_SESSIONS.next(1));
		assertEquals(StatsRange.LAST_7_DAYS, StatsRange.ALL_TIME.next(1));
	}
}
