package dev.shaftjournal.detection;

import java.util.List;

// something that happened, picked up from game text
public sealed interface ShaftEvent {
	// "WOW!" (u found it) or "MINESHAFT!" (spawned nearby). the finder gets both
	record PortalSpawned(boolean foundByYou) implements ShaftEvent {
	}

	// first time the scoreboard shows the shaft id in this shaft
	record ShaftEntered(Shaft shaft) implements ShaftEvent {
	}

	// world changed while in a shaft. shaft is done and wont change anymore
	record ShaftLeft(Shaft shaft) implements ShaftEvent {
	}

	// "BYE!" = left alive, w/ the exit powder bonus
	record ExitBonus(long powder) implements ShaftEvent {
	}

	// "Warping..." = left w/o an exit bonus
	record Warping() implements ShaftEvent {
	}

	record FrozeToDeath() implements ShaftEvent {
	}

	// f.e. "Mining Fortune", "Suspicious Scrap chance" or "Pickaxe Ability cooldown"
	record MayhemBuff(String buff) implements ShaftEvent {
	}

	// "MORE!" = Dead Man's Chest spawned extra corpses
	record ExtraCorpses(int count) implements ShaftEvent {
	}

	record CaveIn() implements ShaftEvent {
	}

	// tab list corpse widget changed
	record CorpsesChanged(List<Corpse> corpses) implements ShaftEvent {
	}

	record CorpseLooted(CorpseLoot loot) implements ShaftEvent {
	}

	// cold is 0 or negative
	record ColdChanged(int cold) implements ShaftEvent {
	}

	record CampfirePlaced() implements ShaftEvent {
	}

	// warmed up at a campfire, amount is how much Cold it took off
	record CampfireUsed(int amount) implements ShaftEvent {
	}

	// "PRISTINE!" msg. gemstone spread blocks proc without a msg so this undercounts gems
	record Pristine(ItemCount gems) implements ShaftEvent {
	}

	// "[Sacks]" msg w/ its hover breakdown. hypixel only lists some items and lumps the rest
	// into "other items", those go in otherAdded and otherRemoved
	record SacksChanged(int windowSeconds, List<ItemCount> added, List<ItemCount> removed,
			long otherAdded, long otherRemoved) implements ShaftEvent {
	}

	// "You used your Pickobulus Pickaxe Ability!" counts as mining, even w/o a block hit
	record PickaxeAbility(String ability) implements ShaftEvent {
	}

	record ScrapFound() implements ShaftEvent {
	}

	record Supercraft(ItemCount result) implements ShaftEvent {
	}
}
