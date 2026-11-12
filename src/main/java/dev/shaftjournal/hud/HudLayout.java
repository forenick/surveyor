package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.shaftjournal.hud.HudValue.Range;

// every HUD box and its settings, so whats in hud.json. mutable bc the editor changes it in place.
// only touched on the client thread, except for the toJson() copies. plain java
public final class HudLayout {
	// 3: where a box shows is 3 checkboxes (2 had one Visibility choice)
	// 2: positions are an anchor + an offset in GUI pixels (1 had fractions of the free space)
	public static final int VERSION = 3;
	// space between a box and the screen edge, also the gap between snapped boxes. Nick's pick (2026-11-11)
	public static final int MARGIN = 4;
	// smallest and biggest box size
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2;
	// the 16 Minecraft colour codes, in order
	public static final String COLOR_CODES = "0123456789abcdef";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// version 2's single choice of where a box shows. only read to convert old files
	enum Visibility {
		ALWAYS, MINES, IN_SHAFT, OUTSIDE_SHAFT
	}

	// where a box is placed from, per axis. the screen margin at the start (left/top), the middle of the
	// screen, or the margin at the end (right/bottom). that edge (or the middle) of the box stays put when
	// the window, GUI scale or the box's text changes, so boxes lined up on that edge stay lined up
	public enum Anchor {
		START, MIDDLE, END;

		// the anchor u get from snapping to this part of a box. 0 its start, 0.5 its middle, 1 its end
		public static Anchor ofEdge(double edge) {
			return edge == 0 ? START : edge == 1 ? END : MIDDLE;
		}

		// for a box placed freely, the third of the screen its middle is in
		public static Anchor forPosition(int start, int size, int screen) {
			double middle = start + size / 2.0;
			return middle < screen / 3.0 ? START : middle > screen * 2 / 3.0 ? END : MIDDLE;
		}

		// where a box w/ this offset starts (left or top), kept inside the margins
		public int start(int offset, int size, int screen) {
			int start = switch (this) {
				case START -> MARGIN + offset;
				case MIDDLE -> centred(size, screen) + offset;
				case END -> screen - MARGIN - size - offset;
			};
			return clamp(start, size, screen);
		}

		// the offset that puts the box's start at start. inwards from the margin, or right/down from the middle
		public int offset(int start, int size, int screen) {
			return switch (this) {
				case START -> start - MARGIN;
				case MIDDLE -> start - centred(size, screen);
				case END -> screen - MARGIN - size - start;
			};
		}

		// keeps a box's start inside the margins. a box too big for the screen just starts at the margin
		public static int clamp(int start, int size, int screen) {
			return Math.max(MARGIN, Math.min(start, screen - MARGIN - size));
		}

		private static int centred(int size, int screen) {
			return (int) Math.round((screen - size) / 2.0);
		}
	}

	public enum Background {
		NONE, DARK, FRAME
	}

	public enum Align {
		LEFT, CENTER, RIGHT
	}

	public static final class Line {
		public String value;
		// text before the value. the value's default prefix unless its changed in the file
		public String prefix;
		public boolean shown = true;
		// index into HudFormat.options
		public int format;
		// which shafts the value adds up, null for values without ranges
		public Range range;
		// free text w/ & codes and {value_id} tags instead of prefix + value. the text is kept when this
		// gets switched off, so switching back brings it back
		public boolean advanced;
		// the free text, null until the line got switched to it the first time
		public String text;
		// the value's ready-made colour rule is on (see HudColors.readyRule)
		public boolean readyRule;
		// own colour thresholds, checked in order before the ready-made rule. null if there are none
		public List<ColorRule> rules;

		public Line() {
		}

		// a new line like the editor adds it. shown, w/ the value's default prefix, format and range
		public Line(HudValue value) {
			this(value, null, true, 0);
		}

		Line(HudValue value, String prefix, boolean shown, int format) {
			this.value = value.id();
			this.prefix = prefix == null ? value.defaultPrefix() : prefix;
			this.shown = shown;
			this.format = format;
			this.range = value.defaultRange();
		}

		// null when the file has a value this version doesnt know
		public HudValue hudValue() {
			return HudValue.byId(value);
		}
	}

	// one colour threshold on a line's value, f.e. red over 3:00
	public static final class ColorRule {
		public enum When {
			OVER, UNDER
		}

