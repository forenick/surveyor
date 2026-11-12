package dev.shaftjournal.journal;

import java.util.Map;
import java.util.regex.Matcher;

import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;

// sacks added - corpse loot - supercrafts, tail estimated (research/06, 07)
// silent gemstone spread in the tail is missing, slight undercount
// mob drops of the same gem count as mined (research/07)
public record GemYield(String gem, long rough, long flawed, long fine, long tailRough, long tailFlawed) {
	// 80:1 per step (research/05)
	public static final int RECIPE = 80;

	// incl estimated tail
	public double fineEquivalent() {
		return toFine(rough + tailRough, flawed + tailFlawed, fine);
	}

	public double reportedFine() {
		return toFine(rough, flawed, fine);
	}

	private static double toFine(long rough, long flawed, long fine) {
		return rough / (double) (RECIPE * RECIPE) + flawed / (double) RECIPE + fine;
	}

	public static GemYield of(Shaft shaft, TextPatterns patterns) {
		if (shaft.id() == null) return null;
		String type = shaft.id().type();
		Totals mined = new Totals();
		mined.add(shaft.sacksAdded(), type, patterns, 1);
		mined.add(shaft.notMinedInSacks(), type, patterns, -1);
		Totals tail = new Totals();
		tail.add(shaft.pristineSinceSacks(), type, patterns, 1);
		String gem = mined.gem != null ? mined.gem : tail.gem;
		if (gem == null) return null;

		long rough = Math.max(0, mined.rough);
		long flawed = Math.max(0, mined.flawed);
		long tailRough = flawed > 0 ? Math.round(tail.flawed * (double) rough / flawed) : 0;
		return new GemYield(gem, rough, flawed, Math.max(0, mined.fine), tailRough, tail.flawed);
	}

	private static final class Totals {
		String gem;
		long rough;
		long flawed;
		long fine;

		void add(Map<String, Long> items, String type, TextPatterns patterns, int sign) {
			items.forEach((item, count) -> {
				Matcher m = patterns.match("gem.item", item);
				if (m == null || !m.group("gem").toUpperCase().startsWith(type)) return;
				gem = m.group("gem");
				switch (m.group("quality")) {
					case "Rough" -> rough += sign * count;
					case "Flawed" -> flawed += sign * count;
					default -> fine += sign * count;
				}
			});
		}
	}
}
