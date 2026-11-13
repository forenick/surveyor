package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.money.CoinBreakdown;

// "" = small gap, not a full blank line
final class TooltipLines {
	private static final Identifier SHIFT_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/shift_icon.png");
	private static final int ICON = 8;
	private static final int ICON_GAP = 3;

	private TooltipLines() {
	}

	static List<ClientTooltipComponent> of(List<String> lines) {
		List<ClientTooltipComponent> components = new ArrayList<>();
		for (String line : lines) {
			if (line.isEmpty()) components.add(new Gap());
			else if (line.equals(CoinBreakdown.SHIFT_HINT)) components.add(new IconLine(SHIFT_ICON, line));
			else components.add(ClientTooltipComponent.create(Component.literal(line).getVisualOrderText()));
		}
		return components;
	}

	private record Gap() implements ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			return CoinBreakdown.GAP_HEIGHT;
		}

		@Override
		public int getWidth(Font font) {
			return 0;
		}
	}

	// icon has its shadow baked in
	private record IconLine(Identifier icon, String text) implements ClientTooltipComponent {
		@Override
		public int getHeight(Font font) {
			return CoinBreakdown.LINE_HEIGHT;
		}

		@Override
		public int getWidth(Font font) {
			return ICON + ICON_GAP + font.width(text);
		}

		@Override
		public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
			graphics.text(font, Component.literal(text), x + ICON + ICON_GAP, y, 0xFFFFFFFF, true);
		}

		@Override
		public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0, 0, ICON, ICON, ICON, ICON);
		}
	}
}
