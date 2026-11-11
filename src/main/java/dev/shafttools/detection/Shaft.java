package dev.shafttools.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.shafttools.detection.ShaftEvent.CampfirePlaced;
import dev.shafttools.detection.ShaftEvent.CaveIn;
import dev.shafttools.detection.ShaftEvent.CorpseLooted;
import dev.shafttools.detection.ShaftEvent.ExitBonus;
import dev.shafttools.detection.ShaftEvent.ExtraCorpses;
import dev.shafttools.detection.ShaftEvent.FrozeToDeath;
import dev.shafttools.detection.ShaftEvent.MayhemBuff;
import dev.shafttools.detection.ShaftEvent.Pristine;
import dev.shafttools.detection.ShaftEvent.SacksChanged;
import dev.shafttools.detection.ShaftEvent.ScrapFound;
import dev.shafttools.detection.ShaftEvent.Warping;

/**
 * What happened during one visit to a shaft. Times are in the caller's clock (milliseconds).
 * Only {@link ShaftTracker} changes it; once {@link #exitReason()} is set it is final.
 */
public final class Shaft {
	public enum ExitReason {
		/** Left through the exit with a "BYE!" bonus. */
		BYE,
		WARP,
		DEATH,
		/** The world changed without a known leaving message. */
		UNKNOWN
	}

	private final long enteredAt;
	private long leftAt = -1;
	private ExitReason exitReason;
	boolean confirmed;
	ShaftId id;
	private ExitReason pendingExit;
	private long exitPowder;
	Integer cold;
	List<Corpse> corpses = List.of();
	private int extraCorpses;
	private final List<CorpseLoot> loots = new ArrayList<>();
	private final List<String> mayhemBuffs = new ArrayList<>();
	private final List<Long> campfiresPlaced = new ArrayList<>();
	private long caveInAt = -1;
	private int scrapFound;
	private int pristineProcs;
	private final Map<String, Long> pristineItems = new LinkedHashMap<>();
	private final Map<String, Long> sacksAdded = new LinkedHashMap<>();
	private final Map<String, Long> sacksRemoved = new LinkedHashMap<>();
	private long lastSacksAt = -1;

	Shaft(long enteredAt) {
		this.enteredAt = enteredAt;
	}

	void apply(ShaftEvent event, long now) {
		switch (event) {
			case ExitBonus bye -> {
				pendingExit = ExitReason.BYE;
				exitPowder = bye.powder();
			}
			case Warping warping -> pendingExit = ExitReason.WARP;
			case FrozeToDeath death -> pendingExit = ExitReason.DEATH;
			case MayhemBuff mayhem -> mayhemBuffs.add(mayhem.buff());
			case ExtraCorpses extra -> extraCorpses += extra.count();
			case CaveIn caveIn -> caveInAt = now;
			case CorpseLooted looted -> loots.add(looted.loot());
			case CampfirePlaced placed -> campfiresPlaced.add(now);
			case ScrapFound scrap -> scrapFound++;
			case Pristine pristine -> {
				pristineProcs++;
				add(pristineItems, pristine.gems());
			}
			case SacksChanged sacks -> {
				sacks.added().forEach(item -> add(sacksAdded, item));
				sacks.removed().forEach(item -> add(sacksRemoved, item));
				lastSacksAt = now;
			}
			default -> {
			}
		}
	}

	void leave(long now) {
		leftAt = now;
		exitReason = pendingExit == null ? ExitReason.UNKNOWN : pendingExit;
	}

	private static void add(Map<String, Long> totals, ItemCount item) {
		totals.merge(item.item(), item.count(), Long::sum);
	}

	/** The world change into the shaft; the cold timer starts here. */
	public long enteredAt() {
		return enteredAt;
	}

	/** The world change out of the shaft, or -1 while still inside. */
	public long leftAt() {
		return leftAt;
	}

	/** Time spent inside, or -1 while still inside. */
	public long durationMillis() {
		return leftAt < 0 ? -1 : leftAt - enteredAt;
	}

	/** Null while still inside. */
	public ExitReason exitReason() {
		return exitReason;
	}

	/** Null until the scoreboard shows it. */
	public ShaftId id() {
		return id;
	}

	/** Glacite Powder from the "BYE!" bonus; 0 for any other exit. */
	public long exitPowder() {
		return pendingExit == ExitReason.BYE ? exitPowder : 0;
	}

	/** The last Cold value seen on the scoreboard, or null. */
	public Integer cold() {
		return cold;
	}

	/** The last non-empty state of the tab list corpse widget. */
	public List<Corpse> corpses() {
		return corpses;
	}

	/** Corpses added by "MORE!" messages; they also appear in {@link #corpses()}. */
	public int extraCorpses() {
		return extraCorpses;
	}

	public List<CorpseLoot> loots() {
		return Collections.unmodifiableList(loots);
	}

	public List<String> mayhemBuffs() {
		return Collections.unmodifiableList(mayhemBuffs);
	}

	/** Times a Portable Campfire was placed. */
	public List<Long> campfiresPlaced() {
		return Collections.unmodifiableList(campfiresPlaced);
	}

	/** Time of the cave-in message, or -1. */
	public long caveInAt() {
		return caveInAt;
	}

	public int scrapFound() {
		return scrapFound;
	}

	public int pristineProcs() {
		return pristineProcs;
	}

	/** Items from "PRISTINE!" messages. Silent Gemstone Spread procs are missing; use {@link #sacksAdded()}. */
	public Map<String, Long> pristineItems() {
		return Collections.unmodifiableMap(pristineItems);
	}

	/** Items from "[Sacks]" hover lines while inside. The last few seconds before leaving are never reported. */
	public Map<String, Long> sacksAdded() {
		return Collections.unmodifiableMap(sacksAdded);
	}

	public Map<String, Long> sacksRemoved() {
		return Collections.unmodifiableMap(sacksRemoved);
	}

	/** Time of the last "[Sacks]" message inside, or -1. */
	public long lastSacksAt() {
		return lastSacksAt;
	}
}
