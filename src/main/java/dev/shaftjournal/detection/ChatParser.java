package dev.shaftjournal.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

import dev.shaftjournal.detection.ShaftEvent.CampfirePlaced;
import dev.shaftjournal.detection.ShaftEvent.CampfireUsed;
import dev.shaftjournal.detection.ShaftEvent.CaveIn;
import dev.shaftjournal.detection.ShaftEvent.CorpseLooted;
import dev.shaftjournal.detection.ShaftEvent.ExitBonus;
import dev.shaftjournal.detection.ShaftEvent.ExtraCorpses;
import dev.shaftjournal.detection.ShaftEvent.FrozeToDeath;
import dev.shaftjournal.detection.ShaftEvent.MayhemBuff;
import dev.shaftjournal.detection.ShaftEvent.PickaxeAbility;
import dev.shaftjournal.detection.ShaftEvent.PortalSpawned;
import dev.shaftjournal.detection.ShaftEvent.Pristine;
import dev.shaftjournal.detection.ShaftEvent.SacksChanged;
import dev.shaftjournal.detection.ShaftEvent.ScrapFound;
import dev.shaftjournal.detection.ShaftEvent.Supercraft;
import dev.shaftjournal.detection.ShaftEvent.Warping;

// hypixel sends corpse loot as 1 msg per line, hence the state
public final class ChatParser {
	private final TextPatterns patterns;
	private LootBlock loot;

	public ChatParser(TextPatterns patterns) {
		this.patterns = patterns;
	}

	// chat only, not action bar. returns the event this msg finishes
	public ShaftEvent parse(String legacy, List<String> hovers) {
		String text = TextPatterns.clean(legacy);
		Matcher m;

		if ((m = patterns.match("loot.start", text)) != null) {
			ShaftEvent unfinished = finishLoot();
			loot = new LootBlock(capitalize(m.group("type")));
			return unfinished;
		}
		if (loot != null) {
			if (patterns.matches("loot.end", text)) return finishLoot();
			if (patterns.matches("loot.bonus", text)) {
				loot.bonusDrop = true;
				return null;
			}
			if (patterns.matches("loot.rewards", text)) {
				loot.rewards = true;
				return null;
			}
			if (loot.rewards && (m = patterns.match("loot.item", text)) != null) {
				String item = m.group("item");
				long count = m.group("count") == null ? 1 : TextPatterns.number(m.group("count"));
				if (patterns.matches("loot.powder", item)) {
					loot.powder += count;
				} else {
					loot.items.add(new ItemCount(item, count));
				}
				return null;
			}
			// other msgs can land in between, keep the block open
		}

		if ((m = patterns.match("pristine", text)) != null) {
			return new Pristine(new ItemCount(m.group("item"), TextPatterns.number(m.group("count"))));
		}
		if ((m = patterns.match("sacks", text)) != null) {
			return parseSacks(Integer.parseInt(m.group("seconds")), hovers);
		}
		if (patterns.matches("spawn.found", text)) return new PortalSpawned(true);
		if (patterns.matches("spawn.nearby", text)) return new PortalSpawned(false);
		if ((m = patterns.match("exit.bye", text)) != null) return new ExitBonus(TextPatterns.number(m.group("powder")));
		if (patterns.matches("exit.warp", text)) return new Warping();
		if (patterns.matches("death.cold", text)) return new FrozeToDeath();
		if ((m = patterns.match("mayhem", text)) != null) return new MayhemBuff(m.group("buff"));
		if ((m = patterns.match("corpse.extra", text)) != null) return new ExtraCorpses(Integer.parseInt(m.group("count")));
		if (patterns.matches("cave_in", text)) return new CaveIn();
		if (patterns.matches("campfire.placed", text)) return new CampfirePlaced();
		if ((m = patterns.match("campfire.used", text)) != null) return new CampfireUsed(Integer.parseInt(m.group("amount")));
		if (patterns.matches("scrap", text)) return new ScrapFound();
		if ((m = patterns.match("ability.used", text)) != null) return new PickaxeAbility(m.group("ability"));
		if ((m = patterns.match("supercraft", text)) != null) {
			return new Supercraft(new ItemCount(m.group("item"), TextPatterns.number(m.group("count"))));
		}
		return null;
	}

	private SacksChanged parseSacks(int windowSeconds, List<String> hovers) {
		List<ItemCount> added = new ArrayList<>();
		List<ItemCount> removed = new ArrayList<>();
		long otherAdded = 0;
		long otherRemoved = 0;
		for (String hover : hovers) {
			for (String line : hover.split("\n")) {
				String text = TextPatterns.clean(line);
				Matcher m;
				if ((m = patterns.match("sacks.item", text)) != null) {
					ItemCount item = new ItemCount(m.group("item"), TextPatterns.number(m.group("count")));
					(m.group("sign").equals("+") ? added : removed).add(item);
				} else if ((m = patterns.match("sacks.other", text)) != null) {
					long count = TextPatterns.number(m.group("count"));
					if (m.group("sign").equals("+")) {
						otherAdded += count;
					} else {
						otherRemoved += count;
					}
				}
			}
		}
		return new SacksChanged(windowSeconds, List.copyOf(added), List.copyOf(removed), otherAdded, otherRemoved);
	}

	private CorpseLooted finishLoot() {
		if (loot == null) return null;
		CorpseLooted event = new CorpseLooted(new CorpseLoot(loot.type, loot.bonusDrop, List.copyOf(loot.items), loot.powder));
		loot = null;
		return event;
	}

	private static String capitalize(String upper) {
		return upper.charAt(0) + upper.substring(1).toLowerCase(Locale.ROOT);
	}

	private static final class LootBlock {
		final String type;
		final List<ItemCount> items = new ArrayList<>();
		boolean bonusDrop;
		boolean rewards;
		long powder;

		LootBlock(String type) {
			this.type = type;
		}
	}
}
