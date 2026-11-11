package dev.shaftjournal.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.shaftjournal.hud.HudSnap.Guide;
import dev.shaftjournal.hud.HudSnap.Rect;
import dev.shaftjournal.hud.HudSnap.Result;

/** Screen 480×270 (1080p at auto GUI scale), margin 4, as approved by Nick. */
class HudSnapTest {
	private static final int W = 480;
	private static final int H = 270;
	private static final int M = 4;

	private static Result snap(Rect box, Rect... others) {
		return HudSnap.snap(box, List.of(others), W, H, M);
	}

	@Test
	void snapsToTheScreenMarginAndSavesTheEdge() {
		Result left = snap(new Rect(7, 100, 50, 20));
		assertEquals(4, left.x());
		assertEquals(0f, left.fractionX());
		assertEquals(List.of(new Guide(3, 0, 1, H, null)), left.guides(), "full-height line just left of the box");

		Result right = snap(new Rect(W - M - 50 - 3, 100, 50, 20));
		assertEquals(W - M - 50, right.x());
		assertEquals(1f, right.fractionX());

		Result bottom = snap(new Rect(100, H - M - 20 + 2, 50, 20));
		assertEquals(H - M - 20, bottom.y());
		assertEquals(1f, bottom.fractionY());
		assertEquals(List.of(new Guide(0, H - M, W, 1, null)), bottom.guides());
	}

	@Test
	void snapsToTheMiddleOfTheScreen() {
		Result r = snap(new Rect(212, 160, 50, 20)); // middle at 237, screen middle 240
		assertEquals(215, r.x());
		assertEquals(0.5f, r.fractionX());
	}

	@Test
	void leavesABoxAloneWhenNothingIsClose() {
		Result r = snap(new Rect(100, 100, 50, 20), new Rect(300, 200, 40, 20));
		assertEquals(100, r.x());
		assertEquals(100, r.y());
		assertNull(r.fractionX());
		assertNull(r.fractionY());
		assertTrue(r.guides().isEmpty());
	}

	@Test
	void linesUpWithAnotherBox() {
		Rect other = new Rect(100, 100, 80, 40);
		Result r = snap(new Rect(102, 200, 60, 20), other);
		assertEquals(100, r.x());
		assertNull(r.fractionX(), "a box-to-box snap has no fixed screen position");
		assertEquals(List.of(new Guide(99, 97, 1, 126, null)), r.guides(), "line spans both boxes plus 3 px");

		Result right = snap(new Rect(118, 200, 60, 20), other); // right edges 178 vs 180
		assertEquals(120, right.x());
	}

	@Test
	void stacksUnderAnotherBoxWithAGap() {
		Rect session = new Rect(4, 4, 80, 40);
		Result r = snap(new Rect(30, 50, 60, 20), session);
		assertEquals(48, r.y(), "4 px under the box");
		assertEquals(30, r.x());
		// The boxes overlap from x 30 to 84, so the marker sits at 57.
		assertEquals(List.of(new Guide(57, 44, 1, 4, "4")), r.guides(), "gap marker in the middle of the overlap");
	}

	@Test
	void onlyStacksBoxesThatAreAboveEachOther() {
		Result r = snap(new Rect(300, 50, 60, 20), new Rect(4, 4, 80, 40));
		assertEquals(50, r.y());
	}

	@Test
	void stacksBesideAnotherBox() {
		Result r = snap(new Rect(87, 10, 60, 20), new Rect(4, 4, 80, 40)); // other ends at 84, gap target 88
		assertEquals(88, r.x());
		assertEquals("4", r.guides().getFirst().label());
	}

	@Test
	void theScreenWinsATie() {
		Result r = snap(new Rect(6, 100, 50, 20), new Rect(4, 200, 80, 40));
		assertEquals(4, r.x());
		assertEquals(0f, r.fractionX());
		assertNull(r.guides().getFirst().label());
		assertEquals(H, r.guides().getFirst().height(), "the screen guide, not the box guide");
	}

	@Test
	void theClosestTargetWins() {
		// Left edge 3 px from the margin, middle 1 px from the other box's middle.
		Result r = snap(new Rect(7, 100, 50, 20), new Rect(13, 200, 40, 20));
		assertEquals(8, r.x());
		assertNull(r.fractionX());
	}
}
