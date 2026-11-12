package dev.shaftjournal.detection;

// item name w/o its icon (f.e. "Rough Jade Gemstone") plus an amount
public record ItemCount(String item, long count) {
}
