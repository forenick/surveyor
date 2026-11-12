package dev.shaftjournal.hud;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.shaftjournal.hud.HudValue.Kind;

public final class HudFormat {
	public static final String MISSING = "-";

	private static final List<String> TIME = List.of("1:42", "1m 42s", "102s");
	private static final List<String> INTEGER = List.of("43,436", "43.4k", "43436");
	private static final List<String> DECIMAL = List.of("19.4", "19", "19.42");
	private static final List<String> COLD = List.of("-12❄");
	private static final List<String> TEXT = List.of("Text");
	// same order as the lists above
	private static final List<String> TIME_NAMES = List.of("clock", "minutes", "seconds");
	private static final List<String> INTEGER_NAMES = List.of("comma", "short", "plain");
	private static final List<String> DECIMAL_NAMES = List.of("tenths", "whole", "hundredths");
	private static final Pattern CLOCK = Pattern.compile("(-?)(\\d+):(\\d{2})");
	private static final Pattern GROUPED = Pattern.compile("-?\\d{1,3}(,\\d{3})+(\\.\\d+)?");

	private HudFormat() {
	}

	public static List<String> options(Kind kind) {
		return switch (kind) {
			case TIME -> TIME;
			case INTEGER -> INTEGER;
			case DECIMAL -> DECIMAL;
			case COLD -> COLD;
			case TEXT -> TEXT;
		};
	}

	public static List<String> names(Kind kind) {
		return switch (kind) {
			case TIME -> TIME_NAMES;
			case INTEGER -> INTEGER_NAMES;
			case DECIMAL -> DECIMAL_NAMES;
			case COLD, TEXT -> List.of();
		};
	}

	// "43,436" = thousands, "2,5" = decimal. times in seconds
	public static Double parseThreshold(Kind kind, String text) {
		String t = text.strip();
		try {
			if (kind == Kind.TIME) {
				Matcher clock = CLOCK.matcher(t);
				if (clock.matches()) {
					long seconds = Long.parseLong(clock.group(3));
					if (seconds >= 60) return null;
					double total = Long.parseLong(clock.group(2)) * 60.0 + seconds;
					return clock.group(1).isEmpty() ? total : -total;
				}
			}
			if (GROUPED.matcher(t).matches()) t = t.replace(",", "");
			else if (t.indexOf(',') == t.lastIndexOf(',') && t.indexOf('.') < 0) t = t.replace(',', '.');
			// no "1e5", "NaN" or "1d"
			if (!t.matches("-?(\\d+\\.?\\d*|\\.\\d+)")) return null;
			return Double.parseDouble(t);
		} catch (NumberFormatException e) {
			// too many digits for a long
			return null;
		}
	}

	public static String thresholdText(Kind kind, double value) {
		if (kind == Kind.TIME && value == Math.rint(value) && value >= 0) return time((long) value, 0);
		return value == Math.rint(value) && Math.abs(value) < 1e15 ? Long.toString((long) value) : Double.toString(value);
	}

	public static String format(Kind kind, int format, Object value) {
		if (value == null) return MISSING;
		int f = format < 0 || format >= options(kind).size() ? 0 : format;
		return switch (kind) {
			case TIME -> time(((Number) value).longValue() / 1000, f);
			case INTEGER -> integer(((Number) value).longValue(), f);
			case DECIMAL -> decimal(((Number) value).doubleValue(), f);
			case COLD -> ((Number) value).longValue() + "❄";
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
			// 999,950 and up = 1.0M, not 1000.0k
			case 1 -> Math.abs(value) >= 999_950 ? one(value / 1e6) + "M"
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
