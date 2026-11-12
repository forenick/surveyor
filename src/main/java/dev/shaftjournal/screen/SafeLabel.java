package dev.shaftjournal.screen;

import io.wispforest.owo.ui.component.LabelComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

// owo label u can hover anywhere. owo 0.13.1 passes the style under the mouse straight to vanillas
// hover handling, which crashes on null. that happens when the mouse is inside a label but not over
// its text, f.e. right of a short line in a full width label (the 0.4.0 crash)
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
