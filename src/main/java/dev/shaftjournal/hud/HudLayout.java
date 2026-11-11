package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Every HUD box and its settings: what {@code hud.json} holds. Mutable, because the editor changes
 * it in place; only touched on the client thread, except for {@link #toJson()} copies. Plain Java.
 */
public final class HudLayout {
	public static final int VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public enum Visibility {
		ALWAYS("Always"),
		MINES("Mines + shafts"),
		IN_SHAFT("Only in a shaft"),
		OUTSIDE_SHAFT("Mines, not in a shaft");

		/** The editor's name; "Mines" is wherever the tab list says Dwarven Mines. */
		public final String label;

		Visibility(String label) {
			this.label = label;
		}

		public boolean visible(boolean inMines, boolean inShaft) {
			return switch (this) {
				case ALWAYS -> true;
				case MINES -> inMines || inShaft;
				case IN_SHAFT -> inShaft;
				case OUTSIDE_SHAFT -> inMines && !inShaft;
			};
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
		/** Text before the value; the value's default prefix unless changed in the file. */
		public String prefix;
		public boolean shown = true;
		/** Index into {@link HudFormat#options}. */
		public int format;

		public Line() {
		}

		Line(HudValue value, String prefix, boolean shown, int format) {
			this.value = value.id();
			this.prefix = prefix == null ? value.defaultPrefix() : prefix;
			this.shown = shown;
			this.format = format;
		}

		/** Null when the file names a value this version doesn't know. */
		public HudValue hudValue() {
			return HudValue.byId(value);
		}
	}

	public static final class Box {
		public String id;
		/** May contain value tags like {shaft_type}. */
		public String title = "";
		public boolean showTitle;
		public Visibility visibility = Visibility.MINES;
		public Background background = Background.DARK;
		public float scale = 1;
		public Align align = Align.LEFT;
		/** Minecraft colour codes, "0" to "f". */
		public String labelColor = "7";
		public String valueColor = "f";
		public boolean labelBold;
		public boolean valueBold;
		/**
		 * Position as a fraction of the free space: 0 is the left/top edge, 1 the right/bottom edge,
		 * so a box at an edge stays there when the window or GUI scale changes.
		 */
		public float x;
		public float y;
		public List<Line> lines = new ArrayList<>();
	}

	public int version = VERSION;
	public List<Box> boxes = new ArrayList<>();

	/** The three default boxes from the approved mockup (Docs/mockups/hud-v1.html). */
	public static HudLayout defaults() {
		HudLayout layout = new HudLayout();

		Box session = box("session", "Session", true, Visibility.MINES, Background.DARK, Align.LEFT, 0, 0);
		session.lines.add(new Line(HudValue.SESSION_SHAFTS, null, true, 0));
		session.lines.add(new Line(HudValue.SHAFTS_PER_HOUR, null, true, 0));
		session.lines.add(new Line(HudValue.AVERAGE_SPAWN, null, false, 1));
		session.lines.add(new Line(HudValue.SESSION_POWDER, null, true, 0));
		session.lines.add(new Line(HudValue.SESSION_CORPSE_FINE, null, true, 0));
		layout.boxes.add(session);

		Box spawn = box("spawn", "Spawn", false, Visibility.MINES, Background.NONE, Align.CENTER, 0.5f, 0.58f);
		spawn.valueBold = true;
		spawn.lines.add(new Line(HudValue.SPAWN_TIMER, null, true, 0));
		spawn.lines.add(new Line(HudValue.AVERAGE_SPAWN, "Avg: ", true, 0));
		layout.boxes.add(spawn);

		Box shaft = box("shaft", "{shaft_type} Shaft", true, Visibility.IN_SHAFT, Background.FRAME, Align.LEFT, 1, 0);
		shaft.lines.add(new Line(HudValue.SHAFT_TIME, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CORPSES, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_COLD, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CAMPFIRES, null, true, 0));
		layout.boxes.add(shaft);
		return layout;
	}

	private static Box box(String id, String title, boolean showTitle, Visibility visibility, Background background,
			Align align, float x, float y) {
		Box box = new Box();
		box.id = id;
		box.title = title;
		box.showTitle = showTitle;
		box.visibility = visibility;
		box.background = background;
		box.align = align;
		box.x = x;
		box.y = y;
		return box;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	/**
	 * Reads a saved layout, repairing what a hand edit may have broken: missing fields get their
	 * defaults, values out of range are clamped, and lines with unknown values are dropped.
	 *
	 * @throws JsonParseException when the text isn't a layout at all
	 */
	public static HudLayout fromJson(String json) {
		HudLayout layout = GSON.fromJson(json, HudLayout.class);
		if (layout == null || layout.boxes == null) throw new JsonParseException("No boxes in the HUD file");
		layout.boxes.removeIf(box -> box == null);
		for (Box box : layout.boxes) repair(box);
		return layout;
	}

	private static void repair(Box box) {
		if (box.title == null) box.title = "";
		if (box.visibility == null) box.visibility = Visibility.MINES;
		if (box.background == null) box.background = Background.DARK;
		if (box.align == null) box.align = Align.LEFT;
		if (!isColor(box.labelColor)) box.labelColor = "7";
		if (!isColor(box.valueColor)) box.valueColor = "f";
		box.scale = clamp(box.scale == 0 ? 1 : box.scale, 0.5f, 2);
		box.x = clamp(box.x, 0, 1);
		box.y = clamp(box.y, 0, 1);
		if (box.lines == null) box.lines = new ArrayList<>();
		box.lines.removeIf(line -> line == null || line.hudValue() == null);
		for (Line line : box.lines) {
			if (line.prefix == null) line.prefix = line.hudValue().defaultPrefix();
			int formats = HudFormat.options(line.hudValue().kind()).size();
			if (line.format < 0 || line.format >= formats) line.format = 0;
		}
	}

	private static boolean isColor(String code) {
		return code != null && code.length() == 1 && "0123456789abcdef".indexOf(code.charAt(0)) >= 0;
	}

	private static float clamp(float value, float min, float max) {
		return Float.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
