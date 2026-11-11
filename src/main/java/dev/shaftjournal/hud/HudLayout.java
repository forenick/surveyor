package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * Every HUD box and its settings: what {@code hud.json} holds. Mutable, because the editor changes
 * it in place; only touched on the client thread, except for {@link #toJson()} copies. Plain Java.
 */
public final class HudLayout {
	/** 2: positions are an anchor plus an offset in GUI pixels (1 had fractions of the free space). */
	public static final int VERSION = 2;
	/** Space between a box and the screen edge; also the gap between snapped boxes. Nick's choice (2026-11-11). */
	public static final int MARGIN = 4;
	/** The smallest and largest box size. */
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2;
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

	/**
	 * Where a box is placed from, per axis: the screen margin at the start (left/top), the middle of the
	 * screen, or the margin at the end (right/bottom). The same edge (or the middle) of the box stays put
	 * when the window, the GUI scale or the box's text changes, so boxes lined up on that edge stay lined up.
	 */
	public enum Anchor {
		START, MIDDLE, END;

		/** The anchor a snap to this part of a box gives: 0 its start, 0.5 its middle, 1 its end. */
		public static Anchor ofEdge(double edge) {
			return edge == 0 ? START : edge == 1 ? END : MIDDLE;
		}

		/** For a box placed freely: the third of the screen its middle is in. */
		public static Anchor forPosition(int start, int size, int screen) {
			double middle = start + size / 2.0;
			return middle < screen / 3.0 ? START : middle > screen * 2 / 3.0 ? END : MIDDLE;
		}

		/** Where a box with this offset starts (its left or top), kept inside the margins. */
		public int start(int offset, int size, int screen) {
			int start = switch (this) {
				case START -> MARGIN + offset;
				case MIDDLE -> centred(size, screen) + offset;
				case END -> screen - MARGIN - size - offset;
			};
			return clamp(start, size, screen);
		}

		/** The offset that puts the box's start at {@code start}: inwards from the margin, or right/down of the middle. */
		public int offset(int start, int size, int screen) {
			return switch (this) {
				case START -> start - MARGIN;
				case MIDDLE -> start - centred(size, screen);
				case END -> screen - MARGIN - size - start;
			};
		}

		/** Keeps a box's start inside the margins; a box too big for the screen starts at the margin. */
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
		public Anchor anchorX = Anchor.START;
		public Anchor anchorY = Anchor.START;
		/** GUI pixels from the anchor: inwards from the margin for START and END, right/down of the middle for MIDDLE. */
		public int offsetX;
		public int offsetY;
		public List<Line> lines = new ArrayList<>();
		/** Version 1 position, a fraction of the free space. Only read to convert old files; never written. */
		Float x;
		Float y;
	}

	public int version = VERSION;
	public List<Box> boxes = new ArrayList<>();

	/** The three default boxes from the approved mockup (Docs/mockups/hud-v1.html). */
	public static HudLayout defaults() {
		HudLayout layout = new HudLayout();

		Box session = box("session", "Session", true, Visibility.MINES, Background.DARK, Align.LEFT, Anchor.START, 0, Anchor.START, 0);
		session.lines.add(new Line(HudValue.SESSION_SHAFTS, null, true, 0));
		session.lines.add(new Line(HudValue.SHAFTS_PER_HOUR, null, true, 0));
		session.lines.add(new Line(HudValue.AVERAGE_SPAWN, null, false, 1));
		session.lines.add(new Line(HudValue.SESSION_POWDER, null, true, 0));
		session.lines.add(new Line(HudValue.SESSION_CORPSE_FINE, null, true, 0));
		layout.boxes.add(session);

		// Just under the crosshair: the same place as version 1's 58% at 1080p.
		Box spawn = box("spawn", "Spawn", false, Visibility.MINES, Background.NONE, Align.CENTER, Anchor.MIDDLE, 0, Anchor.MIDDLE, 18);
		spawn.valueBold = true;
		spawn.lines.add(new Line(HudValue.SPAWN_TIMER, null, true, 0));
		spawn.lines.add(new Line(HudValue.AVERAGE_SPAWN, "Avg: ", true, 0));
		layout.boxes.add(spawn);

		Box shaft = box("shaft", "{shaft_type} Shaft", true, Visibility.IN_SHAFT, Background.FRAME, Align.LEFT, Anchor.END, 0, Anchor.START, 0);
		shaft.lines.add(new Line(HudValue.SHAFT_TIME, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CORPSES, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_COLD, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CAMPFIRES, null, true, 0));
		layout.boxes.add(shaft);
		return layout;
	}

	private static Box box(String id, String title, boolean showTitle, Visibility visibility, Background background,
			Align align, Anchor anchorX, int offsetX, Anchor anchorY, int offsetY) {
		Box box = new Box();
		box.id = id;
		box.title = title;
		box.showTitle = showTitle;
		box.visibility = visibility;
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
		Set<String> ids = new HashSet<>();
		for (Box box : layout.boxes) {
			repair(box);
			// Ids must be there and unique; share codes and later features find boxes by them.
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
		if (box.visibility == null) box.visibility = Visibility.MINES;
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
			if (line.prefix == null) line.prefix = line.hudValue().defaultPrefix();
			int formats = HudFormat.options(line.hudValue().kind()).size();
			if (line.format < 0 || line.format >= formats) line.format = 0;
		}
	}

	/**
	 * Turns a version 1 position (a fraction of the free space) into an anchor and offset. Exact for
	 * the edges and the middle; elsewhere the box may land a few pixels off, as the box size isn't known
	 * here. Offsets are worked out for 1080p's auto GUI scale (480×270).
	 */
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
		return code != null && code.length() == 1 && "0123456789abcdef".indexOf(code.charAt(0)) >= 0;
	}

	private static float clamp(float value, float min, float max) {
		return Float.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
