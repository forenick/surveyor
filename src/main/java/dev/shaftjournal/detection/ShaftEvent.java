package dev.shaftjournal.detection;

import java.util.List;

public sealed interface ShaftEvent {
	record PortalSpawned(boolean foundByYou) implements ShaftEvent {
	}

	record ShaftEntered(Shaft shaft) implements ShaftEvent {
	}

	record ShaftLeft(Shaft shaft) implements ShaftEvent {
	}

	record ExitBonus(long powder) implements ShaftEvent {
	}

	record Warping() implements ShaftEvent {
	}

	record FrozeToDeath() implements ShaftEvent {
	}

	record MayhemBuff(String buff) implements ShaftEvent {
	}

	record ExtraCorpses(int count) implements ShaftEvent {
	}

	record CaveIn() implements ShaftEvent {
	}

	record CorpsesChanged(List<Corpse> corpses) implements ShaftEvent {
	}

	record CorpseLooted(CorpseLoot loot) implements ShaftEvent {
	}

	// 0 or negative
	record ColdChanged(int cold) implements ShaftEvent {
	}

	record CampfirePlaced() implements ShaftEvent {
	}

	record CampfireUsed(int amount) implements ShaftEvent {
	}

	// gemstone spread procs w/o a msg, undercounts
	record Pristine(ItemCount gems) implements ShaftEvent {
	}

	// hypixel lumps some items into "other items"
	record SacksChanged(int windowSeconds, List<ItemCount> added, List<ItemCount> removed,
			long otherAdded, long otherRemoved) implements ShaftEvent {
	}

	record PickaxeAbility(String ability) implements ShaftEvent {
	}

	record ScrapFound() implements ShaftEvent {
	}

	// f.e. "Aquamarine Crystal"
	record CrystalFound(String item) implements ShaftEvent {
	}

	record Supercraft(ItemCount result) implements ShaftEvent {
	}
}