		public When when = When.OVER;
		// in the value's unit, seconds for times
		public double value;
		// a Minecraft colour code, "0" to "f"
		public String color = "c";

		public boolean matches(double number) {
			return when == When.UNDER ? number < value : number > value;
		}
	}

	public static final class Box {
		public String id;
		// can have value tags like {shaft_type}
		public String title = "";
		public boolean showTitle;
		// where the box shows. in a shaft, in the Dwarven Mines outside a shaft, and anywhere else
		public boolean showInShaft = true;
		public boolean showInMines = true;
		public boolean showElsewhere;
		public Background background = Background.DARK;
		public float scale = 1;
		public Align align = Align.LEFT;
		// Minecraft colour codes, "0" to "f"
		public String labelColor = "7";
		public String valueColor = "f";
		public boolean labelBold;
		public boolean valueBold;
		public Anchor anchorX = Anchor.START;
		public Anchor anchorY = Anchor.START;
		// GUI pixels from the anchor. inwards from the margin for START and END, right/down of the middle for MIDDLE
		public int offsetX;
		public int offsetY;
		public List<Line> lines = new ArrayList<>();
		// version 1 position, a fraction of the free space. only read to convert old files, never written
		Float x;
		Float y;
		// version 2's visibility. only read to convert old files, never written
		Visibility visibility;

		// if the box shows here. a shaft counts as a shaft even tho its also in the mines
		public boolean visible(boolean inMines, boolean inShaft) {
			return inShaft ? showInShaft : inMines ? showInMines : showElsewhere;
		}

		// moves a line to a gap between lines. gap 0 is above the first line, gap lines.size() is
		// below the last. dropping a line right above or below itself does nothing
		public void moveLine(int from, int gap) {
			if (from < 0 || from >= lines.size() || gap < 0 || gap > lines.size()) return;
			Line line = lines.remove(from);
			lines.add(gap > from ? gap - 1 : gap, line);
		}
	}

	public int version = VERSION;
	public List<Box> boxes = new ArrayList<>();

	// the 3 default boxes from the approved mockup (Docs/mockups/hud-v1.html)
	public static HudLayout defaults() {
		HudLayout layout = new HudLayout();

		Box session = box("session", "Session", true, true, Background.DARK, Align.LEFT, Anchor.START, 0, Anchor.START, 0);
		session.lines.add(new Line(HudValue.SESSION_SHAFTS, null, true, 0));
		session.lines.add(new Line(HudValue.SHAFTS_PER_HOUR, null, true, 0));
		session.lines.add(new Line(HudValue.AVERAGE_SPAWN, null, false, 1));
		session.lines.add(new Line(HudValue.SESSION_POWDER, null, true, 0));
		session.lines.add(new Line(HudValue.SESSION_CORPSE_FINE, null, true, 0));
		layout.boxes.add(session);

		// just under the crosshair, same spot as version 1's 58% at 1080p
		Box spawn = box("spawn", "Spawn", false, true, Background.NONE, Align.CENTER, Anchor.MIDDLE, 0, Anchor.MIDDLE, 18);
		spawn.valueBold = true;
		spawn.lines.add(new Line(HudValue.SPAWN_TIMER, null, true, 0));
		spawn.lines.add(new Line(HudValue.AVERAGE_SPAWN, "Avg: ", true, 0));
		layout.boxes.add(spawn);

		Box shaft = box("shaft", "{shaft_type} Shaft", true, false, Background.FRAME, Align.LEFT, Anchor.END, 0, Anchor.START, 0);
		shaft.lines.add(new Line(HudValue.SHAFT_TIME, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CORPSES, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_COLD, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CAMPFIRES, null, true, 0));
		layout.boxes.add(shaft);
		return layout;
	}

