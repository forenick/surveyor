package dev.shaftjournal.journal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.shaftjournal.detection.Corpse;
import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;

// one finished shaft visit as saved in the journal file. times are epoch ms, durations ms too.
// add new fields at the end and bump VERSION when the meaning of one changes
// sessionId = start time of the session this shaft is in
// shaftId f.e. "JADE_1", null if the scoreboard never showed it
// exit is a Shaft.ExitReason name
// searchMillis goes from the first block mined to "WOW! You found a Glacite Mineshaft portal!",
// null when the spawn wasnt timed (someone elses portal, or no block mined)
// campfiresAt = when each campfire got placed, relative to entering
// gems is null when no gems of the shafts type were found
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
		GemYield gems) {
	public static final int VERSION = 1;

	public static JournalEntry of(Shaft shaft, long sessionId, Long searchMillis, TextPatterns patterns) {
		return new JournalEntry(VERSION, sessionId, shaft.id() == null ? null : shaft.id().id(),
				shaft.enteredAt(), shaft.durationMillis(), shaft.exitReason().name(), shaft.exitPowder(), searchMillis,
				List.copyOf(shaft.corpses()), shaft.extraCorpses(), List.copyOf(shaft.loots()),
				List.copyOf(shaft.mayhemBuffs()),
				shaft.campfiresPlaced().stream().map(at -> at - shaft.enteredAt()).toList(),
				shaft.cold(), shaft.scrapFound(), shaft.pristineProcs(),
				new LinkedHashMap<>(shaft.pristineItems()), new LinkedHashMap<>(shaft.sacksAdded()), new LinkedHashMap<>(shaft.sacksRemoved()),
				GemYield.of(shaft, patterns));
	}

	// Glacite Powder from corpses (the loot blocks)
	public long corpsePowder() {
		return loots.stream().mapToLong(CorpseLoot::powder).sum();
	}
}
