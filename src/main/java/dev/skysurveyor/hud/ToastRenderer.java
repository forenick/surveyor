package dev.skysurveyor.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.settings.SummarySettings.Style;

// shaft toast for all 3 styles, layouts from Docs/mockups/toast-v1.html
public final class ToastRenderer {
	// vanilla advancement toast, 160x32, no nine slice meta so we slice it ourselves
	private static final Identifier VANILLA = Identifier.withDefaultNamespace("toast/advancement");
	private static final int SPRITE_WIDTH = 160;
	private static final int SPRITE_HEIGHT = 32;
	private static final int SPRITE_BORDER = 4;
	// systemtoast: text at x 18, title y 7, lines 12 apart from y 18. no icon here, so 10
	private static final int VANILLA_X = 10;
	private static final int VANILLA_MIN_WIDTH = 160;
	private static final int LABEL_GAP = 5;
	// hud styles = HudRenderer padding + line height
	private static final int PADDING = HudRenderer.PADDING;
	private static final int HUD_LINE = 10;
	private static final int DARK = 0x80000000;

	public record Size(int width, int height, int labelWidth) {
	}

	private ToastRenderer() {
	}

	public static Size size(Font font, ToastText text, Style style) {
		int labelWidth = labelWidth(font, text);
		int textWidth = Math.max(font.width(title(text, style)), labelWidth + LABEL_GAP + valueWidth(font, text));
		int rows = text.rows().size();
		if (style == Style.VANILLA) return new Size(Math.max(VANILLA_MIN_WIDTH, 2 * VANILLA_X + textWidth), 20 + rows * 12, labelWidth);
		return new Size(textWidth + 2 * PADDING, 2 * PADDING + HUD_LINE + rows * HUD_LINE - 1, labelWidth);
	}

	public static HudRenderer.Bounds bounds(Font font, ToastText text, Style style, Box box, int screenWidth, int screenHeight) {
		return bounds(size(font, text, style), box, screenWidth, screenHeight);
	}

	public static HudRenderer.Bounds bounds(Size size, Box box, int screenWidth, int screenHeight) {
		int width = Math.round(size.width() * box.scale);
		int height = Math.round(size.height() * box.scale);
		return new HudRenderer.Bounds(box.anchorX.start(box.offsetX, width, screenWidth),
				box.anchorY.start(box.offsetY, height, screenHeight), width, height);
	}

	// movable spot, hud + editor
	public static void draw(GuiGraphicsExtractor graphics, Font font, ToastText text, Style style, Size size, Box box,
			HudRenderer.Bounds bounds, float alpha) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(bounds.x(), bounds.y());
		graphics.pose().scale(box.scale, box.scale);
		draw(graphics, font, text, style, size, alpha);
		graphics.pose().popMatrix();
	}

	// at 0,0 of the current pose, unscaled. alpha 0..1 for the hud fade
	public static void draw(GuiGraphicsExtractor graphics, Font font, ToastText text, Style style, Size size, float alpha) {
		int white = ARGB.white(alpha);
		switch (style) {
			case VANILLA -> sliced(graphics, size.width(), size.height(), white);
			case FRAME -> HudRenderer.drawFrame(graphics, size.width(), size.height(), white);
			case DARK -> graphics.fill(0, 0, size.width(), size.height(), ARGB.multiplyAlpha(DARK, alpha));
		}
		boolean vanilla = style == Style.VANILLA;
		int x = vanilla ? VANILLA_X : PADDING;
		int y = vanilla ? 7 : PADDING;
		// vanilla toasts draw text w/o shadow
		graphics.text(font, title(text, style), x, y, white, !vanilla);
		int valueX = x + size.labelWidth() + LABEL_GAP;
		int rowY = vanilla ? 18 : PADDING + HUD_LINE;
		for (ToastText.Row row : text.rows()) {
			graphics.text(font, "§7" + row.label(), x, rowY, white, !vanilla);
			graphics.text(font, row.value(), valueX, rowY, white, !vanilla);
			rowY += vanilla ? 12 : HUD_LINE;
		}
	}

	private static String title(ToastText text, Style style) {
		// hud box titles are bold
		return style == Style.VANILLA ? text.title() : text.title().replaceFirst("^§e", "§e§l").replaceFirst("  ", "§r  ");
	}

	private static int labelWidth(Font font, ToastText text) {
		int width = 0;
		for (ToastText.Row row : text.rows()) width = Math.max(width, font.width("§7" + row.label()));
		return width;
	}

	private static int valueWidth(Font font, ToastText text) {
		int width = 0;
		for (ToastText.Row row : text.rows()) width = Math.max(width, font.width(row.value()));
		return width;
	}

	// corners as is, edges + middle tiled from the sprite
	private static void sliced(GuiGraphicsExtractor graphics, int width, int height, int color) {
		int b = SPRITE_BORDER;
		int innerW = SPRITE_WIDTH - 2 * b;
		int innerH = SPRITE_HEIGHT - 2 * b;
		part(graphics, 0, 0, b, b, 0, 0, b, b, color);
		part(graphics, SPRITE_WIDTH - b, 0, b, b, width - b, 0, b, b, color);
		part(graphics, 0, SPRITE_HEIGHT - b, b, b, 0, height - b, b, b, color);
		part(graphics, SPRITE_WIDTH - b, SPRITE_HEIGHT - b, b, b, width - b, height - b, b, b, color);
		part(graphics, b, 0, innerW, b, b, 0, width - 2 * b, b, color);
		part(graphics, b, SPRITE_HEIGHT - b, innerW, b, b, height - b, width - 2 * b, b, color);
		part(graphics, 0, b, b, innerH, 0, b, b, height - 2 * b, color);
		part(graphics, SPRITE_WIDTH - b, b, b, innerH, width - b, b, b, height - 2 * b, color);
		part(graphics, b, b, innerW, innerH, b, b, width - 2 * b, height - 2 * b, color);
	}

	// sprite region (u, v, uw, vh) tiled over (x, y, w, h)
	private static void part(GuiGraphicsExtractor graphics, int u, int v, int uw, int vh, int x, int y, int w, int h, int color) {
		for (int dy = 0; dy < h; dy += vh) {
			for (int dx = 0; dx < w; dx += uw) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA, SPRITE_WIDTH, SPRITE_HEIGHT, u, v, x + dx, y + dy,
						Math.min(uw, w - dx), Math.min(vh, h - dy), color);
			}
		}
	}
}
