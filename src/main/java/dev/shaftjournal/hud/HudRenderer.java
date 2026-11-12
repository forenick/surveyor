package dev.shaftjournal.hud;

import java.util.List;

import dev.shaftjournal.hud.HudLayout.Box;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;

// draws one HUD box. the in-game HUD and the editor both use it so they look the same
public final class HudRenderer {
	// space between the text and the box edge, before scaling
	static final int PADDING = 3;
	private static final int LINE_GAP = 1;
	private static final int DARK = 0x80000000;

	// where a box ends up on screen, after scaling
	public record Bounds(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}

		// how many pixels this and the other area share, 0 if they dont overlap
		public long overlapArea(Bounds o) {
			long w = Math.min(x + width, o.x + o.width) - Math.max(x, o.x);
			long h = Math.min(y + height, o.y + o.height) - Math.max(y, o.y);
			return w <= 0 || h <= 0 ? 0 : w * h;
		}

		// this area grown by "by" pixels on every side
		public Bounds grown(int by) {
			return new Bounds(x - by, y - by, width + 2 * by, height + 2 * by);
		}
	}

	private HudRenderer() {
	}

	public static Bounds bounds(Font font, Box box, List<String> lines, int screenWidth, int screenHeight) {
		int width = Math.round(innerWidth(font, lines) * box.scale);
		int height = Math.round(innerHeight(font, lines) * box.scale);
		return new Bounds(box.anchorX.start(box.offsetX, width, screenWidth),
				box.anchorY.start(box.offsetY, height, screenHeight), width, height);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Box box, List<String> lines, Bounds bounds) {
		// each line gets measured once per frame
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
			// the tooltip sprite goes 3 pixels past the area it gets, thats our padding
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
