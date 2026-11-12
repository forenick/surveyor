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

// turns finished shafts into JournalEntrys, and keeps the session and spawn timer
// spawn timer: counts mining time in the mining area, from the first mining after the last shaft
// (or session start) up to "WOW! You found a Glacite Mineshaft portal!". mining = a block hit or
// a pickaxe ability like Pickobulus. a gap longer than PAUSE_MILLIS w/o mining only counts for its
// first PAUSE_MILLIS so breaks dont blow up the time. only the finder gets "WOW!", so other
// peoples portals ("MINESHAFT! ... spawned nearby" alone) arent timed. the time goes on the next
// shaft u enter
// session ends after SESSION_GAP_MILLIS w/o mining or shaft stuff, or on newSession. a timer
// running across a session break gets dropped
public final class Journal {
	// no activity for this long ends the session. just a default that felt ok, not a game number
	public static final long SESSION_GAP_MILLIS = 15 * 60_000L;

	// longer gaps w/o mining count as a break for the spawn timer. i picked this (2026-11-11), just
	// above my 32 s Pickobulus cooldown so only using Pickobulus still counts (Docs/research/08).
	// also how long a found portal can sit unentered before the next mining starts a new timer
	public static final long PAUSE_MILLIS = 40_000L;

	public record Session(long id, int shafts, int timedSpawns, long searchMillis) {
		// null until a spawn got timed
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

	// null before any activity
	public Session session() {
		return session;
	}

	// null when there is none or it ran out. the session only really ends at the next activity,
	// but after SESSION_GAP_MILLIS its already over for display
	public Session activeSession(long now) {
		return session == null || now - lastActivity > SESSION_GAP_MILLIS ? null : session;
	}

	// carries on the last saved session after a restart, like the game stayed open. entries are in
	// file order. does nothing once a session is running
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

	// what the HUD spawn timer shows. while searching its the time so far (a break counts for max
	// PAUSE_MILLIS, same as when the shaft is found). after "WOW!" its the timed spawn until u leave
	// that shaft, otherwise null. also null once the session ran out, bc the next activity starts a
	// new session and drops that timer anyway
	public Long spawnTimerMillis(long now) {
		if (activeSession(now) == null) return null;
		if (searching) return searchMillis + Math.max(0, Math.min(now - lastMiningAt, PAUSE_MILLIS));
		return timedSearch;
	}

	// player started or kept hitting a block
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
		// mining between finding a portal and entering it doesnt belong to either search
		if (portalFoundAt >= 0 && now - portalFoundAt <= PAUSE_MILLIS) return;
		if (searching) {
			searchMillis += Math.min(now - lastMiningAt, PAUSE_MILLIS);
		} else {
			searching = true;
			searchMillis = 0;
			// a portal found earlier and not entered in time isnt part of the next shaft
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
