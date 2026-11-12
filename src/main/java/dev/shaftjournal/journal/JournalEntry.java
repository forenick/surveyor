package dev.shaftjournal.journal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.money.ItemPrice;

// times epoch ms. new fields at the end, bump VERSION if a meaning changes
// sessionId = session start time, campfiresAt relative to enteredAt
// searchMillis null = not timed (someone elses portal or no block mined)
// crystals, otherDrops, prices null in entries before 0.14.0
// prices = by skyblock id at the end of the shaft, empty when there were none
public record JournalEntry(
		int version,
		long sessionId,
		String shaftId,
		long enteredAt,
		long durationMillis,
		String exit,
		long exitPowder,
		Long searchMillis,
		List<Corpse> corpses,
		int extraCorpses,
		List<CorpseLoot> loots,
		List<String> mayhemBuffs,
		List<Long> campfiresAt,
		Integer coldAtExit,
		int scrapFound,
		int pristineProcs,
		Map<String, Long> pristineItems,
		Map<String, Long> sacksAdded,
		Map<String, Long> sacksRemoved,
		GemYield gems,
		List<String> crystals,
		Map<String, Long> otherDrops,
		Map<String, ItemPrice> prices) {
	public static final int VERSION = 1;

	public static JournalEntry of(Shaft shaft, long sessionId, Long searchMillis, TextPatterns patterns) {
		return new JournalEntry(VERSION, sessionId, shaft.id() == null ? null : shaft.id().id(),
				shaft.enteredAt(), shaft.durationMillis(), shaft.exitReason().name(), shaft.exitPowder(), searchMillis,
				List.copyOf(shaft.corpses()), shaft.extraCorpses(), List.copyOf(shaft.loots()),
				List.copyOf(shaft.mayhemBuffs()),
				shaft.campfiresPlaced().stream().map(at -> at - shaft.enteredAt()).toList(),
				shaft.cold(), shaft.scrapFound(), shaft.pristineProcs(),
				new LinkedHashMap<>(shaft.pristineItems()), new LinkedHashMap<>(shaft.sacksAdded()), new LinkedHashMap<>(shaft.sacksRemoved()),
				GemYield.of(shaft, patterns), List.copyOf(shaft.crystals()), otherDrops(shaft, patterns), null);
	}

	public JournalEntry withPrices(Map<String, ItemPrice> prices) {
		return new JournalEntry(version, sessionId, shaftId, enteredAt, durationMillis, exit, exitPowder, searchMillis,
				corpses, extraCorpses, loots, mayhemBuffs, campfiresAt, coldAtExit, scrapFound, pristineProcs,
				pristineItems, sacksAdded, sacksRemoved, gems, crystals, otherDrops, prices);
	}

	// sacks minus corpse loot, supercrafts and the shafts own gem (thats GemYield)
	static Map<String, Long> otherDrops(Shaft shaft, TextPatterns patterns) {
		Map<String, Long> drops = new LinkedHashMap<>(shaft.sacksAdded());
		shaft.notMinedInSacks().forEach((item, count) -> drops.merge(item, -count, Long::sum));
		String type = shaft.id() == null ? null : shaft.id().type();
		drops.entrySet().removeIf(e -> e.getValue() <= 0 || GemYield.isShaftGem(e.getKey(), type, patterns));
		return drops;
	}

	public long corpsePowder() {
		return loots.stream().mapToLong(CorpseLoot::powder).sum();
	}
}
