package dev.shaftjournal.journal;

import java.util.ArrayList;
import java.util.List;

import dev.shaftjournal.journal.JournalStats.SessionSummary;

// whole sessions only, so records and bars still open them
public enum StatsRange {
	ALL_TIME("All time"),
	LAST_7_DAYS("Last 7 days"),
	LAST_5_SESSIONS("Last 5 sessions");

	// display choices, not game numbers
	private static final long WEEK_MILLIS = 7 * 24 * 3_600_000L;
	private static final int RECENT_SESSIONS = 5;

	private final String displayName;

	StatsRange(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public StatsRange next(int step) {
		StatsRange[] all = values();
		return all[Math.floorMod(ordinal() + step, all.length)];
	}

	public List<SessionSummary> sessions(List<SessionSummary> newestFirst, long now) {
		return switch (this) {
			case ALL_TIME -> newestFirst;
			case LAST_7_DAYS -> newestFirst.stream().filter(s -> s.start() >= now - WEEK_MILLIS).toList();
			case LAST_5_SESSIONS -> newestFirst.stream().limit(RECENT_SESSIONS).toList();
		};
	}

	public static List<JournalEntry> entries(List<SessionSummary> newestFirst) {
		List<JournalEntry> entries = new ArrayList<>();
		for (SessionSummary session : newestFirst.reversed()) entries.addAll(session.entries());
		return entries;
	}
}
