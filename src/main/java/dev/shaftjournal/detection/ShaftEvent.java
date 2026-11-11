package dev.shaftjournal.detection;

import java.util.List;

/** Something that happened, as recognised from game text. */
public sealed interface ShaftEvent {
	/** "WOW!" (you found it) or "MINESHAFT!" (spawned nearby). The finder gets both. */
	record PortalSpawned(boolean foundByYou) implements ShaftEvent {
	}

	/** The scoreboard showed the shaft ID for the first time in this shaft. */
	record ShaftEntered(Shaft shaft) implements ShaftEvent {
	}

	/** The world changed while in a shaft. The shaft is finished and won't change again. */
	record ShaftLeft(Shaft shaft) implements ShaftEvent {
	}

	/** "BYE!": left the shaft alive, with the exit powder bonus. */
	record ExitBonus(long powder) implements ShaftEvent {
	}

	/** "Warping...": left without an exit bonus. */
	record Warping() implements ShaftEvent {
	}

	record FrozeToDeath() implements ShaftEvent {
	}

	/** E.g. "Mining Fortune", "Suspicious Scrap chance" or "Pickaxe Ability cooldown". */
	record MayhemBuff(String buff) implements ShaftEvent {
	}

	/** "MORE!": Dead Man's Chest spawned extra corpses. */
	record ExtraCorpses(int count) implements ShaftEvent {
	}

	record CaveIn() implements ShaftEvent {
	}

	/** The tab list corpse widget changed. */
	record CorpsesChanged(List<Corpse> corpses) implements ShaftEvent {
	}

	record CorpseLooted(CorpseLoot loot) implements ShaftEvent {
	}

	/** The scoreboard Cold value changed. Cold is zero or negative. */
	record ColdChanged(int cold) implements ShaftEvent {
	}

	record CampfirePlaced() implements ShaftEvent {
	}

	/** Warmed up at a campfire; {@code amount} is how much Cold it removed. */
	record CampfireUsed(int amount) implements ShaftEvent {
	}

	/** A "PRISTINE!" message. Gemstone Spread blocks proc silently, so these undercount gems. */
	record Pristine(ItemCount gems) implements ShaftEvent {
	}

	/**
	 * A "[Sacks]" message with its hover breakdown. Hypixel lists only some items and sums the
	 * rest as "other items", which land in {@code otherAdded} and {@code otherRemoved}.
	 */
	record SacksChanged(int windowSeconds, List<ItemCount> added, List<ItemCount> removed,
			long otherAdded, long otherRemoved) implements ShaftEvent {
	}

	/** "You used your Pickobulus Pickaxe Ability!": mining, even without a block hit. */
	record PickaxeAbility(String ability) implements ShaftEvent {
	}

	record ScrapFound() implements ShaftEvent {
	}

	record Supercraft(ItemCount result) implements ShaftEvent {
	}
}
