package dev.shaftjournal.detection;

// shaft id from the scoreboard, f.e. JADE_1. 4 letter type + a variant (1, 2, C or L)
public record ShaftId(String id, String type, String variant) {
}
