package dev.shaftjournal.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

// ping w/o sending: vanilla block break/use packet (has a sequence) to the servers ack of it
// hypixels tab list ping is always 1. ack may wait for the server tick, up to 50ms high
// client thread only
public final class PingTimer {
	// display choice
	static final int SAMPLES = 10;
	// display choice, keeps the last ping this long after u stop mining
	static final long STALE_MILLIS = 60_000;
	// acks come in order, this only caps lost ones
	private static final int MAX_PENDING = 64;

	private record Sent(int sequence, long time) {
	}

	private final ArrayDeque<Sent> pending = new ArrayDeque<>();
	private final ArrayDeque<Long> samples = new ArrayDeque<>();
	private long lastSample;

	// server switch, sequences can start over
	public void reset() {
		pending.clear();
	}

	public void onSent(int sequence, long now) {
		pending.addLast(new Sent(sequence, now));
		if (pending.size() > MAX_PENDING) pending.removeFirst();
	}

	// ack n = everything up to n, only n itself is a clean sample
	public void onAck(int sequence, long now) {
		while (!pending.isEmpty() && pending.getFirst().sequence() < sequence) pending.removeFirst();
		if (pending.isEmpty() || pending.getFirst().sequence() != sequence) return;
		if (now - lastSample > STALE_MILLIS) samples.clear();
		samples.addLast(now - pending.removeFirst().time());
		if (samples.size() > SAMPLES) samples.removeFirst();
		lastSample = now;
	}

	// median, one slow tick doesnt jump it. null = no mining lately
	public Long ping(long now) {
		if (samples.isEmpty() || now - lastSample > STALE_MILLIS) return null;
		List<Long> sorted = new ArrayList<>(samples);
		sorted.sort(null);
		return sorted.get(sorted.size() / 2);
	}
}
