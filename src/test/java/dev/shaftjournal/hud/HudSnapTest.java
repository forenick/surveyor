package dev.shaftjournal.hud;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.shaftjournal.hud.HudLayout.Anchor;
import dev.shaftjournal.hud.HudSnap.Guide;
import dev.shaftjournal.hud.HudSnap.Rect;
import dev.shaftjournal.hud.HudSnap.Result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 480x270 (1080p auto gui scale), margin 4, nick approved
class HudSnapTest {
	private static final int W = 480;
	private static final int H = 270;
	private static final int M = 4;

	private static Rect rect(double x, double y, double w, double h) {
		return new Rect(x, y, w, h, Anchor.START, Anchor.START);
	}

	private static Result snap(Rect box, Rect... others) {
		return HudSnap.snap(box, List.of(others), W, H, M);
	}

	private static Guide line(int x, int y, int w, int h) {
		return new Guide(x, y, w, h, null, 0, 0);
	}

	@Test
	void snapsToTheScreenMarginAndAnchorsThere() {
		Result left = snap(rect(7, 100, 50, 20));
		assertEquals(4, left.x());
		assertEquals(Anchor.START, left.anchorX());
		assertEquals(List.of(line(3, 0, 1, H)), left.guides(), "full-height line just left of the box");

		Result right = snap(rect(W - M - 50 - 3, 100, 50, 20));
		assertEquals(W - M - 50, right.x());
		assertEquals(Anchor.END, right.anchorX());

		Result bottom = snap(rect(100, H - M - 20 + 2, 50, 20));
		assertEquals(H - M - 20, bottom.y());
		assertEquals(Anchor.END, bottom.anchorY());
		assertEquals(List.of(line(0, H - M, W, 1)), bottom.guides());
	}

	@Test
	void snapsToTheMiddleOfTheScreen() {
		Result r = snap(rect(212, 160, 50, 20)); // middle at 237, screen middle 240
		assertEquals(215, r.x());
		assertEquals(Anchor.MIDDLE, r.anchorX());
	}

	@Test
	void leavesABoxAloneWhenNothingIsClose() {
		Result r = snap(rect(100, 100, 50, 20), rect(300, 200, 40, 20));
		assertEquals(100, r.x());
		assertEquals(100, r.y());
		assertNull(r.anchorX());
		assertNull(r.anchorY());
		assertTrue(r.guides().isEmpty());
	}

	@Test
	void linesUpWithAnotherBox() {
		Rect other = rect(100, 100, 80, 40);
		Result r = snap(rect(102, 200, 60, 20), other);
		assertEquals(100, r.x());
		assertEquals(List.of(line(99, 97, 1, 126)), r.guides(), "line spans both boxes plus 3 px");

		Result right = snap(rect(118, 200, 60, 20), other); // right edges 178 vs 180
		assertEquals(120, right.x());
	}

	@Test
	void liningUpTakesTheOtherBoxsAnchorOnlyWhenItMatchesTheEdge() {
		Rect leftAnchored = rect(100, 100, 80, 40);
		assertEquals(Anchor.START, snap(rect(102, 200, 60, 20), leftAnchored).anchorX(),
				"left edges lined up and both anchored left: they stay lined up whatever the text");

		Rect rightAnchored = new Rect(100, 100, 80, 40, Anchor.END, Anchor.START);
		assertNull(snap(rect(102, 200, 60, 20), rightAnchored).anchorX(), "left edges, but the other grows from its right");
		assertEquals(Anchor.END, snap(rect(118, 200, 60, 20), rightAnchored).anchorX(), "right edges, both anchored right");
	}

	@Test
	void stacksUnderAnotherBoxWithAGap() {
		Rect session = rect(4, 4, 80, 40);
		Result r = snap(rect(30, 50, 60, 20), session);
		assertEquals(48, r.y(), "4 px under the box");
		assertEquals(30, r.x());
		assertNull(r.anchorY(), "a stacked box gets its anchor from where it is");
		// overlap 30..84 = marker 57, no room left so label right of both (end 90)
		assertEquals(List.of(new Guide(57, 44, 1, 4, "4", 92, 42)), r.guides());
	}

	@Test
	void theGapLabelStaysOffBothBoxes() {
		Result r = snap(rect(87, 60, 60, 20), rect(4, 50, 80, 40));
		assertEquals(88, r.x());
		Guide gap = r.guides().getFirst();
		assertEquals("4", gap.label());
		assertEquals(40, gap.labelY(), "above both boxes (the higher one starts at 50)");
		assertEquals(83, gap.labelX(), "centred on the gap");
	}

	@Test
	void onlyStacksBoxesThatAreAboveEachOther() {
		Result r = snap(rect(300, 50, 60, 20), rect(4, 4, 80, 40));
		assertEquals(50, r.y());
	}

	@Test
	void skipsSpotsPastTheMargin() {
		// beside 400..440 = 444..484, past margin 476
		Result r = snap(rect(446, 100, 40, 20), rect(400, 85, 40, 40));
		assertEquals(446, r.x());
		assertTrue(r.guides().isEmpty(), "no guide pointing at a spot the box can't reach");
	}

	@Test
	void theScreenWinsATie() {
		Result r = snap(rect(6, 100, 50, 20), rect(4, 200, 80, 40));
		assertEquals(4, r.x());
		assertEquals(Anchor.START, r.anchorX());
		assertNull(r.guides().getFirst().label());
		assertEquals(H, r.guides().getFirst().height(), "the screen guide, not the box guide");
	}

	@Test
	void theClosestTargetWins() {
		// left edge 3 px off margin, middle 1 px off other middle
		Result r = snap(rect(7, 100, 50, 20), rect(13, 200, 40, 20));
		assertEquals(8, r.x());
		assertNull(r.anchorX(), "middles lined up, but the other box is anchored by its left edge");
	}
}
