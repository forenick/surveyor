package dev.shafttools.journal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.shafttools.detection.Corpse;
import dev.shafttools.detection.CorpseLoot;
import dev.shafttools.detection.Shaft;
import dev.shafttools.detection.TextPatterns;

/**
 * One finished shaft visit as stored in the journal file. Times are epoch milliseconds, durations
 * milliseconds. Add fields at the end and bump {@link #VERSION} when the meaning of one changes.
 *
 * @param sessionId     start time of the session this shaft belongs to
 * @param shaftId       e.g. "JADE_1"; null if the scoreboard never showed it
 * @param exit          a {@link Shaft.ExitReason} name
 * @param searchMillis  from the first block mined to "WOW! You found a Glacite Mineshaft portal!";
 *                      null when the spawn wasn't timed (someone else's portal, or no block was mined)
 * @param campfiresAt   when each campfire was placed, relative to entering
 * @param gems          null when no gems of the shaft's type were found
 */
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

	/** Glacite Powder from corpses (the loot blocks). */
	public long corpsePowder() {
		return loots.stream().mapToLong(CorpseLoot::powder).sum();
	}
}
