package dev.shaftjournal.detection;

import java.util.List;

// powder line pulled out of items
public record CorpseLoot(String type, boolean bonusDrop, List<ItemCount> items, long powder) {
}
