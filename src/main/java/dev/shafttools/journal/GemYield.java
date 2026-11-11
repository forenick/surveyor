package dev.shafttools.journal;

import java.util.Map;
import java.util.regex.Matcher;

import dev.shafttools.detection.Shaft;
import dev.shafttools.detection.TextPatterns;

/**
 * The gems of the shaft's own type that were mined in it, in items.
 *
 * <p>Source: the "[Sacks]" Added hover lines, minus corpse loot and Supercraft output that also
 * went into the sacks (Docs/research/06 and 07). The last seconds before leaving are never
 * reported in Sacks, so the tail is estimated: Flawed from the PRISTINE messages after the last
 * Sacks message, and Rough from that Flawed at the shaft's own measured Rough:Flawed ratio.
 * Silent Gemstone Spread procs in the tail are missing, so the tail is a slight undercount.
 * Mob drops of the same gem would count as mined (seen once, Docs/research/07).
 *
 * @param gem e.g. "Jade"
 */
public record GemYield(String gem, long rough, long flawed, long fine, long tailRough, long tailFlawed) {
	/** 80 Rough make 1 Flawed and 80 Flawed make 1 Fine (standard recipes, Docs/research/05). */
	public static final int RECIPE = 80;

	/** Everything, including the estimated tail, as Fine. */
	public double fineEquivalent() {
		return toFine(rough + tailRough, flawed + tailFlawed, fine);
	}

	/** Only what the Sacks messages reported, as Fine. */
	public double reportedFine() {
		return toFine(rough, flawed, fine);
	}

	private static double toFine(long rough, long flawed, long fine) {
		return rough / (double) (RECIPE * RECIPE) + flawed / (double) RECIPE + fine;
	}

	/** Null when the shaft has no ID yet or no gems of its type were found (e.g. TITA_1). */
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
