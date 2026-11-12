package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.List;

import dev.shaftjournal.hud.HudLayout.Anchor;

// snapping for the Edit HUD screen, how Nick approved it (Docs/mockups/hud-snapping.html). while u drag
// a box, each axis snaps on its own when an edge or the middle of the box gets within DISTANCE of a target.
// targets are the screen margin on each side and the middle of the screen, another box's start, end or
// middle (lined up), and just past another box w/ a gap of one margin (stacked, only if the boxes are side
// by side on the other axis)
// closest target wins, on a tie the screen wins. targets the box cant reach without going past the margin
// get skipped. a snap can also pick the box's Anchor so it stays lined up when the window or its text
// changes. plain java, GUI pixels
public final class HudSnap {
	// how close an edge has to get to snap, in GUI pixels
	public static final int DISTANCE = 4;
	// how far guide lines for lined up boxes stick out past the boxes
	private static final int OVERHANG = 3;
	// gap label size in Minecraft's font. one digit is 6 pixels wide w/ its spacing, a line is 9 high
	private static final int LABEL_WIDTH = 6;
	private static final int LABEL_HEIGHT = 9;

	// a box on screen, w/ the anchors its placed from
	public record Rect(double x, double y, double width, double height, Anchor anchorX, Anchor anchorY) {
		Rect transposed() {
			return new Rect(y, x, height, width, anchorY, anchorX);
		}
	}

	// a guide to draw. a filled area (1 pixel wide for a line), and for a gap its label, placed
	// outside both boxes so it doesnt cover their text
	public record Guide(int x, int y, int width, int height, String label, int labelX, int labelY) {
		Guide transposed() {
			return new Guide(y, x, height, width, label, labelY, labelX);
		}
	}

	// where the box ends up. anchorX/anchorY get set when the snap decides how the box should be
	// anchored on that axis (a screen snap, or lining up w/ a box anchored on the same edge). null leaves
	// it to the editor
	public record Result(double x, double y, Anchor anchorX, Anchor anchorY, List<Guide> guides) {
	}

	// a target on one axis. edge is which part of the dragged box meets it, 0 its start, 0.5 its middle,
	// 1 its end. other is null for the screen, gapStart is set for a stacked snap
	private record Target(double edge, double value, Rect other, Double gapStart) {
	}

	private record Hit(double start, Target target) {
	}

	private HudSnap() {
	}

	// box = the dragged box where the mouse puts it, others = every other box on screen
	// margin = the screen margin, also the gap between stacked boxes
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

	// snaps the box's x. the y axis goes through here too, just transposed
	private static Hit snapAxis(Rect box, List<Rect> others, int screen, int margin) {
		List<Target> targets = new ArrayList<>();
		// screen first so it wins a tie
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
			// cant reach a spot past the margin, the box would get pushed back and the guide would be wrong
			if (start < margin || start + box.width() > screen - margin) continue;
			double distance = Math.abs(start - box.x());
			if (distance <= DISTANCE && distance < bestDistance) {
				best = t;
				bestDistance = distance;
			}
		}
		return best == null ? new Hit(box.x(), null) : new Hit(best.value() - best.edge() * box.width(), best);
	}

	// true if the boxes overlap (or almost) on the other axis, so stacking them along this one makes sense
	private static boolean sideBySide(Rect box, Rect o) {
		return box.y() < o.y() + o.height() + DISTANCE && o.y() < box.y() + box.height() + DISTANCE;
	}

	// the screen gives the anchor of the edge that snapped. lining up w/ a box only keeps them lined up
	// if both are anchored on that edge, so then the dragged box takes the other one's anchor
	private static Anchor anchor(Hit hit) {
		Target t = hit.target();
		if (t == null || t.gapStart() != null) return null;
		Anchor edge = Anchor.ofEdge(t.edge());
		if (t.other() == null) return edge;
		return t.other().anchorX() == edge ? edge : null;
	}

	// the guide for an x axis snap, crossLength is the screen's height. label size is given along and
	// across the axis so the transposed y axis can swap them
	private static Guide guide(Rect box, Target t, int crossLength, int margin, int labelAlong, int labelAcross) {
		Rect o = t.other();
		if (t.gapStart() != null) {
			double top = Math.max(box.y(), o.y());
			double bottom = Math.min(box.y() + box.height(), o.y() + o.height());
			int middle = (int) Math.floor((top + bottom) / 2);
			int gap = (int) Math.round(t.gapStart());
			// label goes above both boxes, or below them if there's no room above
			int labelAt = (int) Math.round(t.gapStart() + margin / 2.0 - labelAlong / 2.0);
			int above = (int) Math.floor(Math.min(box.y(), o.y())) - labelAcross - 1;
			int labelAcrossAt = above >= 0 ? above : (int) Math.ceil(Math.max(box.y() + box.height(), o.y() + o.height())) + 2;
			return new Guide(gap, middle, margin, 1, Integer.toString(margin), labelAt, labelAcrossAt);
		}
		// on the box outline, just before a start edge, just after an end edge
		int column = (int) Math.floor(t.value()) - (t.edge() == 0 ? 1 : 0);
		if (o == null) return new Guide(column, 0, 1, crossLength, null, 0, 0);
		int top = (int) Math.floor(Math.min(box.y(), o.y())) - OVERHANG;
		int bottom = (int) Math.ceil(Math.max(box.y() + box.height(), o.y() + o.height())) + OVERHANG;
		return new Guide(column, top, 1, bottom - top, null, 0, 0);
	}
}
