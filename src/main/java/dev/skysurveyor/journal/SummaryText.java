package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.Shaft.ExitReason;
import dev.skysurveyor.money.CoinBreakdown;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.util.Format;

public final class SummaryText {
	private static final String RULE = "§8§m                                                  ";
	private static final Map<Part, String> PART_NAMES = Map.of(Part.MINED, "gems", Part.OTHER, "other",
			Part.CORPSES, "corpses", Part.SCRAP, "scrap", Part.CRYSTALS, "crystals", Part.KEYS, "keys",
			Part.CAMPFIRES, "campfires");

	private SummaryText() {
	}

	// session already incl entry, null = no session line. value null = no prices yet
	public static List<String> lines(JournalEntry entry, Journal.Session session, ShaftValue value, boolean ironman) {
		List<String> lines = new ArrayList<>();
		lines.add(RULE);
		lines.add(" §b§l" + (entry.shaftId() == null ? "Mineshaft" : entry.shaftId())
				+ "  §7inside §f" + Format.duration(entry.durationMillis()) + exit(entry.exitReason()));
		lines.add(" §7Spawned in " + (entry.searchMillis() == null ? "§8not timed" : "§f" + Format.duration(entry.searchMillis())));

		if (!entry.corpses().isEmpty()) lines.add(" §7Corpses " + corpses(entry.corpses()));
		if (entry.gems() != null) {
			lines.add(" §7Gems §f≈" + Format.decimal(entry.gems().fineEquivalent())
					+ " Fine " + entry.gems().gem() + " §8(" + Format.number(entry.pristineProcs()) + " pristine procs)");
		}
		long corpsePowder = entry.corpsePowder();
		long powder = corpsePowder + entry.exitPowder();
		if (powder > 0) {
			String parts = corpsePowder > 0 && entry.exitPowder() > 0
					? " §8(corpses " + Format.number(corpsePowder) + ", exit " + Format.number(entry.exitPowder()) + ")"
					: "";
			lines.add(" §7Powder §f+" + Format.number(powder) + parts);
		}
		if (!entry.campfiresAt().isEmpty()) lines.add(" §7Campfires §f" + entry.campfiresAt().size());
		String coins = coins(value, ironman);
		if (coins != null) lines.add(coins);

		if (session != null) lines.add(" §7Session " + session(session));
		lines.add(RULE);
		return lines;
	}

	// chat + toast
	static String session(Journal.Session session) {
		String line = "§f" + session.shafts() + (session.shafts() == 1 ? " shaft" : " shafts");
		Long average = session.averageSearchMillis();
		if (average != null) line += "§7, avg spawn §f" + Format.duration(average);
		return line;
	}

	static String coins(ShaftValue value, boolean ironman) {
		if (value == null) return " §7Coins §8no prices yet";
		if (value.lines().isEmpty()) return null;
		double net = value.net();
		String line = " §7Coins " + (net < 0 ? "§c" : "§6") + Coins.signed(net);
		List<String> parts = new ArrayList<>();
		for (Part part : Part.values()) {
			double total = value.total(part);
			if (Math.round(total) != 0) parts.add(PART_NAMES.get(part) + " " + Coins.format(total));
		}
		// 1 part = same as the total
		if (parts.size() > 1) line += " §8(" + String.join(", ", parts) + ")";
		int unpriced = value.unpriced().size();
		if (unpriced > 0) line += " " + CoinBreakdown.unpriced(unpriced, ironman);
		return line;
	}

	static String corpses(List<Corpse> corpses) {
		List<String> parts = new ArrayList<>();
		JournalStats.corpseCounts(corpses).forEach((type, count) ->
				parts.add((type.equals(CorpseType.LAPIS.label()) ? "§9" : "§f") + type + " " + count.looted() + "/" + count.total()));
		return String.join("§7, ", parts);
	}

	private static String exit(ExitReason exit) {
		return switch (exit) {
			case DEATH, WARP -> " §8(" + exitName(exit).toLowerCase(Locale.ROOT) + ")";
			default -> "";
		};
	}

	public static String exitName(ExitReason exit) {
		return switch (exit) {
			case BYE -> "Took the exit";
			case WARP -> "Warped out";
			case DEATH -> "Froze";
			default -> "Unknown";
		};
	}
}
