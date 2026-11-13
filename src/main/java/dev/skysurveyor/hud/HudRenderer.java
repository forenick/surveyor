package dev.skysurveyor.hud;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.hud.HudLayout.Box;

public final class HudRenderer {
	static final int PADDING = 3;
	private static final int LINE_GAP = 1;
	private static final int DARK = 0x80000000;
	private static final Identifier FRAME_BACKGROUND = Identifier.withDefaultNamespace("tooltip/background");
	private static final Identifier FRAME_BORDER = Identifier.withDefaultNamespace("tooltip/frame");
	// vanilla tooltip sprites overhang the text by 12px
	private static final int FRAME_OVERHANG = 12;

	public record Bounds(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}

		public long overlapArea(Bounds o) {
			long w = Math.min(x + width, o.x + o.width) - Math.max(x, o.x);
			long h = Math.min(y + height, o.y + o.height) - Math.max(y, o.y);
			return w <= 0 || h <= 0 ? 0 : w * h;
		}

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
			case FRAME -> drawFrame(graphics, width, height);
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
			drawLine(graphics, font, line, x, y);
			y += font.lineHeight + LINE_GAP;
		}
		graphics.pose().popMatrix();
	}

	// not via TooltipRenderUtil, iconographic recolours that w/ the last hovered item
	private static void drawFrame(GuiGraphicsExtractor graphics, int width, int height) {
		int x = PADDING - FRAME_OVERHANG;
		int w = width - 2 * PADDING + 2 * FRAME_OVERHANG;
		int h = height - 2 * PADDING + 2 * FRAME_OVERHANG;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_BACKGROUND, x, x, w, h);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FRAME_BORDER, x, x, w, h);
	}

	private static void drawLine(GuiGraphicsExtractor graphics, Font font, String line, int x, int y) {
		if (!line.contains(HudBoxText.HIDE_START)) {
			graphics.text(font, line, x, y, 0xFFFFFFFF, true);
			return;
		}
		List<String> pieces = HudBoxText.pieces(line);
		for (int i = 0; i < pieces.size(); i++) {
			if (i % 2 == 0) graphics.text(font, pieces.get(i), x, y, 0xFFFFFFFF, true);
			x += font.width(pieces.get(i));
		}
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
