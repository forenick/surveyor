package dev.skysurveyor.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.detection.ShaftEvent.CampfirePlaced;
import dev.skysurveyor.detection.ShaftEvent.CampfireUsed;
import dev.skysurveyor.detection.ShaftEvent.CaveIn;
import dev.skysurveyor.detection.ShaftEvent.CorpseLooted;
import dev.skysurveyor.detection.ShaftEvent.CrystalFound;
import dev.skysurveyor.detection.ShaftEvent.CrystalOwned;
import dev.skysurveyor.detection.ShaftEvent.ExitBonus;
import dev.skysurveyor.detection.ShaftEvent.ExtraCorpses;
import dev.skysurveyor.detection.ShaftEvent.FrozeToDeath;
import dev.skysurveyor.detection.ShaftEvent.MayhemBuff;
import dev.skysurveyor.detection.ShaftEvent.Pristine;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ScrapFound;
import dev.skysurveyor.detection.ShaftEvent.Supercraft;
import dev.skysurveyor.detection.ShaftEvent.Warping;

// only ShaftTracker changes it, final once exitReason is set
public final class Shaft {
	public enum ExitReason {
		BYE,
		WARP,
		DEATH,
		UNKNOWN
	}

	private final long enteredAt;
	private long leftAt = -1;
	private ExitReason exitReason;
	boolean confirmed;
	private ShaftId id;
	private ExitReason pendingExit;
	private long exitPowder;
	private Integer cold;
	private List<Corpse> corpses = List.of();
	private int extraCorpses;
	private final List<CorpseLoot> loots = new ArrayList<>();
	private final List<String> mayhemBuffs = new ArrayList<>();
	private final List<Long> campfiresPlaced = new ArrayList<>();
	// scoreboard still shows the cold from before the -50 (recording 1)
	private final List<Integer> campfireColds = new ArrayList<>();
	private long caveInAt = -1;
	private int scrapFound;
	private final List<String> crystals = new ArrayList<>();
	private int pristineProcs;
	private final Map<String, Long> pristineItems = new LinkedHashMap<>();
	private final Map<String, Long> sacksAdded = new LinkedHashMap<>();
	private final Map<String, Long> sacksRemoved = new LinkedHashMap<>();
	private long lastSacksAt = -1;
	private final Map<String, Long> pristineSinceSacks = new LinkedHashMap<>();
	// corpse loot + supercrafts wait here until a sacks msg reports them
	private final Map<String, Long> notMinedPending = new LinkedHashMap<>();
	private final Map<String, Long> notMinedInSacks = new LinkedHashMap<>();
	// goes up on every change but cold, so callers can cache
	private int version;

	Shaft(long enteredAt) {
		this.enteredAt = enteredAt;
	}

	void apply(ShaftEvent event, long now) {
		version++;
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
			case CorpseLooted looted -> {
				loots.add(looted.loot());
				looted.loot().items().forEach(item -> add(notMinedPending, item));
			}
			case Supercraft craft -> add(notMinedPending, craft.result());
			case CampfirePlaced placed -> campfiresPlaced.add(now);
			case CampfireUsed used -> campfireColds.add(cold);
			case ScrapFound scrap -> scrapFound++;
			case CrystalFound crystal -> crystals.add(crystal.item());
			case CrystalOwned owned -> {
				if (!crystals.isEmpty()) crystals.removeLast();
			}
			case Pristine pristine -> {
				pristineProcs++;
				add(pristineItems, pristine.gems());
				add(pristineSinceSacks, pristine.gems());
			}
			case SacksChanged sacks -> {
				sacks.added().forEach(item -> add(sacksAdded, item));
				sacks.removed().forEach(item -> add(sacksRemoved, item));
				lastSacksAt = now;
				pristineSinceSacks.clear();
				notMinedPending.forEach((item, count) -> notMinedInSacks.merge(item, count, Long::sum));
				notMinedPending.clear();
			}
			default -> {
			}
		}
	}

	void setId(ShaftId id) {
		version++;
		this.id = id;
	}

	// no version bump, cold changes every few secs and never changes coins
	void setCold(Integer cold) {
		this.cold = cold;
	}

	void setCorpses(List<Corpse> corpses) {
		version++;
		this.corpses = corpses;
	}

	void leave(long now) {
		version++;
		leftAt = now;
		exitReason = pendingExit == null ? ExitReason.UNKNOWN : pendingExit;
	}

	private static void add(Map<String, Long> totals, ItemCount item) {
		totals.merge(item.item(), item.count(), Long::sum);
	}

	public int version() {
		return version;
	}

	public long enteredAt() {
		return enteredAt;
	}

	// -1 = still inside
	public long leftAt() {
		return leftAt;
	}

	public long durationMillis() {
		return leftAt < 0 ? -1 : leftAt - enteredAt;
	}

	public ExitReason exitReason() {
		return exitReason;
	}

	public ShaftId id() {
		return id;
	}

	public long exitPowder() {
		return pendingExit == ExitReason.BYE ? exitPowder : 0;
	}

	public Integer cold() {
		return cold;
	}

	// last non empty widget state
	public List<Corpse> corpses() {
		return corpses;
	}

	// already in corpses() too
	public int extraCorpses() {
		return extraCorpses;
	}

	public List<CorpseLoot> loots() {
		return Collections.unmodifiableList(loots);
	}

	public List<String> mayhemBuffs() {
		return Collections.unmodifiableList(mayhemBuffs);
	}

	public List<Long> campfiresPlaced() {
		return Collections.unmodifiableList(campfiresPlaced);
	}

	// null = cold line wasnt there
	public List<Integer> campfireColds() {
		return Collections.unmodifiableList(campfireColds);
	}

	public long caveInAt() {
		return caveInAt;
	}

	public int scrapFound() {
		return scrapFound;
	}

	public List<String> crystals() {
		return Collections.unmodifiableList(crystals);
	}

	public int pristineProcs() {
		return pristineProcs;
	}

	// misses silent gemstone spread procs, sacksAdded() has real totals
	public Map<String, Long> pristineItems() {
		return Collections.unmodifiableMap(pristineItems);
	}

	// last few secs before leaving never get reported
	public Map<String, Long> sacksAdded() {
		return Collections.unmodifiableMap(sacksAdded);
	}

	public Map<String, Long> sacksRemoved() {
		return Collections.unmodifiableMap(sacksRemoved);
	}

	// start of the unreported tail, everything if no sacks msg yet
	public Map<String, Long> pristineSinceSacks() {
		return Collections.unmodifiableMap(pristineSinceSacks);
	}

	public Map<String, Long> notMinedInSacks() {
		return Collections.unmodifiableMap(notMinedInSacks);
	}

	public long lastSacksAt() {
		return lastSacksAt;
	}
}
