package dev.shaftjournal.hud;

import dev.shaftjournal.hud.HudLayout.ColorRule;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.hud.HudValue.Kind;
import dev.shaftjournal.hud.HudValue.Range;

/** Value-based colours: a line's own thresholds, then its value's ready-made rule. Plain Java. */
public final class HudColors {
	/** Cold turns yellow from here: a campfire's 50 is then fully used. Nick's choice (2026-11-12). */
	static final int COLD_YELLOW = -50;
	/** Cold turns red from here; death is at -100. Nick's choice (2026-11-12). */
	static final int COLD_RED = -80;

	private HudColors() {
	}

	/** What the value's ready-made rule does, for the editor; null when the value has none. */
	public static String readyRule(HudValue value) {
		return switch (value) {
			case SPAWN_TIMER -> "Red above the average spawn";
			case SHAFT_COLD -> "Yellow at " + COLD_YELLOW + "❄, red at " + COLD_RED + "❄";
			default -> null;
		};
	}

	/**
	 * The colour code the line's value takes now, or null for the box's value colour.
	 *
	 * @param example the editor: missing values use their examples
	 */
	public static String color(Line line, HudData data, boolean example) {
		HudValue value = line.hudValue();
		if (value == null || !value.hasNumber()) return null;
		Double number = data.number(value, line.range, example);
		if (number == null) return null;
		if (line.rules != null) {
			// Thresholds for times are typed in seconds; the value is in milliseconds.
			double compared = value.kind() == Kind.TIME ? number / 1000 : number;
			for (ColorRule rule : line.rules) {
				if (rule.matches(compared)) return rule.color;
			}
		}
		return line.readyRule ? ready(value, number, data, example) : null;
	}

	private static String ready(HudValue value, double number, HudData data, boolean example) {
		return switch (value) {
			case SPAWN_TIMER -> {
				Double average = data.number(HudValue.AVERAGE_SPAWN, Range.SESSION, example);
				yield average != null && number > average ? "c" : null;
			}
			case SHAFT_COLD -> number <= COLD_RED ? "c" : number <= COLD_YELLOW ? "e" : null;
			default -> null;
		};
	}
}
