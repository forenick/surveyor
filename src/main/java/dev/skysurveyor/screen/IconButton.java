package dev.skysurveyor.screen;

import java.util.function.Consumer;

import io.wispforest.owo.mixin.ui.access.AbstractWidgetAccessor;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// owo only centres text, so icon + text centred here. icon has its shadow baked in
final class IconButton extends PackButton {
	private static final int GAP = 3;
	private Identifier icon;
	private final int iconWidth;
	private final int iconHeight;

	IconButton(Component message, Identifier icon, int iconWidth, int iconHeight, Consumer<ButtonComponent> onPress) {
		super(message, onPress);
		this.icon = icon;
		this.iconWidth = iconWidth;
		this.iconHeight = iconHeight;
		horizontalSizing(Sizing.fixed(Minecraft.getInstance().font.width(message) + iconWidth + gap() + 12));
	}

	void icon(Identifier value) {
		icon = value;
	}

	// icon only = no gap
	private int gap() {
		return getMessage().getString().isEmpty() ? 0 : GAP;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		renderer.draw((OwoUIGraphics) graphics, this, delta);
		Font font = Minecraft.getInstance().font;
		// same colours as owo's own label
		int color = active ? 0xFFFFFFFF : 0xFFA0A0A0;
		int x = getX() + (width - iconWidth - gap() - font.width(getMessage())) / 2;
		int y = getY() + (height - 8) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
		graphics.text(font, getMessage(), x + iconWidth + GAP, y, color, textShadow);
		// same as owos own button, tooltip() lives there. cast via Object, mixin accessor
		var tooltip = ((AbstractWidgetAccessor) (Object) this).owo$getTooltip();
		if (isHovered && tooltip.get() != null) {
			graphics.setTooltipForNextFrame(font, tooltip.get().toCharSequence(Minecraft.getInstance()), DefaultTooltipPositioner.INSTANCE,
					mouseX, mouseY, false);
		}
	}
}
