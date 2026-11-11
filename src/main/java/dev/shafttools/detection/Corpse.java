package dev.shafttools.detection;

/** One line of the tab list's "Frozen Corpses:" widget. The type is as shown there, e.g. "Lapis". */
public record Corpse(String type, boolean looted) {
}
