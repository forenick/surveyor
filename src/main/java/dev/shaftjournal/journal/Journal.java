package dev.shaftjournal.journal;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.shaftjournal.detection.ShaftEvent;
import dev.shaftjournal.detection.ShaftEvent.PickaxeAbility;
import dev.shaftjournal.detection.ShaftEvent.PortalSpawned;
import dev.shaftjournal.detection.ShaftEvent.ShaftEntered;
import dev.shaftjournal.detection.ShaftEvent.ShaftLeft;
import dev.shaftjournal.detection.TextPatterns;

/**
 * Turns finished shafts into {@link JournalEntry}s, and keeps the session and the spawn timer.
 *
 * <p>Spawn timer: counts mining time in the mining area, from the first mining after the previous
 * shaft (or the session start) to "WOW! You found a Glacite Mineshaft portal!". Mining is a block
 * hit or a pickaxe ability such as Pickobulus. A gap longer than {@link #PAUSE_MILLIS} without
 * mining only counts for its first {@code PAUSE_MILLIS}, so breaks don't inflate the time. Only
 * the finder gets "WOW!", so other players' portals ("MINESHAFT! ... spawned nearby" alone) aren't
 * timed. The time is stored on the shaft entered next.
 *
 * <p>Session: ends after {@link #SESSION_GAP_MILLIS} without mining or shaft activity, or on
 * {@link #newSession}. A timer running across a session break is dropped.
 */
public final class Journal {
	/** No activity for this long ends the session. A usability default, not a game number. */
	public static final long SESSION_GAP_MILLIS = 15 * 60_000L;

	/**
	 * Longer gaps without mining are a break for the spawn timer. Nick's choice (2026-11-11): just
	 * above his 32 s Pickobulus cooldown, so a Pickobulus-only rhythm still counts (Docs/research/08).
	 * Also how long a found portal may go unentered before the next mining starts a new timer.
	 */
	public static final long PAUSE_MILLIS = 40_000L;

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
	private final BooleanSupplier inMiningArea;
	private final Consumer<JournalEntry> listener;
	private Session session;
	private long lastActivity;
	private boolean searching;
	private long searchMillis;
	private long lastMiningAt;
	private long portalFoundAt = -1;
	private Long timedSearch;

	public Journal(TextPatterns patterns, BooleanSupplier inMiningArea, Consumer<JournalEntry> listener) {
		this.patterns = patterns;
		this.inMiningArea = inMiningArea;
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
		return searching;
	}

	/** The player started or kept hitting a block. */
	public void onBlockHit(long now) {
		mining(now);
	}

	public void onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PickaxeAbility ability -> mining(now);
			case PortalSpawned spawned when spawned.foundByYou() -> {
				touch(now);
				timedSearch = searching ? searchMillis + Math.min(now - lastMiningAt, PAUSE_MILLIS) : null;
				searching = false;
				portalFoundAt = now;
			}
			case ShaftEntered entered -> {
				touch(now);
				searching = false;
				portalFoundAt = -1;
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
		searching = false;
		portalFoundAt = -1;
		timedSearch = null;
	}

	private void mining(long now) {
		touch(now);
		if (!inMiningArea.getAsBoolean()) return;
		// Mining between finding a portal and entering it belongs to neither search.
		if (portalFoundAt >= 0 && now - portalFoundAt <= PAUSE_MILLIS) return;
		if (searching) {
			searchMillis += Math.min(now - lastMiningAt, PAUSE_MILLIS);
		} else {
			searching = true;
			searchMillis = 0;
		}
		lastMiningAt = now;
	}

	private void touch(long now) {
		if (session == null || now - lastActivity > SESSION_GAP_MILLIS) {
			newSession(now);
		} else {
			lastActivity = now;
		}
	}
}
