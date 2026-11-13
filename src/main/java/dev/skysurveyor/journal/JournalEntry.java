package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.money.ItemPrice;

// times epoch ms. new fields at the end, bump VERSION if a meaning changes
// sessionId = session start time, campfiresAt relative to enteredAt
// searchMillis null = not timed (someone elses portal or no block mined)
// crystals, otherDrops, prices null in entries before 0.14.0
// prices = by skyblock id at the end of the shaft, empty when there were none
// campfireColds = cold before each campfire use, null in entries before 0.22.0, null items = no cold line
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
		Map<String, ItemPrice> prices,
		List<Integer> campfireColds) {
	public static final int VERSION = 1;

	public static JournalEntry of(Shaft shaft, long sessionId, Long searchMillis, TextPatterns patterns) {
		return new JournalEntry(VERSION, sessionId, shaft.id() == null ? null : shaft.id().id(),
				shaft.enteredAt(), shaft.durationMillis(),
				// null = still inside (live coins)
				shaft.exitReason() == null ? null : shaft.exitReason().name(), shaft.exitPowder(), searchMillis,
				List.copyOf(shaft.corpses()), shaft.extraCorpses(), List.copyOf(shaft.loots()),
				List.copyOf(shaft.mayhemBuffs()),
				shaft.campfiresPlaced().stream().map(at -> at - shaft.enteredAt()).toList(),
				shaft.cold(), shaft.scrapFound(), shaft.pristineProcs(),
				new LinkedHashMap<>(shaft.pristineItems()), new LinkedHashMap<>(shaft.sacksAdded()), new LinkedHashMap<>(shaft.sacksRemoved()),
				GemYield.of(shaft, patterns), List.copyOf(shaft.crystals()), otherDrops(shaft, patterns), null,
				// list.copyOf hates nulls
				Collections.unmodifiableList(new ArrayList<>(shaft.campfireColds())));
	}

	public JournalEntry withPrices(Map<String, ItemPrice> prices) {
		return new JournalEntry(version, sessionId, shaftId, enteredAt, durationMillis, exit, exitPowder, searchMillis,
				corpses, extraCorpses, loots, mayhemBuffs, campfiresAt, coldAtExit, scrapFound, pristineProcs,
				pristineItems, sacksAdded, sacksRemoved, gems, crystals, otherDrops, prices, campfireColds);
	}

	// sacks minus corpse loot, supercrafts and the shafts own gem (thats GemYield)
	static Map<String, Long> otherDrops(Shaft shaft, TextPatterns patterns) {
		Map<String, Long> drops = new LinkedHashMap<>(shaft.sacksAdded());
		shaft.notMinedInSacks().forEach((item, count) -> drops.merge(item, -count, Long::sum));
		String type = shaft.id() == null ? null : shaft.id().type();
		drops.entrySet().removeIf(e -> e.getValue() <= 0 || GemYield.isShaftGem(e.getKey(), type, patterns));
		return drops;
	}

	// null = no id seen
	public String shaftType() {
		return idPart("type");
	}

	// f.e. "1", "2", "C" crystal, "L" littlefoot
	public String variant() {
		return idPart("variant");
	}

	private String idPart(String group) {
		if (shaftId == null) return null;
		Matcher m = TextPatterns.defaults().match("shaft.id", shaftId);
		return m == null ? null : m.group(group);
	}

	// null = still inside, unknown name = UNKNOWN
	public Shaft.ExitReason exitReason() {
		if (exit == null) return null;
		for (Shaft.ExitReason reason : Shaft.ExitReason.values()) {
			if (reason.name().equals(exit)) return reason;
		}
		return Shaft.ExitReason.UNKNOWN;
	}

	public long corpsePowder() {
		return loots.stream().mapToLong(CorpseLoot::powder).sum();
	}
}
