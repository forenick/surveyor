package dev.shaftjournal.hud;

import dev.shaftjournal.hud.HudLayout.ColorRule;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.hud.HudValue.Kind;
import dev.shaftjournal.hud.HudValue.Range;

public final class HudColors {
	// nicks pick (2026-11-12)
	static final int COLD_YELLOW = -50;
	// nicks pick (2026-11-12)
	static final int COLD_RED = -80;

	private HudColors() {
	}

	public static String readyRule(HudValue value) {
		return switch (value) {
			case SPAWN_TIMER -> "Red above the average spawn";
			case SHAFT_COLD -> "Yellow at " + COLD_YELLOW + "❄, red at " + COLD_RED + "❄";
			default -> null;
		};
	}

	public static String color(Line line, HudData data, boolean example) {
		HudValue value = line.hudValue();
		if (value == null || !value.hasNumber()) return null;
		Double number = data.number(value, HudBoxText.ruleRange(line), example);
		if (number == null) return null;
		double shown = shown(value.kind(), number);
		if (line.rules != null) {
			for (ColorRule rule : line.rules) {
				Double limit = limit(rule, data, example);
				if (limit != null && rule.matches(shown, limit)) return rule.color;
			}
		}
		return line.readyRule ? ready(value, shown, data, example) : null;
	}

	// tag not ready yet = rule skipped
	private static Double limit(ColorRule rule, HudData data, boolean example) {
		if (rule.than == null) return rule.value;
		HudValue value = HudBoxText.numberTagValue(rule.than);
		Double number = value == null ? null : data.number(value, HudBoxText.numberTagRange(rule.than), example);
		return number == null ? null : shown(value.kind(), number);
	}

	// whole secs, so "over 1:30" skips a shown 1:30
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