	// inMines = also shown in the Dwarven Mines outside a shaft. every default box shows in a shaft
	private static Box box(String id, String title, boolean showTitle, boolean inMines, Background background,
			Align align, Anchor anchorX, int offsetX, Anchor anchorY, int offsetY) {
		Box box = new Box();
		box.id = id;
		box.title = title;
		box.showTitle = showTitle;
		box.showInMines = inMines;
		box.background = background;
		box.align = align;
		box.anchorX = anchorX;
		box.offsetX = offsetX;
		box.anchorY = anchorY;
		box.offsetY = offsetY;
		return box;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	// reads a saved layout and repairs what a hand edit might have broken. missing fields get their
	// defaults, values out of range get clamped, lines with unknown values get dropped
	// throws JsonParseException if the text isnt a layout at all
	public static HudLayout fromJson(String json) {
		HudLayout layout = GSON.fromJson(json, HudLayout.class);
		if (layout == null || layout.boxes == null) throw new JsonParseException("No boxes in the HUD file");
		layout.boxes.removeIf(box -> box == null);
		Set<String> ids = new HashSet<>();
		for (Box box : layout.boxes) {
			repair(box);
			// ids have to be there and unique, share codes and later features find boxes by them
			if (box.id == null || box.id.isBlank() || !ids.add(box.id)) {
				int n = 1;
				while (!ids.add("box" + n)) n++;
				box.id = "box" + n;
			}
		}
		layout.version = VERSION;
		return layout;
	}

	private static void repair(Box box) {
		if (box.title == null) box.title = "";
		if (box.visibility != null) convertVersion2(box);
		if (box.background == null) box.background = Background.DARK;
		if (box.align == null) box.align = Align.LEFT;
		if (!isColor(box.labelColor)) box.labelColor = "7";
		if (!isColor(box.valueColor)) box.valueColor = "f";
		box.scale = clamp(box.scale == 0 ? 1 : box.scale, MIN_SCALE, MAX_SCALE);
		if (box.anchorX == null) box.anchorX = Anchor.START;
		if (box.anchorY == null) box.anchorY = Anchor.START;
		if (box.x != null || box.y != null) convertVersion1(box);
		if (box.lines == null) box.lines = new ArrayList<>();
		box.lines.removeIf(line -> line == null || line.hudValue() == null);
		for (Line line : box.lines) {
			HudValue value = line.hudValue();
			if (line.prefix == null) line.prefix = value.defaultPrefix();
			int formats = HudFormat.options(value.kind()).size();
			if (line.format < 0 || line.format >= formats) line.format = 0;
			// files from before ranges dont have one, those values were all session values back then
			if (!value.hasRange(line.range)) line.range = value.defaultRange();
			if (HudColors.readyRule(value) == null) line.readyRule = false;
			if (line.rules != null) {
				line.rules.removeIf(rule -> rule == null);
				for (ColorRule rule : line.rules) {
					if (rule.when == null) rule.when = ColorRule.When.OVER;
					if (!isColor(rule.color)) rule.color = "c";
					if (!Double.isFinite(rule.value)) rule.value = 0;
				}
				if (line.rules.isEmpty() || !value.hasNumber()) line.rules = null;
			}
			if (line.advanced && line.text == null) line.advanced = false;
		}
	}

	// turns version 2's single visibility choice into the 3 checkboxes
	private static void convertVersion2(Box box) {
		Visibility old = box.visibility;
		box.showInShaft = old != Visibility.OUTSIDE_SHAFT;
		box.showInMines = old != Visibility.IN_SHAFT;
		box.showElsewhere = old == Visibility.ALWAYS;
		box.visibility = null;
	}

	// turns a version 1 position (fraction of the free space) into an anchor + offset. exact for the
	// edges and the middle, anywhere else the box can land a few pixels off bc we dont know the box size
	// here. offsets are worked out for 1080p's auto GUI scale (480×270)
	private static void convertVersion1(Box box) {
		float fx = clamp(box.x == null ? 0 : box.x, 0, 1);
		float fy = clamp(box.y == null ? 0 : box.y, 0, 1);
		box.anchorX = anchorOf(fx);
		box.offsetX = offsetOf(box.anchorX, fx, 480);
		box.anchorY = anchorOf(fy);
		box.offsetY = offsetOf(box.anchorY, fy, 270);
		box.x = null;
		box.y = null;
	}

	private static Anchor anchorOf(float fraction) {
		return fraction < 1 / 3f ? Anchor.START : fraction > 2 / 3f ? Anchor.END : Anchor.MIDDLE;
	}

	private static int offsetOf(Anchor anchor, float fraction, int screen) {
		int usable = screen - 2 * MARGIN;
		return Math.round(switch (anchor) {
			case START -> fraction * usable;
			case MIDDLE -> (fraction - 0.5f) * usable;
			case END -> (1 - fraction) * usable;
		});
	}

	private static boolean isColor(String code) {
		return code != null && code.length() == 1 && COLOR_CODES.indexOf(code.charAt(0)) >= 0;
	}

	private static float clamp(float value, float min, float max) {
		return Float.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
