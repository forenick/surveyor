package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.List;

import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;

/** Turns a box and the current values into §-coded text lines, title first. Plain Java. */
public final class HudBoxText {
	private static final String TITLE_STYLE = "§e§l";

	private HudBoxText() {
	}

	/**
	 * @param example true in the editor: missing values show their example instead of "-"
	 * @return the lines to draw, empty when the box has nothing to show
	 */
	public static List<String> lines(Box box, HudData data, boolean example) {
		List<String> lines = new ArrayList<>();
		if (box.showTitle) {
			String title = tags(box.title, data, example).strip();
			if (!title.isEmpty()) lines.add(TITLE_STYLE + title);
		}
		String label = "§" + box.labelColor + (box.labelBold ? "§l" : "");
		String value = "§" + box.valueColor + (box.valueBold ? "§l" : "");
		for (Line line : box.lines) {
			HudValue hudValue = line.hudValue();
			if (!line.shown || hudValue == null) continue;
			lines.add(label + line.prefix + value + text(hudValue, line.format, data, example));
		}
		return lines;
	}

	/** Replaces every {value_id} with that value in its first format. */
	static String tags(String text, HudData data, boolean example) {
		if (text.indexOf('{') < 0) return text;
		for (HudValue value : HudValue.values()) {
			String tag = "{" + value.id() + "}";
			if (text.contains(tag)) text = text.replace(tag, text(value, 0, data, example));
		}
		return text;
	}

	private static String text(HudValue value, int format, HudData data, boolean example) {
		return HudFormat.format(value.kind(), format, example ? data.getOrExample(value) : data.get(value));
	}
}
