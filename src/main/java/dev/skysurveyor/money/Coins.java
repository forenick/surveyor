package dev.skysurveyor.money;

import dev.skysurveyor.util.Format;

public final class Coins {
	private Coins() {
	}

	// 999,950 and up = 1.0M, not 1000.0k
	public static String format(double coins) {
		double abs = Math.abs(coins);
		if (abs >= 999_950_000) return one(coins / 1e9) + "B";
		if (abs >= 999_950) return one(coins / 1e6) + "M";
		if (abs >= 999.5) return one(coins / 1e3) + "k";
		return Long.toString(Math.round(coins));
	}

	// no .0, f.e. 1.0M = 1M on chart axes
	public static String whole(double coins) {
		String text = format(coins);
		int suffix = text.length() - 1;
		return text.startsWith(".0", suffix - 2) && !Character.isDigit(text.charAt(suffix)) ? text.substring(0, suffix - 2) + text.charAt(suffix) : text;
	}

	public static String signed(double coins) {
		return (Math.round(coins) > 0 ? "+" : "") + format(coins);
	}

	private static String one(double value) {
		return Format.decimal(value);
	}
}
