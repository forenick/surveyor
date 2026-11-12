package dev.shaftjournal.hud;

import dev.shaftjournal.hud.HudLayout.ColorRule;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.hud.HudValue.Kind;
import dev.shaftjournal.hud.HudValue.Range;

// colours based on the value. the line's own thresholds first, then the value's ready-made rule. plain java
public final class HudColors {
	// cold goes yellow from here, a campfire's 50 is used up by then. Nick's pick (2026-11-12)
	static final int COLD_YELLOW = -50;
	// cold goes red from here, u die at -100. Nick's pick (2026-11-12)
	static final int COLD_RED = -80;

	private HudColors() {
	}

	// what the ready-made rule does, shown in the editor. null if the value has none
	public static String readyRule(HudValue value) {
		return switch (value) {
			case SPAWN_TIMER -> "Red above the average spawn";
			case SHAFT_COLD -> "Yellow at " + COLD_YELLOW + "❄, red at " + COLD_RED + "❄";
			default -> null;
		};
	}

	// colour code for the line's value right now, or null for the box's value colour
	// example = in the editor, so missing values use their example
	public static String color(Line line, HudData data, boolean example) {
		HudValue value = line.hudValue();
		if (value == null || !value.hasNumber()) return null;
		Double number = data.number(value, HudBoxText.ruleRange(line), example);
		if (number == null) return null;
		double shown = shown(value.kind(), number);
		if (line.rules != null) {
			for (ColorRule rule : line.rules) {
				if (rule.matches(shown)) return rule.color;
			}
		}
		return line.readyRule ? ready(value, shown, data, example) : null;
	}

	// the number like the line shows it, in the unit thresholds are typed in. whole seconds for times
	// (1:30 shows until 1:31) so "over 1:30" doesnt colour a "1:30"
	private static double shown(Kind kind, double number) {
		return kind == Kind.TIME ? Math.floor(number / 1000) : number;
	}

	private static String ready(HudValue value, double shown, HudData data, boolean example) {
		return switch (value) {
			case SPAWN_TIMER -> {
				Double average = data.number(HudValue.AVERAGE_SPAWN, Range.SESSION, example);
				yield average != null && shown > shown(Kind.TIME, average) ? "c" : null;
			}
			case SHAFT_COLD -> shown <= COLD_RED ? "c" : shown <= COLD_YELLOW ? "e" : null;
			default -> null;
		};
	}
}
