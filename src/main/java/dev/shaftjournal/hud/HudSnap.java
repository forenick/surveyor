package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.List;

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
 * The closest target wins; on a tie the screen wins. A screen snap also gives the exact position
 * fraction (0, 0.5 or 1), so the box stays lined up at any window size. Plain Java; GUI pixels.
 */
public final class HudSnap {
	/** How close an edge must come to snap, in GUI pixels. */
	public static final int DISTANCE = 4;
	/** How far guide lines for lined-up boxes reach past the boxes. */
	private static final int OVERHANG = 3;

	public record Rect(double x, double y, double width, double height) {
		Rect transposed() {
			return new Rect(y, x, height, width);
		}
	}

	/** A guide to draw: a filled area (1 pixel wide for a line) and an optional label beside it. */
	public record Guide(int x, int y, int width, int height, String label) {
		Guide transposed() {
			return new Guide(y, x, height, width, label);
		}
	}

	/**
	 * Where the box ends up. {@code fractionX}/{@code fractionY} are set when that axis snapped to
	 * the screen: the position to save instead of one worked out from pixels.
	 */
	public record Result(double x, double y, Float fractionX, Float fractionY, List<Guide> guides) {
	}

	/**
	 * A target on one axis. {@code edge} is which part of the dragged box meets it: 0 its start,
	 * 0.5 its middle, 1 its end. {@code other} is null for the screen; {@code gapStart} is set for a
	 * stacked snap.
	 */
	private record Target(double edge, double value, Float fraction, Rect other, Double gapStart) {
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
		Rect placed = new Rect(hx.start(), hy.start(), box.width(), box.height());

		List<Guide> guides = new ArrayList<>();
		if (hx.target() != null) guides.add(guide(placed, hx.target(), screenHeight, margin));
		if (hy.target() != null) guides.add(guide(placed.transposed(), hy.target(), screenWidth, margin).transposed());
		return new Result(placed.x(), placed.y(), fraction(hx), fraction(hy), guides);
	}

	/** Snaps the box's x; the y axis goes through here transposed. */
	private static Hit snapAxis(Rect box, List<Rect> others, int screen, int margin) {
		List<Target> targets = new ArrayList<>();
		// Screen first, so it wins a tie.
		targets.add(new Target(0, margin, 0f, null, null));
		targets.add(new Target(1, screen - margin, 1f, null, null));
		targets.add(new Target(0.5, screen / 2.0, 0.5f, null, null));
		for (Rect o : others) {
			targets.add(new Target(0, o.x(), null, o, null));
			targets.add(new Target(1, o.x() + o.width(), null, o, null));
			targets.add(new Target(0.5, o.x() + o.width() / 2, null, o, null));
			if (sideBySide(box, o)) {
				targets.add(new Target(0, o.x() + o.width() + margin, null, o, o.x() + o.width()));
				targets.add(new Target(1, o.x() - margin, null, o, o.x() - margin));
			}
		}
		Target best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Target t : targets) {
			double distance = Math.abs(t.value() - (box.x() + t.edge() * box.width()));
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

	private static Float fraction(Hit hit) {
		return hit.target() == null ? null : hit.target().fraction();
	}

	/** The guide for an x-axis snap; {@code crossLength} is the screen's height. */
	private static Guide guide(Rect box, Target t, int crossLength, int margin) {
		Rect o = t.other();
		if (t.gapStart() != null) {
			double top = Math.max(box.y(), o.y());
			double bottom = Math.min(box.y() + box.height(), o.y() + o.height());
			int middle = (int) Math.floor((top + bottom) / 2);
			return new Guide((int) Math.round(t.gapStart()), middle, margin, 1, Integer.toString(margin));
		}
		// On the box outline: just before a start edge, just after an end edge.
		int column = (int) Math.floor(t.value()) - (t.edge() == 0 ? 1 : 0);
		if (o == null) return new Guide(column, 0, 1, crossLength, null);
		int top = (int) Math.floor(Math.min(box.y(), o.y())) - OVERHANG;
		int bottom = (int) Math.ceil(Math.max(box.y() + box.height(), o.y() + o.height())) + OVERHANG;
		return new Guide(column, top, 1, bottom - top, null);
	}
}
