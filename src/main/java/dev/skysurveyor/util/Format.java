package dev.skysurveyor.util;

import java.util.Locale;

public final class Format {
	private Format() {
	}

	// 75 min = "1h 15m", hud time differs on purpose
	public static String duration(long millis) {
		long seconds = Math.max(0, millis) / 1000;
		if (seconds < 60) return seconds + "s";
		if (seconds < 3600) return seconds / 60 + "m " + seconds % 60 + "s";
		return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, seconds % 3600 / 60);
	}

	public static String number(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	public static String decimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
