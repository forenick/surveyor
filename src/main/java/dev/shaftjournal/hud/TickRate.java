package dev.shaftjournal.hud;

import java.util.ArrayDeque;

// skyhanni's method: hypixel anticheat sends 1 ping packet per server tick. unconfirmed in our recordings
// client thread only
public final class TickRate {
	// skyhanni's numbers
	static final long WARMUP_MILLIS = 5_000;
	static final long STALE_MILLIS = 1_000;
	static final int GAPS = 100;
	// display choice, ~1s so the first number isnt noise
	static final int MIN_GAPS = 20;
	private static final double MAX_TPS = 20;

	private final ArrayDeque<Long> times = new ArrayDeque<>();
	private long since;
	private int lastId;
	private boolean hasId;

	// world change, join burst would read as 20+
	public void reset(long now) {
		times.clear();
		since = now;
		hasId = false;
	}

	public void onPing(int id, long now) {
		// same id twice = not a new tick
		if (hasId && id == lastId) return;
		lastId = id;
		hasId = true;
		if (now - since < WARMUP_MILLIS) return;
		times.addLast(now);
		if (times.size() > GAPS + 1) times.removeFirst();
	}

	// null = no ticks seen (not hypixel, warming up), 0 = server stopped sending
	public Double tps(long now) {
		if (times.size() < MIN_GAPS + 1) return null;
		if (now - times.getLast() > STALE_MILLIS) return 0.0;
		long span = times.getLast() - times.getFirst();
		if (span <= 0) return MAX_TPS;
		return Math.min(MAX_TPS, (times.size() - 1) * 1000.0 / span);
	}
}
