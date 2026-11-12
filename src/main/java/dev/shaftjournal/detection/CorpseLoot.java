package dev.shaftjournal.detection;

import java.util.List;

// one "CORPSE LOOT!" chat block. type is capitalised like in the tab list ("Lapis"), and the
// Glacite Powder line gets pulled out of items into powder
public record CorpseLoot(String type, boolean bonusDrop, List<ItemCount> items, long powder) {
}
