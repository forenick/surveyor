package dev.skysurveyor.hud;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.hud.HudValue.Kind;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.util.Format;

public final class HudFormat {
	public static final String MISSING = "-";

	// spreadsheet style codes, nicks pick (2026-11-12)
	private static final List<String> TIME = List.of("m:ss", "m\"m\" s\"s\"", "s\"s\"");
	private static final List<String> INTEGER = List.of("#,##0", "0.0k", "0");
	private static final List<String> DECIMAL = List.of("0.0", "0", "0.00");
	private static final List<String> COLD = List.of("0❄");
	private static final List<String> COINS = List.of("0.0M", "#,##0", "0");
	private static final List<String> TEXT = List.of("Text");
	private static final List<List<String>> TIME_EXAMPLES = List.of(List.of("1:42"), List.of("1m 42s"), List.of("102s"));
	private static final List<List<String>> INTEGER_EXAMPLES = List.of(List.of("43,436"), List.of("43.4k", "1.2M"),
			List.of("43436"));
	private static final List<List<String>> DECIMAL_EXAMPLES = List.of(List.of("19.4"), List.of("19"), List.of("19.42"));
	private static final List<List<String>> COINS_EXAMPLES = List.of(List.of("43.4k", "1.2M"), List.of("1,204,310"),
			List.of("1204310"));
	// same order as the lists above
	private static final List<String> TIME_NAMES = List.of("clock", "minutes", "seconds");
	private static final List<String> INTEGER_NAMES = List.of("comma", "short", "plain");
	private static final List<String> DECIMAL_NAMES = List.of("tenths", "whole", "hundredths");
	private static final List<String> COINS_NAMES = List.of("short", "comma", "plain");
	// 1.5M, 250k, 2b
	private static final Pattern SHORT = Pattern.compile("(-?(?:\\d+\\.?\\d*|\\.\\d+))([kmb])");
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
			case COINS -> COINS;
			case TEXT -> TEXT;
		};
	}

	// suffix = unit or /h, see HudBoxText.suffix
	public static String example(Kind kind, int format, String suffix) {
		List<List<String>> examples = switch (kind) {
			case TIME -> TIME_EXAMPLES;
			case INTEGER -> INTEGER_EXAMPLES;
			case DECIMAL -> DECIMAL_EXAMPLES;
			case COINS -> COINS_EXAMPLES;
			case COLD, TEXT -> List.of();
		};
		if (format < 0 || format >= examples.size()) return "";
		return String.join(", ", examples.get(format).stream().map(example -> example + suffix).toList());
	}

	public static List<String> names(Kind kind) {
		return switch (kind) {
			case TIME -> TIME_NAMES;
			case INTEGER -> INTEGER_NAMES;
			case DECIMAL -> DECIMAL_NAMES;
			case COINS -> COINS_NAMES;
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
			if (kind == Kind.COINS) {
				Matcher suffixed = SHORT.matcher(t.toLowerCase(Locale.ROOT).replace(',', '.'));
				if (suffixed.matches()) {
					double unit = switch (suffixed.group(2)) {
						case "k" -> 1e3;
						case "m" -> 1e6;
						default -> 1e9;
					};
					return Math.round(Double.parseDouble(suffixed.group(1)) * unit) * 1.0;
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
		if (kind == Kind.COINS) {
			// back to 2.4M when thats exact
			for (double unit : new double[] {1e9, 1e6, 1e3}) {
				double tenths = value * 10 / unit;
				if (Math.abs(value) < unit) continue;
				if (tenths != Math.rint(tenths)) break;
				String number = tenths % 10 == 0 ? Long.toString((long) (tenths / 10)) : one(tenths / 10);
				return number + (unit == 1e9 ? "B" : unit == 1e6 ? "M" : "k");
			}
		}
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
			case COINS -> coins(((Number) value).doubleValue(), f);
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
			default -> Format.number(value);
		};
	}

	private static String coins(double value, int format) {
		return switch (format) {
			case 1 -> Format.number(Math.round(value));
			case 2 -> Long.toString(Math.round(value));
			default -> Coins.format(value);
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
		return Format.decimal(value);
	}
}
