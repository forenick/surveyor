package dev.shaftjournal.screen;

import io.wispforest.owo.ui.component.LabelComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * An owo label that can be hovered anywhere. owo 0.13.1 passes the style under the mouse straight
 * to vanilla's hover handling, which crashes on null. That happens when the mouse is inside a label
 * but not over its text, e.g. to the right of a short line in a full-width label (crash of 0.4.0).
 */
public final class SafeLabel extends LabelComponent {
	public SafeLabel(Component text) {
		super(text);
	}

	@Override
	protected Style styleAt(int mouseX, int mouseY) {
		Style style = super.styleAt(mouseX, mouseY);
		return style == null ? Style.EMPTY : style;
	}
}
