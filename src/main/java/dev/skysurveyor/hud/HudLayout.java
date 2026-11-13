package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.skysurveyor.hud.HudValue.Range;

// client thread only, except toJson() copies
public final class HudLayout {
	public static final int VERSION = 3;
	// nicks pick (2026-11-11)
	public static final int MARGIN = 4;
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2;
	public static final String COLOR_CODES = "0123456789abcdef";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	enum Visibility {
		ALWAYS, MINES, IN_SHAFT, OUTSIDE_SHAFT
	}

	public enum Anchor {
		START, MIDDLE, END;

		// edge: 0 start, 0.5 middle, 1 end
		public static Anchor ofEdge(double edge) {
			return edge == 0 ? START : edge == 1 ? END : MIDDLE;
		}

		public static Anchor forPosition(int start, int size, int screen) {
			double middle = start + size / 2.0;
			return middle < screen / 3.0 ? START : middle > screen * 2 / 3.0 ? END : MIDDLE;
		}

		public int start(int offset, int size, int screen) {
			int start = switch (this) {
				case START -> MARGIN + offset;
				case MIDDLE -> centred(size, screen) + offset;
				case END -> screen - MARGIN - size - offset;
			};
			return clamp(start, size, screen);
		}

		public int offset(int start, int size, int screen) {
			return switch (this) {
				case START -> start - MARGIN;
				case MIDDLE -> start - centred(size, screen);
				case END -> screen - MARGIN - size - start;
			};
		}

		// too big for the screen = starts at the margin
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
		public String prefix;
		// null = box colour
		public String labelColor;
		// null = box colour
		public String valueColor;
		public boolean shown = true;
		public int format;
		public Range range;
		// text kept when switched off
		public boolean advanced;
		public String text;
		public boolean readyRule;
		public List<ColorRule> rules;

		public Line() {
		}

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

		public HudValue hudValue() {
			return HudValue.byId(value);
		}
	}

	public static final class ColorRule {
		public enum When {
			OVER, UNDER
		}

		public When when = When.OVER;
		// seconds for times, not ms
		public double value;
		// a value tag like {average_spawn}, beats value. null = value
		public String than;
		public String color = "c";

		public boolean matches(double number, double limit) {
			return when == When.UNDER ? number < limit : number > limit;
		}
	}

	public static final class Box {
		public String id;
		public String title = "";
		public boolean showTitle;
		public boolean showInShaft = true;
		public boolean showInMines = true;
		public boolean showElsewhere;
		public Background background = Background.DARK;
		public float scale = 1;
		public Align align = Align.LEFT;
		public String labelColor = "7";
		public String valueColor = "f";
		public boolean labelBold;
		public boolean valueBold;
		public Anchor anchorX = Anchor.START;
		public Anchor anchorY = Anchor.START;
		// inwards for START/END, right/down for MIDDLE
		public int offsetX;
		public int offsetY;
		public List<Line> lines = new ArrayList<>();
		// v1/v2 only, read to convert, never written
		Float x;
		Float y;
		Visibility visibility;

		// shaft wins over mines
		public boolean visible(boolean inMines, boolean inShaft) {
			return inShaft ? showInShaft : inMines ? showInMines : showElsewhere;
		}

		// gap 0 = above first, lines.size() = below last
		public void moveLine(int from, int gap) {
			if (from < 0 || from >= lines.size() || gap < 0 || gap > lines.size()) return;
			Line line = lines.remove(from);
			lines.add(gap > from ? gap - 1 : gap, line);
		}
	}

	public int version = VERSION;
	public List<Box> boxes = new ArrayList<>();

	// from Docs/mockups/hud-v1.html
	public static HudLayout defaults() {
		HudLayout layout = new HudLayout();
		layout.boxes.add(sessionBox());
		layout.boxes.add(spawnBox());
		layout.boxes.add(shaftBox());
		return layout;
	}

	public record Preset(String name, String description, Supplier<Box> box) {
	}

	// nicks picks (2026-11-12), lines matter more than looks
	public static List<Preset> presets() {
		return List.of(
				new Preset("Spawn", "Spawn timer and average spawn", HudLayout::spawnBox),
				new Preset("Session", "Shafts, shafts per hour, powder and Fine", HudLayout::sessionBox),
				new Preset("Current shaft", "Time inside, corpses, cold and campfires", HudLayout::shaftBox),
				new Preset("All-time totals", "Shafts, powder and Fine, all-time", HudLayout::allTimeBox),
				new Preset("Money", "Coins this shaft, this session, per hour and per shaft", HudLayout::moneyBox),
				new Preset("Performance", "FPS, ping and TPS", HudLayout::performanceBox));
	}

