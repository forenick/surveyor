package dev.shaftjournal.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.shaftjournal.detection.Corpse;

/** The chat summary shown after leaving a shaft, as "§"-coded lines. */
public final class SummaryText {
	private static final String RULE = "§8§m                                                  ";

	private SummaryText() {
	}

	/** {@code session} already includes {@code entry}; null to leave the session line out. */
	public static List<String> lines(JournalEntry entry, Journal.Session session) {
		List<String> lines = new ArrayList<>();
		lines.add(RULE);
		lines.add(" §b§l" + (entry.shaftId() == null ? "Mineshaft" : entry.shaftId())
				+ "  §7inside §f" + duration(entry.durationMillis()) + exit(entry.exit()));
		lines.add(" §7Spawned in " + (entry.searchMillis() == null ? "§8not timed" : "§f" + duration(entry.searchMillis())));

		if (!entry.corpses().isEmpty()) lines.add(" §7Corpses " + corpses(entry.corpses()));
		if (entry.gems() != null) {
			lines.add(" §7Gems §f≈" + decimal(entry.gems().fineEquivalent())
					+ " Fine " + entry.gems().gem() + " §8(" + number(entry.pristineProcs()) + " pristine procs)");
		}
		long corpsePowder = entry.corpsePowder();
		long powder = corpsePowder + entry.exitPowder();
		if (powder > 0) {
			String parts = corpsePowder > 0 && entry.exitPowder() > 0
					? " §8(corpses " + number(corpsePowder) + ", exit " + number(entry.exitPowder()) + ")"
					: "";
			lines.add(" §7Powder §f+" + number(powder) + parts);
		}
		if (!entry.campfiresAt().isEmpty()) lines.add(" §7Campfires §f" + entry.campfiresAt().size());

		if (session != null) {
			String line = " §7Session §f" + session.shafts() + (session.shafts() == 1 ? " shaft" : " shafts");
			Long average = session.averageSearchMillis();
			if (average != null) line += "§7, avg spawn §f" + duration(average);
			lines.add(line);
		}
		lines.add(RULE);
		return lines;
	}

	/** E.g. "Lapis 2/2, Tungsten 0/1": looted out of total, per type, in tab list order. */
	static String corpses(List<Corpse> corpses) {
		List<String> parts = new ArrayList<>();
		JournalStats.corpseCounts(corpses).forEach((type, count) ->
				parts.add((type.equals("Lapis") ? "§9" : "§f") + type + " " + count.looted() + "/" + count.total()));
		return String.join("§7, ", parts);
	}

	private static String exit(String exit) {
		return switch (exit) {
			case "DEATH", "WARP" -> " §8(" + exitName(exit).toLowerCase(Locale.ROOT) + ")";
			default -> "";
		};
	}

	/** How the shaft was left, for display: "Took the exit", "Warped out", "Froze" or "Unknown". */
	public static String exitName(String exit) {
		return switch (exit) {
			case "BYE" -> "Took the exit";
			case "WARP" -> "Warped out";
			case "DEATH" -> "Froze";
			default -> "Unknown";
		};
	}

	/** E.g. "45s", "12m 27s", "1h 03m". */
	public static String duration(long millis) {
		long seconds = Math.max(0, millis) / 1000;
		if (seconds < 60) return seconds + "s";
		if (seconds < 3600) return seconds / 60 + "m " + seconds % 60 + "s";
		return String.format(Locale.ROOT, "%dh %02dm", seconds / 3600, seconds % 3600 / 60);
	}

	/** E.g. "12,060". */
	public static String number(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	/** One decimal, e.g. "19.4". */
	public static String decimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
