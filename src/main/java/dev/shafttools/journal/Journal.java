package dev.shafttools.journal;

import java.util.List;
import java.util.function.Consumer;

import dev.shafttools.detection.ShaftEvent;
import dev.shafttools.detection.ShaftEvent.PortalSpawned;
import dev.shafttools.detection.ShaftEvent.ShaftEntered;
import dev.shafttools.detection.ShaftEvent.ShaftLeft;
import dev.shafttools.detection.TextPatterns;

/**
 * Turns finished shafts into {@link JournalEntry}s, and keeps the session and the spawn timer.
 *
 * <p>Spawn timer: starts at the first block hit in the mining area after the previous shaft (or
 * at the session start), stops at "WOW! You found a Glacite Mineshaft portal!". Only the finder
 * gets "WOW!", so other players' portals ("MINESHAFT! ... spawned nearby" alone) aren't timed.
 * The time is stored on the shaft entered next.
 *
 * <p>Session: ends after {@link #SESSION_GAP_MILLIS} without block hits or shaft activity, or
 * on {@link #newSession}. A timer running across a session break is dropped, so going AFK
 * doesn't spoil the average.
 */
public final class Journal {
	/** No activity for this long ends the session. A usability default, not a game number. */
	public static final long SESSION_GAP_MILLIS = 15 * 60_000L;

	/** Totals for one session. */
	public record Session(long id, int shafts, int timedSpawns, long searchMillis) {
		/** Null until a spawn has been timed. */
		public Long averageSearchMillis() {
			return timedSpawns == 0 ? null : searchMillis / timedSpawns;
		}

		Session add(JournalEntry entry) {
			Long search = entry.searchMillis();
			return new Session(id, shafts + 1, timedSpawns + (search == null ? 0 : 1),
					searchMillis + (search == null ? 0 : search));
		}
	}

	private final TextPatterns patterns;
	private final Consumer<JournalEntry> listener;
	private Session session;
	private long lastActivity;
	private long searchStartedAt = -1;
	private Long timedSearch;

	public Journal(TextPatterns patterns, Consumer<JournalEntry> listener) {
		this.patterns = patterns;
		this.listener = listener;
	}

	/** The current session, or null before any activity. */
	public Session session() {
		return session;
	}

	/**
	 * Continues the last saved session after a restart, as if the game had stayed open. Entries
	 * are in file order. Does nothing once a session is running.
	 */
	public void resume(List<JournalEntry> entries) {
		if (entries.isEmpty() || session != null) return;
		JournalEntry last = entries.getLast();
		Session resumed = new Session(last.sessionId(), 0, 0, 0);
		for (JournalEntry entry : entries) {
			if (entry.sessionId() == last.sessionId()) resumed = resumed.add(entry);
		}
		session = resumed;
		lastActivity = last.enteredAt() + last.durationMillis();
	}

	/** True while the spawn timer is running. */
	public boolean searchRunning() {
		return searchStartedAt >= 0;
	}

	/** The player started or kept hitting a block. */
	public void onBlockHit(long now, boolean inMiningArea) {
		touch(now);
		if (inMiningArea && searchStartedAt < 0) searchStartedAt = now;
	}

	public void onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PortalSpawned spawned when spawned.foundByYou() -> {
				touch(now);
				timedSearch = searchStartedAt < 0 ? null : now - searchStartedAt;
				searchStartedAt = -1;
			}
			case ShaftEntered entered -> {
				touch(now);
				searchStartedAt = -1;
			}
			case ShaftLeft left -> {
				touch(now);
				JournalEntry entry = JournalEntry.of(left.shaft(), session.id(), timedSearch, patterns);
				timedSearch = null;
				session = session.add(entry);
				listener.accept(entry);
			}
			default -> {
			}
		}
	}

	public void newSession(long now) {
		session = new Session(now, 0, 0, 0);
		lastActivity = now;
		searchStartedAt = -1;
		timedSearch = null;
	}

	private void touch(long now) {
		if (session == null || now - lastActivity > SESSION_GAP_MILLIS) {
			newSession(now);
		} else {
			lastActivity = now;
		}
	}
}
