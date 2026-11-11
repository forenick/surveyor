package dev.shaftjournal.hud;

import java.util.List;

import dev.shaftjournal.hud.HudLayout.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;

/** Draws one HUD box. Used by the in-game HUD and the editor, so both look the same. */
public final class HudRenderer {
	/** Space between the text and the box edge, before scaling. */
	static final int PADDING = 3;
	/** Space between a box and the screen edge; also the gap between snapped boxes. Nick's choice (2026-11-11). */
	static final int MARGIN = 4;
	private static final int LINE_GAP = 1;
	private static final int DARK = 0x80000000;

	/** Where a box ends up on screen, after scaling. */
	public record Bounds(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}
	}

	private HudRenderer() {
	}

	public static Bounds bounds(Font font, Box box, List<String> lines, int screenWidth, int screenHeight) {
		int width = Math.round(innerWidth(font, lines) * box.scale);
		int height = Math.round(innerHeight(font, lines) * box.scale);
		int freeX = Math.max(0, screenWidth - 2 * MARGIN - width);
		int freeY = Math.max(0, screenHeight - 2 * MARGIN - height);
		return new Bounds(MARGIN + Math.round(box.x * freeX), MARGIN + Math.round(box.y * freeY), width, height);
	}

	/** The position fraction that puts the box's top-left corner at this point. */
	public static float fractionX(int x, int width, int screenWidth) {
		int free = screenWidth - 2 * MARGIN - width;
		return free <= 0 ? 0 : Math.max(0, Math.min(1, (x - MARGIN) / (float) free));
	}

	public static float fractionY(int y, int height, int screenHeight) {
		return fractionX(y, height, screenHeight);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Box box, List<String> lines, Bounds bounds) {
		// Each line is measured once per frame.
		int[] lineWidths = new int[lines.size()];
		int widest = 0;
		for (int i = 0; i < lineWidths.length; i++) {
			lineWidths[i] = font.width(lines.get(i));
			widest = Math.max(widest, lineWidths[i]);
		}
		int width = widest + 2 * PADDING;
		int height = innerHeight(font, lines);
		graphics.pose().pushMatrix();
		graphics.pose().translate(bounds.x(), bounds.y());
		graphics.pose().scale(box.scale, box.scale);
		switch (box.background) {
			case DARK -> graphics.fill(0, 0, width, height, DARK);
			// The tooltip sprite reaches 3 pixels past the area it's given, which is our padding.
			case FRAME -> TooltipRenderUtil.extractTooltipBackground(graphics, PADDING, PADDING,
					width - 2 * PADDING, height - 2 * PADDING, null);
			case NONE -> {
			}
		}
		int textWidth = width - 2 * PADDING;
		int y = PADDING;
		for (int i = 0; i < lineWidths.length; i++) {
			String line = lines.get(i);
			int lineWidth = lineWidths[i];
			int x = PADDING + switch (box.align) {
				case LEFT -> 0;
				case CENTER -> (textWidth - lineWidth) / 2;
				case RIGHT -> textWidth - lineWidth;
			};
			graphics.text(font, line, x, y, 0xFFFFFFFF, true);
			y += font.lineHeight + LINE_GAP;
		}
		graphics.pose().popMatrix();
	}

	private static int innerWidth(Font font, List<String> lines) {
		int width = 0;
		for (String line : lines) width = Math.max(width, font.width(line));
		return width + 2 * PADDING;
	}

	private static int innerHeight(Font font, List<String> lines) {
		return Math.max(0, lines.size() * (font.lineHeight + LINE_GAP) - LINE_GAP) + 2 * PADDING;
	}
}