	private static Box sessionBox() {
		Box session = box("session", "Session", true, true, Background.DARK, Align.LEFT, Anchor.START, 0, Anchor.START, 0);
		session.lines.add(new Line(HudValue.SESSION_SHAFTS, null, true, 0));
		session.lines.add(new Line(HudValue.SHAFTS_PER_HOUR, null, true, 0));
		session.lines.add(new Line(HudValue.AVERAGE_SPAWN, null, false, 1));
		session.lines.add(new Line(HudValue.SESSION_POWDER, null, true, 0));
		session.lines.add(new Line(HudValue.SESSION_CORPSE_FINE, null, true, 0));
		return session;
	}

	private static Box spawnBox() {
		// = v1s 58% at 1080p, under the crosshair
		Box spawn = box("spawn", "Spawn", false, true, Background.NONE, Align.CENTER, Anchor.MIDDLE, 0, Anchor.MIDDLE, 18);
		spawn.valueBold = true;
		spawn.lines.add(new Line(HudValue.SPAWN_TIMER, null, true, 0));
		spawn.lines.add(new Line(HudValue.AVERAGE_SPAWN, "Avg: ", true, 0));
		return spawn;
	}

	private static Box shaftBox() {
		Box shaft = box("shaft", "{shaft_type} Shaft", true, false, Background.FRAME, Align.LEFT, Anchor.END, 0, Anchor.START, 0);
		shaft.lines.add(new Line(HudValue.SHAFT_TIME, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CORPSES, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_COLD, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CAMPFIRES, null, true, 0));
		return shaft;
	}

	private static Box allTimeBox() {
		Box box = box("all_time", "All-time", true, true, Background.DARK, Align.LEFT, Anchor.START, 0, Anchor.MIDDLE, 0);
		for (HudValue value : List.of(HudValue.SESSION_SHAFTS, HudValue.SESSION_POWDER, HudValue.SESSION_CORPSE_FINE)) {
			Line line = new Line(value);
			line.range = Range.ALL_TIME;
			box.lines.add(line);
		}
		return box;
	}

	private static Box moneyBox() {
		Box box = box("money", "Money", true, true, Background.DARK, Align.LEFT, Anchor.END, 0, Anchor.MIDDLE, 0);
		box.valueColor = "6";
		box.lines.add(rangeLine(HudValue.COINS, "This shaft: ", Range.SHAFT));
		box.lines.add(rangeLine(HudValue.COINS, "Session: ", Range.SESSION));
		box.lines.add(rangeLine(HudValue.COINS, "Per hour: ", Range.PER_HOUR));
		box.lines.add(rangeLine(HudValue.COINS_PER_SHAFT, "Per shaft: ", Range.SESSION));
		return box;
	}

	private static Line rangeLine(HudValue value, String prefix, Range range) {
		Line line = new Line(value, prefix, true, 0);
		line.range = range;
		return line;
	}

	private static Box performanceBox() {
		Box box = box("performance", "Performance", false, true, Background.DARK, Align.LEFT, Anchor.END, 0, Anchor.END, 0);
		box.showElsewhere = true;
		box.lines.add(new Line(HudValue.FPS));
		box.lines.add(new Line(HudValue.PING));
		box.lines.add(new Line(HudValue.TPS));
		return box;
	}

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

	public static HudLayout fromJson(String json) {
		HudLayout layout = GSON.fromJson(json, HudLayout.class);
		if (layout == null || layout.boxes == null) throw new JsonParseException("No boxes in the HUD file");
		layout.boxes.removeIf(box -> box == null);
		Set<String> ids = new HashSet<>();
		for (Box box : layout.boxes) {
			repair(box);
			// share codes find boxes by id
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
			if (!isColor(line.labelColor)) line.labelColor = null;
			if (!isColor(line.valueColor)) line.valueColor = null;
			int formats = HudFormat.options(value.kind()).size();
			if (line.format < 0 || line.format >= formats) line.format = 0;
			// pre-range files have none, were all session values
			if (!value.hasRange(line.range)) line.range = value.defaultRange();
			if (HudColors.readyRule(value) == null) line.readyRule = false;
			if (line.rules != null) {
				line.rules.removeIf(rule -> rule == null);
				for (ColorRule rule : line.rules) {
					if (rule.when == null) rule.when = ColorRule.When.OVER;
					if (!isColor(rule.color)) rule.color = "c";
					if (!Double.isFinite(rule.value)) rule.value = 0;
					if (rule.than != null && !HudBoxText.isLimitTag(rule.than, value)) rule.than = null;
				}
				if (line.rules.isEmpty() || !value.hasNumber()) line.rules = null;
			}
			if (line.advanced && line.text == null) line.advanced = false;
		}
	}

	private static void convertVersion2(Box box) {
		Visibility old = box.visibility;
		box.showInShaft = old != Visibility.OUTSIDE_SHAFT;
		box.showInMines = old != Visibility.IN_SHAFT;
		box.showElsewhere = old == Visibility.ALWAYS;
		box.visibility = null;
	}

	// 480×270 = 1080p auto gui scale. non-edge boxes can land a few px off
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
