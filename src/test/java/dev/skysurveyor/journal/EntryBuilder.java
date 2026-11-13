package dev.skysurveyor.journal;

import java.util.List;
import java.util.Map;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.money.ItemPrice;

// tests only. defaults = a plain 1 min jade shaft, nothing found
public final class EntryBuilder {
	private int version = JournalEntry.VERSION;
	private long sessionId;
	private String shaftId = "JADE_1";
	private long enteredAt;
	private long durationMillis = 60_000;
	private String exit = "BYE";
	private long exitPowder;
	private Long searchMillis;
	private List<Corpse> corpses = List.of();
	private int extraCorpses;
	private List<CorpseLoot> loots = List.of();
	private List<String> mayhemBuffs = List.of();
	private List<Long> campfiresAt = List.of();
	private Integer coldAtExit;
	private int scrapFound;
	private int pristineProcs;
	private Map<String, Long> pristineItems = Map.of();
	private Map<String, Long> sacksAdded = Map.of();
	private Map<String, Long> sacksRemoved = Map.of();
	private GemYield gems;
	private List<String> crystals = List.of();
	private Map<String, Long> otherDrops = Map.of();
	private Map<String, ItemPrice> prices;
	private List<Integer> campfireColds;

	public static EntryBuilder entry() {
		return new EntryBuilder();
	}

	public static EntryBuilder from(JournalEntry e) {
		return entry().version(e.version()).sessionId(e.sessionId()).shaftId(e.shaftId()).enteredAt(e.enteredAt())
				.durationMillis(e.durationMillis()).exit(e.exit()).exitPowder(e.exitPowder()).searchMillis(e.searchMillis())
				.corpses(e.corpses()).extraCorpses(e.extraCorpses()).loots(e.loots()).mayhemBuffs(e.mayhemBuffs())
				.campfiresAt(e.campfiresAt()).coldAtExit(e.coldAtExit()).scrapFound(e.scrapFound())
				.pristineProcs(e.pristineProcs()).pristineItems(e.pristineItems()).sacksAdded(e.sacksAdded())
				.sacksRemoved(e.sacksRemoved()).gems(e.gems()).crystals(e.crystals()).otherDrops(e.otherDrops())
				.prices(e.prices()).campfireColds(e.campfireColds());
	}

	public JournalEntry build() {
		return new JournalEntry(version, sessionId, shaftId, enteredAt, durationMillis, exit, exitPowder, searchMillis, corpses,
				extraCorpses, loots, mayhemBuffs, campfiresAt, coldAtExit, scrapFound, pristineProcs, pristineItems, sacksAdded,
				sacksRemoved, gems, crystals, otherDrops, prices, campfireColds);
	}

	public EntryBuilder version(int value) {
		version = value;
		return this;
	}

	public EntryBuilder sessionId(long value) {
		sessionId = value;
		return this;
	}

	public EntryBuilder shaftId(String value) {
		shaftId = value;
		return this;
	}

	public EntryBuilder enteredAt(long value) {
		enteredAt = value;
		return this;
	}

	public EntryBuilder durationMillis(long value) {
		durationMillis = value;
		return this;
	}

	public EntryBuilder exit(String value) {
		exit = value;
		return this;
	}

	public EntryBuilder exitPowder(long value) {
		exitPowder = value;
		return this;
	}

	public EntryBuilder searchMillis(Long value) {
		searchMillis = value;
		return this;
	}

	public EntryBuilder corpses(List<Corpse> value) {
		corpses = value;
		return this;
	}

	public EntryBuilder extraCorpses(int value) {
		extraCorpses = value;
		return this;
	}

	public EntryBuilder loots(List<CorpseLoot> value) {
		loots = value;
		return this;
	}

	public EntryBuilder mayhemBuffs(List<String> value) {
		mayhemBuffs = value;
		return this;
	}

	public EntryBuilder campfiresAt(List<Long> value) {
		campfiresAt = value;
		return this;
	}

	public EntryBuilder coldAtExit(Integer value) {
		coldAtExit = value;
		return this;
	}

	public EntryBuilder scrapFound(int value) {
		scrapFound = value;
		return this;
	}

	public EntryBuilder pristineProcs(int value) {
		pristineProcs = value;
		return this;
	}

	public EntryBuilder pristineItems(Map<String, Long> value) {
		pristineItems = value;
		return this;
	}

	public EntryBuilder sacksAdded(Map<String, Long> value) {
		sacksAdded = value;
		return this;
	}

	public EntryBuilder sacksRemoved(Map<String, Long> value) {
		sacksRemoved = value;
		return this;
	}

	public EntryBuilder gems(GemYield value) {
		gems = value;
		return this;
	}

	public EntryBuilder crystals(List<String> value) {
		crystals = value;
		return this;
	}

	public EntryBuilder otherDrops(Map<String, Long> value) {
		otherDrops = value;
		return this;
	}

	public EntryBuilder prices(Map<String, ItemPrice> value) {
		prices = value;
		return this;
	}

	public EntryBuilder campfireColds(List<Integer> value) {
		campfireColds = value;
		return this;
	}
}
