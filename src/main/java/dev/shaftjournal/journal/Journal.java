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

// only the finder gets "WOW!", other peoples portals arent timed
public final class Journal {
	// default, not a game number
	public static final long SESSION_GAP_MILLIS = 15 * 60_000L;

	// nicks pick, just > 32s pickobulus cd (research/08)
	// also how long a found portal can sit unentered
	public static final long PAUSE_MILLIS = 40_000L;

	public record Session(long id, int shafts, int timedSpawns, long searchMillis) {
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

	public Session session() {
		return session;
	}

	// session only really ends at the next activity
	public Session activeSession(long now) {
		return session == null || now - lastActivity > SESSION_GAP_MILLIS ? null : session;
	}

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

	public boolean searchRunning() {
		return searching;
	}

	// null once session ran out, next activity drops that timer anyway
	public Long spawnTimerMillis(long now) {
		if (activeSession(now) == null) return null;
		if (searching) return searchMillis + Math.max(0, Math.min(now - lastMiningAt, PAUSE_MILLIS));
		return timedSearch;
	}

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
		// mining between finding and entering belongs to neither search
		if (portalFoundAt >= 0 && now - portalFoundAt <= PAUSE_MILLIS) return;
		if (searching) {
			searchMillis += Math.min(now - lastMiningAt, PAUSE_MILLIS);
		} else {
			searching = true;
			searchMillis = 0;
			// found portal not entered in time isnt part of the next shaft
			timedSearch = null;
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
