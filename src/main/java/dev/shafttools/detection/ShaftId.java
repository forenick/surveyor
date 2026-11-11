package dev.shafttools.detection;

/** A shaft ID from the scoreboard, e.g. {@code JADE_1}: a 4-letter type and a variant (1, 2, C or L). */
public record ShaftId(String id, String type, String variant) {
}
