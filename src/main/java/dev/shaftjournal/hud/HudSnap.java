package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.List;

import dev.shaftjournal.hud.HudLayout.Anchor;

/**
 * Snapping for the Edit HUD screen, as approved by Nick (Docs/mockups/hud-snapping.html). While a box
 * is dragged, each axis on its own snaps when an edge or the middle of the box comes within
 * {@link #DISTANCE} of a target:
 * <ul>
 * <li>the screen margin on each side, and the middle of the screen;</li>
 * <li>another box's start, end or middle (lined up);</li>
 * <li>just past another box with a gap of one margin (stacked), only when the boxes are side by side
 * on the other axis.</li>
 * </ul>
 * The closest target wins; on a tie the screen wins. Targets the box can't reach without crossing
 * the margin are skipped. A snap can also pick the box's {@link Anchor}, so it stays lined up when
 * the window or its text changes. Plain Java; GUI pixels.
 */
public final class HudSnap {
	/** How close an edge must come to snap, in GUI pixels. */
	public static final int DISTANCE = 4;
	/** How far guide lines for lined-up boxes reach past the boxes. */
	private static final int OVERHANG = 3;
	/** The gap label's size in Minecraft's font: one digit is 6 pixels wide with its spacing, a line 9 high. */
	private static final int LABEL_WIDTH = 6;
	private static final int LABEL_HEIGHT = 9;

	/** A box on screen, with the anchors it's placed from. */
	public record Rect(double x, double y, double width, double height, Anchor anchorX, Anchor anchorY) {
		Rect transposed() {
			return new Rect(y, x, height, width, anchorY, anchorX);
		}
	}

	/**
	 * A guide to draw: a filled area (1 pixel wide for a line), and for a gap its label, placed
	 * outside both boxes so it doesn't cover their text.
	 */
	public record Guide(int x, int y, int width, int height, String label, int labelX, int labelY) {
		Guide transposed() {
			return new Guide(y, x, height, width, label, labelY, labelX);
		}
	}

	/**
	 * Where the box ends up. {@code anchorX}/{@code anchorY} are set when the snap decides how the
	 * box should be anchored on that axis (a screen snap, or lining up with a box anchored on the same
	 * edge); null leaves it to the editor.
	 */
	public record Result(double x, double y, Anchor anchorX, Anchor anchorY, List<Guide> guides) {
	}

	/**
	 * A target on one axis. {@code edge} is which part of the dragged box meets it: 0 its start,
	 * 0.5 its middle, 1 its end. {@code other} is null for the screen; {@code gapStart} is set for a
	 * stacked snap.
	 */
	private record Target(double edge, double value, Rect other, Double gapStart) {
	}

	private record Hit(double start, Target target) {
	}

	private HudSnap() {
	}

	/**
	 * @param box    the dragged box where the mouse puts it
	 * @param others every other box on screen
	 * @param margin the screen margin, also the gap between stacked boxes
	 */
	public static Result snap(Rect box, List<Rect> others, int screenWidth, int screenHeight, int margin) {
		Hit hx = snapAxis(box, others, screenWidth, margin);
		List<Rect> othersT = others.stream().map(Rect::transposed).toList();
		Hit hy = snapAxis(box.transposed(), othersT, screenHeight, margin);
		Rect placed = new Rect(hx.start(), hy.start(), box.width(), box.height(), box.anchorX(), box.anchorY());

		List<Guide> guides = new ArrayList<>();
		if (hx.target() != null) {
			guides.add(guide(placed, hx.target(), screenHeight, margin, LABEL_WIDTH, LABEL_HEIGHT));
		}
		if (hy.target() != null) {
			guides.add(guide(placed.transposed(), hy.target(), screenWidth, margin, LABEL_HEIGHT, LABEL_WIDTH).transposed());
		}
		return new Result(placed.x(), placed.y(), anchor(hx), anchor(hy), guides);
	}

	/** Snaps the box's x; the y axis goes through here transposed. */
	private static Hit snapAxis(Rect box, List<Rect> others, int screen, int margin) {
		List<Target> targets = new ArrayList<>();
		// Screen first, so it wins a tie.
		targets.add(new Target(0, margin, null, null));
		targets.add(new Target(1, screen - margin, null, null));
		targets.add(new Target(0.5, screen / 2.0, null, null));
		for (Rect o : others) {
			targets.add(new Target(0, o.x(), o, null));
			targets.add(new Target(1, o.x() + o.width(), o, null));
			targets.add(new Target(0.5, o.x() + o.width() / 2, o, null));
			if (sideBySide(box, o)) {
				targets.add(new Target(0, o.x() + o.width() + margin, o, o.x() + o.width()));
				targets.add(new Target(1, o.x() - margin, o, o.x() - margin));
			}
		}
		Target best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Target t : targets) {
			double start = t.value() - t.edge() * box.width();
			// A spot past the margin can't be reached: the box would be pushed back and the guide would lie.
			if (start < margin || start + box.width() > screen - margin) continue;
			double distance = Math.abs(start - box.x());
			if (distance <= DISTANCE && distance < bestDistance) {
				best = t;
				bestDistance = distance;
			}
		}
		return best == null ? new Hit(box.x(), null) : new Hit(best.value() - best.edge() * box.width(), best);
	}

	/** True when the boxes overlap (or nearly) on the other axis, so stacking them along this one makes sense. */
	private static boolean sideBySide(Rect box, Rect o) {
		return box.y() < o.y() + o.height() + DISTANCE && o.y() < box.y() + box.height() + DISTANCE;
	}

	/**
	 * The screen gives the anchor of the edge that snapped. Lining up with a box keeps them lined up
	 * only when both are anchored on that edge, so then the dragged box takes the other's anchor.
	 */
	private static Anchor anchor(Hit hit) {
		Target t = hit.target();
		if (t == null || t.gapStart() != null) return null;
		Anchor edge = Anchor.ofEdge(t.edge());
		if (t.other() == null) return edge;
		return t.other().anchorX() == edge ? edge : null;
	}

	/**
	 * The guide for an x-axis snap; {@code crossLength} is the screen's height. The label size is
	 * given along and across the axis, so the transposed y axis can swap them.
	 */
	private static Guide guide(Rect box, Target t, int crossLength, int margin, int labelAlong, int labelAcross) {
		Rect o = t.other();
		if (t.gapStart() != null) {
			double top = Math.max(box.y(), o.y());
			double bottom = Math.min(box.y() + box.height(), o.y() + o.height());
			int middle = (int) Math.floor((top + bottom) / 2);
			int gap = (int) Math.round(t.gapStart());
			// The label goes above both boxes, or below them when there's no room above.
			int labelAt = (int) Math.round(t.gapStart() + margin / 2.0 - labelAlong / 2.0);
			int above = (int) Math.floor(Math.min(box.y(), o.y())) - labelAcross - 1;
			int labelAcrossAt = above >= 0 ? above : (int) Math.ceil(Math.max(box.y() + box.height(), o.y() + o.height())) + 2;
			return new Guide(gap, middle, margin, 1, Integer.toString(margin), labelAt, labelAcrossAt);
		}
		// On the box outline: just before a start edge, just after an end edge.
		int column = (int) Math.floor(t.value()) - (t.edge() == 0 ? 1 : 0);
		if (o == null) return new Guide(column, 0, 1, crossLength, null, 0, 0);
		int top = (int) Math.floor(Math.min(box.y(), o.y())) - OVERHANG;
		int bottom = (int) Math.ceil(Math.max(box.y() + box.height(), o.y() + o.height())) + OVERHANG;
		return new Guide(column, top, 1, bottom - top, null, 0, 0);
	}
}
