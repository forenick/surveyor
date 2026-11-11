package dev.shafttools.detection;

import java.util.List;

/**
 * One "CORPSE LOOT!" chat block. The type is capitalised like the tab list ("Lapis"), and the
 * Glacite Powder line is split out of the items into {@code powder}.
 */
public record CorpseLoot(String type, boolean bonusDrop, List<ItemCount> items, long powder) {
}
