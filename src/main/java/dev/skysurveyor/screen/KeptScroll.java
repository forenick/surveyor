package dev.skysurveyor.screen;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;

// owo has no public getter for the offset. layout clamps it to the new content
final class KeptScroll extends ScrollContainer<FlowLayout> {
	KeptScroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
		super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
	}

	double offset() {
		return scrollOffset;
	}

	void offset(double offset) {
		scrollOffset = offset;
		currentScrollPosition = offset;
	}
}
