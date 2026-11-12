package dev.shaftjournal.screen;

import io.wispforest.owo.ui.component.LabelComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

// owo 0.13.1 passes null hover style to vanilla = crash off text (0.4.0)
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
