package dev.shaftjournal.money;

import java.util.ArrayList;
import java.util.List;

import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.journal.JournalStats.SessionSummary;
import dev.shaftjournal.journal.SummaryText;

// tooltips for the stats charts' avg lines, "" = small gap
public final class ChartTips {
	private ChartTips() {
	}

	// coins null = no prices, perHour null = under the rate minimum
	public record SessionCoins(Double coins, long millis, Double perHour) {
	}

	// avg = every timed shaft, not the avg of the session avgs
	public static List<String> spawn(List<SessionSummary> oldestFirst) {
		List<SessionSummary> timed = oldestFirst.stream().filter(s -> s.averageSearchMillis() != null).toList();
		List<Long> shafts = timed.stream().flatMap(s -> s.entries().stream()).map(JournalEntry::searchMillis)
				.filter(m -> m != null).sorted().toList();
		List<String> lines = new ArrayList<>();
		if (shafts.isEmpty()) return lines;
		long average = Math.round(shafts.stream().mapToLong(Long::longValue).average().orElse(0));
		lines.add("§fAvg spawn §e" + SummaryText.duration(average));
		lines.add("");
		lines.add("§7From §f" + shafts.size() + " §7timed shafts in §f" + timed.size() + (timed.size() == 1 ? " §7session" : " §7sessions"));
		lines.add("§7Median shaft §f" + SummaryText.duration(shafts.get(shafts.size() / 2)));
		if (shafts.size() >= 4) {
			lines.add("§7Middle half §f" + SummaryText.duration(shafts.get(shafts.size() / 4)) + " §7to §f"
					+ SummaryText.duration(shafts.get(shafts.size() * 3 / 4)));
		}
		lines.add("");
		long fasterSessions = timed.stream().filter(s -> s.averageSearchMillis() < average).count();
		lines.add("§7Sessions faster than avg §f" + fasterSessions + " of " + timed.size() + " §8(" + percent(fasterSessions, timed.size()) + ")");
		long fasterShafts = shafts.stream().filter(m -> m < average).count();
		lines.add("§7Shafts faster than avg §f" + percent(fasterShafts, shafts.size()));
		long fastest = timed.stream().mapToLong(SessionSummary::averageSearchMillis).min().orElse(0);
		long slowest = timed.stream().mapToLong(SessionSummary::averageSearchMillis).max().orElse(0);
		if (timed.size() > 1) {
			lines.add("§7Fastest session §a" + SummaryText.duration(fastest) + "§7, slowest §c" + SummaryText.duration(slowest));
		}
		if (timed.size() > 1 && average > 0) {
			long latest = timed.getLast().averageSearchMillis();
			double change = (average - latest) / (double) average;
			lines.add("");
			lines.add("§7Latest session §f" + SummaryText.duration(latest) + " §7(" + (Math.round(change * 100) == 0 ? "same as avg"
					: change > 0 ? "§a" + percent(change) + " faster§7" : "§c" + percent(-change) + " slower§7") + ")");
		}
		return lines;
	}

	public static List<String> coins(List<SessionCoins> oldestFirst, long totalMillis) {
		List<String> lines = new ArrayList<>();
		double total = 0;
		boolean priced = false;
		for (SessionCoins session : oldestFirst) {
			if (session.coins() == null) continue;
			total += session.coins();
			priced = true;
		}
		Double average = priced ? JournalStats.perHour(total, totalMillis) : null;
		List<SessionCoins> rated = oldestFirst.stream().filter(s -> s.perHour() != null).toList();
		if (average == null || rated.isEmpty()) return lines;
		lines.add("§fAvg coins / h " + CoinBreakdown.coins(average));
		lines.add("");
		lines.add("§7Coins " + CoinBreakdown.coins(total) + " §7in §f" + SummaryText.duration(totalMillis) + " §8(total / time shafting)");
		List<Double> rates = rated.stream().map(SessionCoins::perHour).sorted().toList();
		lines.add("§7Median session " + CoinBreakdown.coins(rates.get(rates.size() / 2)) + " §7/ h");
		if (rates.size() > 1) {
			lines.add("§7Best session " + CoinBreakdown.coins(rates.getLast()) + " §7/ h, worst " + CoinBreakdown.coins(rates.getFirst()) + " §7/ h");
		}
		lines.add("");
		long above = rates.stream().filter(r -> r > average).count();
		lines.add("§7Sessions above avg §f" + above + " of " + rates.size() + " §8(" + percent(above, rates.size()) + ")");
		long earning = oldestFirst.stream().filter(s -> s.coins() != null && Math.round(s.coins()) > 0).count();
		long counted = oldestFirst.stream().filter(s -> s.coins() != null).count();
		lines.add("§7Sessions that made coins §f" + earning + " of " + counted + " §8(" + percent(earning, counted) + ")");
		if (counted > 1 && total > 0) {
			double best = oldestFirst.stream().filter(s -> s.coins() != null).mapToDouble(SessionCoins::coins).max().orElse(0);
			if (best > 0) lines.add("§7Best session made §f" + percent(best / total) + " §7of all coins");
		}
		if (rates.size() > 1 && average > 0) {
			double latest = rated.getLast().perHour();
			double change = (latest - average) / average;
			lines.add("");
			lines.add("§7Latest session " + CoinBreakdown.coins(latest) + " §7/ h (" + (Math.round(change * 100) == 0 ? "same as avg"
					: change > 0 ? "§a+" + percent(change) + "§7 vs avg" : "§c-" + percent(-change) + "§7 vs avg") + ")");
		}
		return lines;
	}

	private static String percent(long part, long of) {
		return percent(of == 0 ? 0 : part / (double) of);
	}

	private static String percent(double fraction) {
		return Math.round(fraction * 100) + "%";
	}
}
