package dev.skysurveyor.screen;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;

// points relative to this, no tooltip so hit boxes on top get the hover
final class LinePlot extends BaseUIComponent {
	private static final int DOT = 3;
	private final int[] xs;
	private final int[] ys;
	private final int[] dotArgb;
	private final Color line;

	LinePlot(int[] xs, int[] ys, int[] dotArgb, int lineArgb) {
		this.xs = xs;
		this.ys = ys;
		this.dotArgb = dotArgb;
		this.line = Color.ofArgb(lineArgb);
		horizontalSizing(Sizing.fill(100));
		verticalSizing(Sizing.fill(100));
	}

	@Override
	public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
		for (int i = 1; i < xs.length; i++) graphics.drawLine(x + xs[i - 1], y + ys[i - 1], x + xs[i], y + ys[i], 1, line);
		for (int i = 0; i < xs.length; i++) {
			int left = x + xs[i] - DOT / 2;
			int top = y + ys[i] - DOT / 2;
			graphics.fill(left, top, left + DOT, top + DOT, dotArgb[i]);
		}
	}
}
