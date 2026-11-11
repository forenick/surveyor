package dev.shaftjournal.hud;

import java.util.List;
import java.util.Locale;

import dev.shaftjournal.hud.HudValue.Kind;

/** The number formats a line can pick, per {@link Kind}. Plain Java. */
public final class HudFormat {
	/** Shown when a value isn't known yet, e.g. shafts per hour in the first minutes. */
	public static final String MISSING = "-";

	private static final List<String> TIME = List.of("1:42", "1m 42s", "102s");
	private static final List<String> INTEGER = List.of("43,436", "43.4k", "43436");
	private static final List<String> DECIMAL = List.of("19.4", "19", "19.42");
	private static final List<String> TEXT = List.of("Text");

	private HudFormat() {
	}

	/** The choices for this kind, each shown as an example. */
	public static List<String> options(Kind kind) {
		return switch (kind) {
			case TIME -> TIME;
			case INTEGER -> INTEGER;
			case DECIMAL -> DECIMAL;
			case TEXT -> TEXT;
		};
	}

	/** Formats a value; an unknown format index falls back to the first. */
	public static String format(Kind kind, int format, Object value) {
		if (value == null) return MISSING;
		int f = format < 0 || format >= options(kind).size() ? 0 : format;
		return switch (kind) {
			case TIME -> time(((Number) value).longValue() / 1000, f);
			case INTEGER -> integer(((Number) value).longValue(), f);
			case DECIMAL -> decimal(((Number) value).doubleValue(), f);
			case TEXT -> value.toString();
		};
	}

	private static String time(long seconds, int format) {
		return switch (format) {
			case 1 -> seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
			case 2 -> seconds + "s";
			default -> seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
		};
	}

	private static String integer(long value, int format) {
		return switch (format) {
			case 1 -> Math.abs(value) >= 1_000_000 ? one(value / 1e6) + "M"
					: Math.abs(value) >= 1_000 ? one(value / 1e3) + "k" : Long.toString(value);
			case 2 -> Long.toString(value);
			default -> String.format(Locale.ROOT, "%,d", value);
		};
	}

	private static String decimal(double value, int format) {
		return switch (format) {
			case 1 -> Long.toString(Math.round(value));
			case 2 -> String.format(Locale.ROOT, "%.2f", value);
			default -> one(value);
		};
	}

	private static String one(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
