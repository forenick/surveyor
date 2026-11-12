package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.TextAreaComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import dev.shaftjournal.hud.HudLayout.Align;
import dev.shaftjournal.hud.HudLayout.Anchor;
import dev.shaftjournal.hud.HudLayout.Background;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.screen.SafeLabel;

public final class EditHudScreen extends BaseOwoScreen<FlowLayout> {
	private static final int PANEL_FILL = 0xE8141414;
	private static final int PANEL_OUTLINE = 0x30FFFFFF;
	private static final Surface PANEL = Surface.flat(PANEL_FILL).and(Surface.outline(PANEL_OUTLINE));
	private static final Surface MENU = Surface.flat(0xF0101010).and(Surface.outline(0xFFA0A0A0));
	// no outline, so it doesnt look like a field
	private static final Surface PREVIEW = Surface.flat(0x8C000000)
			.and((graphics, c) -> graphics.fill(c.x(), c.y(), c.x() + 2, c.y() + c.height(), 0x40FFFFFF));
	private static final Surface DIVIDER = (graphics, c) -> graphics.fill(c.x(), c.y(), c.x() + c.width(), c.y() + 1,
			0x1CFFFFFF);
	private static final int PANEL_WIDTH = 204;
	// panel minus its padding
	private static final int INNER = PANEL_WIDTH - 10;
	private static final int ROW_LABEL = 62;
	private static final int KEY = 38;
	// action buttons / choices + line rows. fits 480x270
	private static final int BUTTON = 16;
	private static final int CHOICE = 14;
	private static final int MENU_SWATCH = 10;
	private static final int TIP_WIDTH = 240;
	// tooltip frame sticks out ~3px
	private static final int TIP_MARGIN = 5;
	private static final Identifier REMOVE_ICON = Identifier.withDefaultNamespace("player_list/remove_player");
	private static final int OUTLINE = 0x70FFFFFF;
	private static final int OUTLINE_SELECTED = 0xFFFFFF55;
	// light, so the preview stays readable
	private static final int HIDDEN_SHADE = 0x60000000;
	private static final int GUIDE = 0xFFFF55FF;
	private static final int DROP_LINE = 0xFFFFFF55;
	private static final int DRAGGED_LINE = 0x30FFFFFF;
	private static final int HOVER = 0x20FFFFFF;
	private static final Surface GRIP = (graphics, c) -> {
		int x = c.x() + (c.width() - 3) / 2;
		int y = c.y() + (c.height() - 5) / 2;
		for (int dx = 0; dx <= 2; dx += 2) {
			for (int dy = 0; dy <= 4; dy += 2) graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, 0xFFA0A0A0);
		}
	};
	// batches scroll/nudge spam into 1 undo step
	private static final long SETTLE_MILLIS = 400;
	// 8 wide pixel icons, same in every font
	private static final String[] RESET_ICON = {
			"..XXXX.X",
			".X....XX",
			"X....XXX",
			"X.......",
			"X.......",
			"X......X",
			".X....X.",
			"..XXXX.."};
	private static final String[] PENCIL_ICON = {
			"......X.",
			".....XXX",
			"....XXX.",
			"...XXX..",
			"..XXX...",
			".XXX....",
			".XX.....",
			"X......."};
	private static final String[] UNDO_ICON = {
			"...X....",
			"..XX....",
			".XXXXXX.",
			"..XX...X",
			"...X...X",
			".......X",
			"....XXX."};
	// also the size slider step
	private static final float SCALE_STEP = 0.1f;
	private static final int PADDING = 4;
	private static final int[][] TOOLBAR_SPOTS = {{50, 0}, {50, 100}, {0, 0}, {100, 0}, {0, 100}, {100, 100}};
	private static final Anchor[][] NEW_BOX_SPOTS = {{Anchor.START, Anchor.MIDDLE}, {Anchor.END, Anchor.MIDDLE},
			{Anchor.START, Anchor.END}, {Anchor.END, Anchor.END}, {Anchor.MIDDLE, Anchor.END}, {Anchor.MIDDLE, Anchor.MIDDLE}};

	private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	private static final String[] COLOR_NAMES = {"Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red",
			"Dark Purple", "Gold", "Gray", "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White"};
	private static final String HIDDEN_NOTE = "hidden";
	private static final String TEXT_HINT = "§8The buttons type values and colours for you. &7 is a colour, {spawn_timer} a value.";
	// right of the mouse if it fits, else left, always on screen
	private static final ClientTooltipPositioner FIT = (screenW, screenH, x, y, w, h) -> new Vector2i(
			clamp(x + 12 + w <= screenW - TIP_MARGIN ? x + 12 : x - 12 - w, w, screenW),
			clamp(y - 12, h, screenH));

	private final Screen parent;
	private final LiveHud hud;
	private final Map<Box, HudRenderer.Bounds> drawn = new IdentityHashMap<>();
	private FlowLayout root;
	private FlowLayout toolbar;
	private LabelComponent toolbarTitle;
	private FlowLayout panel;
	private PanelScroll panelScroll;
	// last frame, so a rebuild doesnt flicker
	private int panelHeight = 200;
	// box id + page, so each page keeps its scroll
	private final Map<String, Double> scrollMemory = new HashMap<>();
	private String panelKey;
	private int scrollToRow = -1;
	private DiscreteSliderComponent scaleSlider;
	private ButtonComponent resetButton;
	private ButtonComponent undoButton;
	private ButtonComponent redoButton;
	private final List<Field> fields = new ArrayList<>();
	private final List<TextBoxComponent> homeFields = new ArrayList<>();
	private Box panelBox;
	private final List<FlowLayout> lineRows = new ArrayList<>();
	private final List<List<UIComponent>> lineHandles = new ArrayList<>();
	private int lineDrag = -1;
	private int lineGap;
	private boolean mouseHeld;
	private HudHistory history;
	private String unsettled;
	private long unsettledSince;
	// tooltips drawn by us, owo's dont wrap or stay on screen
	private final Map<UIComponent, List<FormattedCharSequence>> toolbarTips = new IdentityHashMap<>();
	private final Map<UIComponent, List<FormattedCharSequence>> panelTips = new IdentityHashMap<>();
	private final Map<UIComponent, List<FormattedCharSequence>> menuTips = new IdentityHashMap<>();
	private Map<UIComponent, List<FormattedCharSequence>> tipTarget = panelTips;
	private List<FormattedCharSequence> helpLines;
	private List<FormattedCharSequence> pinnedLines;
	private HudValue helpValue;
	private boolean helpPinned;
	private ButtonComponent helpButton;
	private boolean resetArmed;
	private boolean deleteArmed;
	private ButtonComponent deleteButton;
	private FlowLayout deleteRow;
	// page state, reset when another box is picked
	private String pageBox;
	private boolean picking;
	// by index bc undo makes new line objects
	private int editedIndex = -1;
	private LabelComponent preview;
	private Line previewLine;
	// unclipped, so clip only runs on change
	private String previewRaw;
	private FreeTextArea freeArea;
	private LabelComponent freeHint;
	private String freeHintText;
	private String hintSource;
	private boolean hintNoRoom;
	private String noRoomFor;
	private FlowLayout menu;
	private UIComponent menuAnchor;
	// menu picks happen on press, rest of that press isnt a text drag
	private boolean swallowDrag;
	// 2nd press of a double click would hit the new page
	private boolean rebuiltByClick;
	private int toolbarSpot = -1;
	private Box selected;
	private Box dragging;
	private double grabX;
	private double grabY;
	private List<HudSnap.Guide> guides = List.of();
	private double scrollLeft;
	private HudLayout shownLayout;

	public EditHudScreen(Screen parent, LiveHud hud) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
		this.hud = hud;
		this.history = new HudHistory(hud.layout().toJson());
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void build(FlowLayout root) {
		this.root = root;
		root.padding(Insets.of(PADDING));
		root.horizontalAlignment(HorizontalAlignment.CENTER);
		toolbar = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		moveToolbar(0);
		toolbar.surface(PANEL);
		toolbar.padding(Insets.of(3));
		toolbar.gap(4);
		toolbar.verticalAlignment(VerticalAlignment.CENTER);
		toolbarTitle = label("§fEdit §bHUD");
		toolbarTitle.margins(Insets.of(0, 0, 2, 6));
		toolbar.child(toolbarTitle);
		undoButton = iconButton(false, b -> undo(false));
		tip(toolbarTips, undoButton, "Undo (Ctrl+Z)");
		toolbar.child(undoButton);
		redoButton = iconButton(true, b -> undo(true));
		tip(toolbarTips, redoButton, "Redo (Ctrl+Shift+Z)");
		toolbar.child(redoButton);
		ButtonComponent newBox = button("+ New box", b -> newBox());
		tip(toolbarTips, newBox, "Add an empty box and pick its first line");
		toolbar.child(newBox);
		ButtonComponent done = button("Done", b -> onClose());
		tip(toolbarTips, done, "Changes save by themselves");
		toolbar.child(done);
		armReset(false);
		root.child(toolbar);
		rebuildPanel();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (hud.layout() != shownLayout) {
			// late file load only, undo/Reset set it themselves
			if (shownLayout != null) {
				selected = null;
				dragging = null;
				lineDrag = -1;
				guides = List.of();
				history = new HudHistory(hud.layout().toJson());
				unsettled = null;
				rebuildPanel();
			}
			shownLayout = hud.layout();
		}
		for (TextBoxComponent field : homeFields) {
			field.moveCursorToEnd(false);
			field.moveCursorToStart(false);
		}
		homeFields.clear();
		syncFields();
		refreshLinePage();
		recordChanges(false);
		undoButton.active(history.canUndo());
		redoButton.active(history.canRedo());
		drawn.clear();
		for (Box box : hud.layout().boxes) {
			List<String> lines = lines(box);
			HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines, width, height);
			drawn.put(box, bounds);
			HudRenderer.draw(graphics, font, box, lines, bounds);
			if (!hud.visible(box)) drawHidden(graphics, bounds);
			graphics.outline(bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2,
					box == selected ? OUTLINE_SELECTED : OUTLINE);
		}
		for (HudSnap.Guide guide : guides) {
			graphics.fill(guide.x(), guide.y(), guide.x() + guide.width(), guide.y() + guide.height(), GUIDE);
			if (guide.label() != null) graphics.text(font, guide.label(), guide.labelX(), guide.labelY(), GUIDE, true);
		}
		if (selected == null) {
			// short, fits 480 wide (1080p auto scale)
			String[] hint = {"§7Drag a box to move it · hold Ctrl to place it freely · click it to edit",
					"§7Scroll on the selected box to resize it · arrow keys nudge it",
					"§7Changes save by themselves · Ctrl+Z undo · Ctrl+Shift+Z redo"};
			for (int i = 0; i < hint.length; i++) {
				graphics.text(font, hint[i], (width - font.width(hint[i])) / 2, height - 64 + i * 11, 0xFFFFFFFF, true);
			}
		}
		placeToolbar();
		// panel draws over boxes from here
		graphics.nextStratum();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		fitPanel();
		fitMenu();
		if (lineDrag >= 0) {
			scrollWhileDragging(mouseY);
			graphics.nextStratum();
			drawLineDrag(graphics);
		}
		drawTip(graphics, mouseX, mouseY);
	}

	private static int clamp(int pos, int size, int screen) {
		return Math.max(TIP_MARGIN, Math.min(pos, screen - TIP_MARGIN - size));
	}

	// replaces owo's tooltip, smallest hovered component wins
	private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (dragging != null || lineDrag >= 0) return;
		Map<UIComponent, List<FormattedCharSequence>> tips = menu != null && menu.isInBoundingBox(mouseX, mouseY) ? menuTips
				: inPanel(mouseX, mouseY) ? panelTips
				: toolbar.isInBoundingBox(mouseX, mouseY) ? toolbarTips : Map.of();
		List<FormattedCharSequence> lines = null;
		long smallest = Long.MAX_VALUE;
		for (Map.Entry<UIComponent, List<FormattedCharSequence>> tip : tips.entrySet()) {
			UIComponent c = tip.getKey();
			long area = (long) c.width() * c.height();
			if (area < smallest && c.isInBoundingBox(mouseX, mouseY)) {
				smallest = area;
				lines = tip.getValue();
			}
		}
		if (lines != null) {
			graphics.setTooltipForNextFrame(font, lines, FIT, mouseX, mouseY, true);
		} else if (helpPinned && helpValue != null && panelScroll != null) {
			boolean panelRight = panelScroll.x() > width / 2;
			int px = panelRight ? panelScroll.x() : panelScroll.x() + panelScroll.width();
			if (pinnedLines == null) {
				// beside the panel, not over the text
				pinnedLines = helpLines(helpValue, (panelRight ? px : width - px) - 8 - 2 * TIP_MARGIN - 6);
				if (pinnedLines.size() * 10 + 8 > height - 2 * TIP_MARGIN) pinnedLines = helpLines;
			}
			// clear of the toolbar so Done stays visible
			boolean toolbarTop = toolbar.y() < height / 2;
			int top = toolbarTop ? toolbar.y() + toolbar.height() + 2 : 0;
			int bottom = toolbarTop ? height : toolbar.y() - 2;
			graphics.setTooltipForNextFrame(font, pinnedLines, (screenW, screenH, x, y, w, h) -> new Vector2i(
					clamp(panelRight ? px - 8 - w : px + 8, w, screenW),
					clamp(Math.max(top + TIP_MARGIN, bottom - TIP_MARGIN - h), h, screenH)), mouseX, mouseY, true);
		}
	}

	private void tip(UIComponent c, String text) {
		tip(tipTarget, c, text);
	}

	private void tip(Map<UIComponent, List<FormattedCharSequence>> target, UIComponent c, String text) {
		target.put(c, font.split(Component.literal(text), TIP_WIDTH));
	}

	// panel doesnt scroll by itself during a line drag
	private void scrollWhileDragging(int mouseY) {
		if (panelScroll == null || lineRows.isEmpty()) return;
		int top = panelScroll.y();
		int bottom = top + panelScroll.height();
		FlowLayout last = lineRows.getLast();
		// only as far as the lines go
		if (mouseY < top + 12 && lineRows.getFirst().y() < top + 4) {
			panelScroll.offset(Math.max(0, panelScroll.offset() - 3));
		} else if (mouseY > bottom - 12 && last.y() + last.height() > bottom - 4) {
			panelScroll.offset(panelScroll.offset() + 3);
		}
		updateLineGap(mouseY);
	}

	private void updateLineGap(double mouseY) {
		lineGap = 0;
		for (FlowLayout row : lineRows) {
			if (mouseY > row.y() + row.height() / 2.0) lineGap++;
		}
	}

	// badge, not a dark cover, so edits stay readable
	private void drawHidden(GuiGraphicsExtractor graphics, HudRenderer.Bounds bounds) {
		graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), HIDDEN_SHADE);
		String badge = "hidden here";
		int w = font.width(badge) + 4;
		int x = Math.max(0, Math.min(width - w, bounds.x() + bounds.width() - w));
		int y = bounds.y() >= font.lineHeight + 3 ? bounds.y() - font.lineHeight - 3 : bounds.y() + bounds.height() + 2;
		graphics.fill(x, y, x + w, y + font.lineHeight + 1, 0xC0000000);
		graphics.text(font, "§7" + badge, x + 2, y + 1, 0xFFFFFFFF, false);
	}

	private void drawLineDrag(GuiGraphicsExtractor graphics) {
		if (lineDrag >= lineRows.size()) return;
		FlowLayout from = lineRows.get(lineDrag);
		graphics.fill(from.x(), from.y(), from.x() + from.width(), from.y() + from.height(), DRAGGED_LINE);
		if (lineGap == lineDrag || lineGap == lineDrag + 1) return;
		FlowLayout last = lineRows.getLast();
		// rows 1px apart, bar covers it +1 each side
		int y = lineGap < lineRows.size() ? lineRows.get(lineGap).y() - 1 : last.y() + last.height();
		graphics.fill(from.x(), y - 1, from.x() + from.width(), y + 1, DROP_LINE);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		// prior typing/scrolling = own undo step
		syncFields();
		recordChanges(true);
		mouseHeld = true;
		swallowDrag = false;
		boolean left = click.button() == 0;
		boolean rebuilt = rebuiltByClick;
		rebuiltByClick = false;
		if (doubled && rebuilt && overPanel(click.x(), click.y())) return true;
		if (menu != null) {
			// rows + checkboxes act on any button, panel is left click only
			if (menu.isInBoundingBox(click.x(), click.y())) return !left || super.mouseClicked(click, doubled);
			// 1st click outside only closes it
			closeMenu();
			return true;
		}
		if (resetArmed && !resetButton.isInBoundingBox(click.x(), click.y())) armReset(false);
		if (deleteArmed && !deleteButton.isInBoundingBox(click.x(), click.y())) armDelete(false);
		int handle = left ? lineHandleAt(click.x(), click.y()) : -1;
		if (handle >= 0) {
			root.focusHandler().focus(null, null);
			lineDrag = handle;
			lineGap = handle;
			return true;
		}
		if (overPanel(click.x(), click.y())) return !left || super.mouseClicked(click, doubled);
		if (!left) return false;
		root.focusHandler().focus(null, null);
		Box hit = null;
		// last drawn = on top
		for (Box box : hud.layout().boxes) {
			HudRenderer.Bounds bounds = drawn.get(box);
			if (bounds != null && bounds.contains(click.x(), click.y())) hit = box;
		}
		if (hit != selected) scrollLeft = 0;
		selected = hit;
		if (hit != null) {
			HudRenderer.Bounds bounds = drawn.get(hit);
			dragging = hit;
			grabX = click.x() - bounds.x();
			grabY = click.y() - bounds.y();
			removePanel();
		} else {
			rebuildPanel();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent drag, double dx, double dy) {
		if (swallowDrag) return true;
		if (lineDrag >= 0) {
			updateLineGap(drag.y());
			return true;
		}
		if (dragging == null) return super.mouseDragged(drag, dx, dy);
		HudRenderer.Bounds bounds = drawn.get(dragging);
		if (bounds == null) return true;
		int x = (int) Math.round(drag.x() - grabX);
		int y = (int) Math.round(drag.y() - grabY);
		Anchor anchorX = null;
		Anchor anchorY = null;
		guides = List.of();
		if (!minecraft.hasControlDown()) {
			List<HudSnap.Rect> others = new ArrayList<>();
			drawn.forEach((box, b) -> {
				if (box != dragging) others.add(new HudSnap.Rect(b.x(), b.y(), b.width(), b.height(), box.anchorX, box.anchorY));
			});
			HudSnap.Result snap = HudSnap.snap(new HudSnap.Rect(x, y, bounds.width(), bounds.height(), dragging.anchorX,
					dragging.anchorY), others, width, height, HudLayout.MARGIN);
			x = (int) Math.round(snap.x());
			y = (int) Math.round(snap.y());
			anchorX = snap.anchorX();
			anchorY = snap.anchorY();
			guides = snap.guides();
		}
		x = Anchor.clamp(x, bounds.width(), width);
		y = Anchor.clamp(y, bounds.height(), height);
		dragging.anchorX = anchorX != null ? anchorX : Anchor.forPosition(x, bounds.width(), width);
		dragging.anchorY = anchorY != null ? anchorY : Anchor.forPosition(y, bounds.height(), height);
		dragging.offsetX = dragging.anchorX.offset(x, bounds.width(), width);
		dragging.offsetY = dragging.anchorY.offset(y, bounds.height(), height);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		// panel would scroll away from it
		if (menu != null && !menu.isInBoundingBox(mouseX, mouseY)) closeMenu();
		HudRenderer.Bounds bounds = selected == null ? null : drawn.get(selected);
		if (vertical == 0 || bounds == null || overPanel(mouseX, mouseY) || !bounds.contains(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		// touchpads send tiny amounts, add up to a notch
		if (Math.signum(vertical) != Math.signum(scrollLeft)) scrollLeft = 0;
		scrollLeft += vertical;
		// nudge so ten 0.1s (0.9999999) reach a notch
		int steps = (int) (scrollLeft + Math.copySign(1e-6, scrollLeft));
		if (steps == 0) return true;
		scrollLeft -= steps;
		if (Math.abs(scrollLeft) < 1e-6) scrollLeft = 0;
		float scale = Math.round((selected.scale + steps * SCALE_STEP) * 10) / 10f;
		selected.scale = Math.max(HudLayout.MIN_SCALE, Math.min(HudLayout.MAX_SCALE, scale));
		if (scaleSlider != null) scaleSlider.setFromDiscreteValue(selected.scale);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent key) {
		// esc goes back one step before it closes the editor
		if (key.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (menu != null) {
				closeMenu();
				return true;
			}
			if (helpPinned) {
				pinHelp(false);
				return true;
			}
			if (picking || editedIndex >= 0) {
				backToLines();
				return true;
			}
		}
		// also while typing, title field has no own undo
		if (key.key() == GLFW.GLFW_KEY_Z && key.hasControlDownWithQuirk()) {
			undo(key.hasShiftDown());
			return true;
		}
		if (selected != null && !typing()) {
			int step = key.hasShiftDown() ? 10 : 1;
			int dx = key.isLeft() ? -step : key.isRight() ? step : 0;
			int dy = key.isUp() ? -step : key.isDown() ? step : 0;
			if (dx != 0 || dy != 0) {
				// measured now, key repeat beats frame rate
				HudRenderer.Bounds bounds = HudRenderer.bounds(font, selected, lines(selected), width, height);
				int x = Anchor.clamp(bounds.x() + dx, bounds.width(), width);
				int y = Anchor.clamp(bounds.y() + dy, bounds.height(), height);
				selected.offsetX = selected.anchorX.offset(x, bounds.width(), width);
				selected.offsetY = selected.anchorY.offset(y, bounds.height(), height);
				return true;
			}
		}
		return super.keyPressed(key);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent release) {
		mouseHeld = false;
		swallowDrag = false;
		if (lineDrag >= 0) {
			int from = lineDrag;
			lineDrag = -1;
			if (panelBox != null) panelBox.moveLine(from, lineGap);
			rebuildPanel();
			return true;
		}
		if (dragging == null) return super.mouseReleased(release);
		dragging = null;
		guides = List.of();
		rebuildPanel();
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		super.removed();
		syncFields();
		hud.save();
	}

	// new button each time, owo doesnt re-measure a changed label
	private void armReset(boolean armed) {
		resetArmed = armed;
		if (resetButton != null) {
			dropFocusIn(resetButton);
			toolbarTips.remove(resetButton);
			toolbar.removeChild(resetButton);
		}
		resetButton = button(armed ? "§cReally reset all?" : "Reset all", b -> {
			if (!resetArmed) {
				armReset(true);
				return;
			}
			armReset(false);
			hud.setLayout(HudLayout.defaults());
			// set here so it doesnt look like a late file load
			shownLayout = hud.layout();
			selected = null;
			rebuildPanel();
		});
		tip(toolbarTips, resetButton, armed ? "Click again to replace all boxes with the three default ones. Ctrl+Z brings yours back."
				: "Replace all boxes with the three default ones");
		resetButton.margins(Insets.left(6));
		toolbar.child(resetButton);
	}

	private void newBox() {
		Box box = new Box();
		box.id = newBoxId();
		box.title = "New box";
		box.showTitle = true;
		for (Anchor[] spot : NEW_BOX_SPOTS) {
			box.anchorX = spot[0];
			box.anchorY = spot[1];
			HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines(box), width, height).grown(HudLayout.MARGIN);
			if (drawn.values().stream().noneMatch(b -> bounds.overlapArea(b) > 0)) break;
		}
		hud.layout().boxes.add(box);
		selected = box;
		scrollLeft = 0;
		pageBox = box.id;
		editedIndex = -1;
		picking = true;
		rebuildPanel();
	}

	private String newBoxId() {
		for (int n = 1;; n++) {
			String id = "box" + n;
			if (hud.layout().boxes.stream().noneMatch(b -> id.equals(b.id))) return id;
		}
	}

	private void armDelete(boolean armed) {
		deleteArmed = armed;
		if (deleteRow == null) return;
		if (deleteButton != null) {
			dropFocusIn(deleteButton);
			panelTips.remove(deleteButton);
			deleteRow.removeChild(deleteButton);
		}
		Box box = panelBox;
		deleteButton = iconTextButton(REMOVE, armed ? "§cReally delete?" : "§cDelete box", null, b -> {
			if (!deleteArmed) {
				armDelete(true);
				return;
			}
			deleteArmed = false;
			hud.layout().boxes.remove(box);
			selected = null;
			rebuildPanel();
		});
		tip(deleteButton, armed ? "Click again to delete this box. Ctrl+Z brings it back."
				: "Delete this box. Ctrl+Z brings it back.");
		deleteRow.child(deleteButton);
	}

	private record Field(Supplier<String> value, Consumer<String> apply) {
	}

	// polled bc caxton cancels onValueChange
	private void syncFields() {
		for (Field field : fields) field.apply().accept(field.value().get());
	}

	// waits out drags, mouse down, title typing. now = record right away
	private void recordChanges(boolean now) {
		String json = hud.layout().toJson();
		if (json.equals(history.current())) {
			unsettled = null;
			return;
		}
		if (!now) {
			if (dragging != null || lineDrag >= 0 || mouseHeld || typing()) return;
			long time = Util.getMillis();
			if (!json.equals(unsettled)) {
				unsettled = json;
				unsettledSince = time;
				return;
			}
			if (time - unsettledSince < SETTLE_MILLIS) return;
		}
		unsettled = null;
		history.record(json);
		hud.save();
	}

	// owo mixins make EditBox + MultiLineEditBox greedy, javac cant see the 2nd
	private boolean typing() {
		return root.focusHandler().focused() instanceof GreedyInputUIComponent;
	}

	// owo keeps a removed component focused, enter/space would still press it
	private void dropFocusIn(UIComponent removed) {
		for (UIComponent c = root.focusHandler().focused(); c != null; c = c.parent()) {
			if (c == removed) {
				root.focusHandler().focus(null, null);
				return;
			}
		}
	}

	private static List<String> lineValues(Box box) {
		return box.lines.stream().map(line -> line.value).toList();
	}

	private void undo(boolean redo) {
		syncFields();
		recordChanges(true);
		String json = redo ? history.redo() : history.undo();
		if (json == null) return;
		String selectedId = selected == null ? null : selected.id;
		List<String> lineValues = selected == null ? null : lineValues(selected);
		HudLayout layout = HudLayout.fromJson(json);
		hud.setLayout(layout);
		shownLayout = layout;
		selected = null;
		for (Box box : layout.boxes) {
			if (box.id.equals(selectedId)) selected = box;
		}
		// lines moved, the index may point at another line now
		if (selected != null && !lineValues(selected).equals(lineValues)) editedIndex = -1;
		dragging = null;
		lineDrag = -1;
		guides = List.of();
		if (resetArmed) armReset(false);
		rebuildPanel();
		hud.save();
	}

	private List<String> lines(Box box) {
		List<String> lines = HudBoxText.lines(box, hud.data(), true);
		return lines.isEmpty() ? List.of("§8(empty)") : lines;
	}

	// not while dragging or reset armed, so nothing jumps under the mouse
	private void placeToolbar() {
		if (dragging != null || resetArmed || toolbar.width() == 0) return;
		int best = 0;
		long bestCost = Long.MAX_VALUE;
		for (int i = 0; i < TOOLBAR_SPOTS.length; i++) {
			int x = PADDING + Math.round(TOOLBAR_SPOTS[i][0] / 100f * (width - 2 * PADDING - toolbar.width()));
			int y = PADDING + Math.round(TOOLBAR_SPOTS[i][1] / 100f * (height - 2 * PADDING - toolbar.height()));
			long cost = coverCost(new HudRenderer.Bounds(x, y, toolbar.width(), toolbar.height()));
			if (cost < bestCost) {
				best = i;
				bestCost = cost;
			}
			if (cost == 0) break;
		}
		moveToolbar(best);
	}

	private void moveToolbar(int spot) {
		if (spot == toolbarSpot) return;
		toolbarSpot = spot;
		toolbar.positioning(Positioning.relative(TOOLBAR_SPOTS[spot][0], TOOLBAR_SPOTS[spot][1]));
	}

	private long coverCost(HudRenderer.Bounds area) {
		long cost = 0;
		for (Map.Entry<Box, HudRenderer.Bounds> drawnBox : drawn.entrySet()) {
			HudRenderer.Bounds outlined = drawnBox.getValue().grown(1);
			long covered = area.overlapArea(outlined);
			cost += covered;
			if (covered == (long) outlined.width() * outlined.height()) cost += 1_000_000;
			// never over the box being edited if theres another spot
			if (covered > 0 && drawnBox.getKey() == selected) cost += 10_000_000;
		}
		if (panelScroll != null && area.overlapArea(
				new HudRenderer.Bounds(panelScroll.x(), panelScroll.y(), panelScroll.width(), panelScroll.height())) > 0) {
			cost += 100_000_000;
		}
		return cost;
	}

	private boolean overPanel(double x, double y) {
		return toolbar.isInBoundingBox(x, y) || inPanel(x, y) || (menu != null && menu.isInBoundingBox(x, y));
	}

	// empty part under the content lets boxes show + get clicked
	private boolean inPanel(double x, double y) {
		return panelScroll != null && panelScroll.isInBoundingBox(x, y) && y < panelBottom();
	}

	private int panelBottom() {
		return Math.min(panelScroll.y() + panelScroll.height(), panel.y() + panel.height());
	}

	// sizes known after layout
	private void fitPanel() {
		if (panelScroll == null || panelScroll.width() == 0) return;
		// no room beside it = leave the top for the toolbar
		boolean narrow = toolbar.width() + panelScroll.width() + 3 * PADDING > width;
		// title goes first, so the panel keeps the full height at 480 wide
		if (narrow && toolbarTitle.parent() != null) {
			toolbar.removeChild(toolbarTitle);
			return;
		}
		int reserve = narrow ? toolbar.height() + PADDING : 0;
		// screen height, not content, a content fit jumps around
		int fit = height - 2 * PADDING - reserve;
		if (fit != panelHeight || panelScroll.height() != fit) {
			panelHeight = fit;
			panelScroll.verticalSizing(Sizing.fixed(fit));
			return;
		}
		if (scrollToRow >= 0) {
			if (scrollToRow < lineRows.size()) showRow(lineRows.get(scrollToRow));
			scrollToRow = -1;
		}
	}

	private void showRow(UIComponent row) {
		double offset = panelScroll.offset();
		int top = row.y() - panelScroll.y();
		int bottom = top + row.height();
		if (bottom > panelScroll.height() - 4) offset += bottom - panelScroll.height() + 8;
		else if (top < 4) offset -= 8 - top;
		panelScroll.offset(Math.max(0, offset));
	}

	private static final class PanelScroll extends ScrollContainer<FlowLayout> {
		PanelScroll(int height, FlowLayout content) {
			super(ScrollDirection.VERTICAL, Sizing.content(), Sizing.fixed(height), content);
		}

		double offset() {
			return scrollOffset;
		}

		void offset(double offset) {
			scrollOffset = offset;
			currentScrollPosition = offset;
		}

		// owo only clamps the target, clamp drawn pos too or a shorter panel overshoots
		@Override
		public void layout(Size space) {
			super.layout(space);
			scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
			currentScrollPosition = Math.max(0, Math.min(currentScrollPosition, maxScroll + .5));
		}
	}

	private static final class HoverRow extends FlowLayout {
		HoverRow() {
			super(Sizing.fill(100), Sizing.content(), Algorithm.HORIZONTAL);
			gap(2);
			verticalAlignment(VerticalAlignment.CENTER);
			padding(Insets.vertical(1));
			cursorStyle(CursorStyle.HAND);
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			if (isInBoundingBox(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, HOVER);
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
		}
	}

	// equal cells, so options never wrap one by one
	private static final class Grid extends FlowLayout {
		private final int cell;

		Grid(int columns, int keyWidth) {
			super(Sizing.fixed(INNER - keyWidth - 2), Sizing.content(), Algorithm.LTR_TEXT);
			gap(2);
			cell = (INNER - keyWidth - 2 - (columns - 1) * 2) / columns;
		}

		ButtonComponent add(ButtonComponent button) {
			button.horizontalSizing(Sizing.fixed(cell));
			child(button);
			return button;
		}
	}

	private static final class FreeTextArea extends TextAreaComponent {
		FreeTextArea(Sizing horizontal, Sizing vertical) {
			super(horizontal, vertical);
		}

		// vanilla cuts it to the char limit, half a tag is worse than none
		boolean insert(String text) {
			int length = editBox.value().length() - editBox.getSelectedText().length() + text.length();
			if (length > editBox.characterLimit()) return false;
			editBox.insertText(text);
			return true;
		}

		// 1 HUD line, no newlines or tab spaces
		@Override
		public boolean keyPressed(KeyEvent input) {
			if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) return true;
			if (input.isCycleFocus()) return false;
			// vanilla paste keeps newlines
			if (input.isPaste()) {
				editBox.insertText(Minecraft.getInstance().keyboardHandler.getClipboard().replace("\r", "").replace('\n', ' '));
				return true;
			}
			return super.keyPressed(input);
		}

		// vanilla eats the wheel even w/ nothing to scroll, panel should scroll
		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
			return maxScrollAmount() > 0 && super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
	}

	private void removePanel() {
		syncFields();
		if (panelScroll != null) {
			dropFocusIn(panelScroll);
			scrollMemory.put(panelKey, panelScroll.offset());
			root.removeChild(panelScroll);
		}
		closeMenu();
		panelScroll = null;
		panel = null;
		panelBox = null;
		panelTips.clear();
		helpLines = null;
		pinnedLines = null;
		helpValue = null;
		helpButton = null;
		scaleSlider = null;
		fields.clear();
		homeFields.clear();
		lineRows.clear();
		lineHandles.clear();
		deleteButton = null;
		deleteRow = null;
		deleteArmed = false;
		preview = null;
		previewLine = null;
		previewRaw = null;
		freeArea = null;
		freeHint = null;
		freeHintText = null;
		hintSource = null;
		hintNoRoom = false;
		noRoomFor = null;
	}

	private void rebuildPanel() {
		if (selected == null || !selected.id.equals(pageBox)) {
			picking = false;
			editedIndex = -1;
		}
		if (selected != null && editedIndex >= selected.lines.size()) editedIndex = -1;
		if (picking || editedIndex < 0) helpPinned = false;
		removePanel();
		if (selected == null) {
			pageBox = null;
			return;
		}
		Box box = selected;
		pageBox = box.id;
		panelBox = box;
		panelKey = box.id + "/" + (picking ? "picker" : editedIndex >= 0 ? "line" + editedIndex : "box");
		panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
		panel.padding(Insets.of(5));
		panel.gap(1);
		panelScroll = new PanelScroll(panelHeight, panel);
		panelScroll.surface((graphics, c) -> {
			int h = panelBottom() - c.y();
			graphics.fill(c.x(), c.y(), c.x() + c.width(), c.y() + h, PANEL_FILL);
			graphics.drawRectOutline(c.x(), c.y(), c.width(), h, PANEL_OUTLINE);
		});
		panelScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x80FFFFFF)));
		panelScroll.scrollbarThiccness(2);
		panelScroll.offset(scrollMemory.getOrDefault(panelKey, 0.0));
		// measured now, after undo the box wasnt drawn yet
		HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines(box), width, height);
		boolean boxOnRight = bounds.x() + bounds.width() / 2 > width / 2;
		panelScroll.positioning(Positioning.relative(boxOnRight ? 0 : 100, 100));
		if (picking) buildPicker(box);
		else if (editedIndex >= 0) buildLinePage(box, editedIndex);
		else buildBoxPage(box);
		// after the content, owo lays out right away and would clamp the offset to 0
		root.child(panelScroll);
		rebuiltByClick = true;
	}

	private void openLine(int index) {
		editedIndex = index;
		scrollMemory.remove(pageBox + "/line" + index);
		rebuildPanel();
	}

	private void backToLines() {
		scrollToRow = editedIndex;
		editedIndex = -1;
		picking = false;
		rebuildPanel();
	}

	private void buildBoxPage(Box box) {
		FlowLayout linesHead = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		linesHead.verticalAlignment(VerticalAlignment.CENTER);
		linesHead.child(label("§fLines §8(" + box.lines.size() + ")"));
		ButtonComponent addLine = button("+ Add line", b -> {
			picking = true;
			rebuildPanel();
		});
		tip(addLine, "Add any value to this box");
		linesHead.child(spacer());
		linesHead.child(addLine);
		panel.child(linesHead);
		for (int i = 0; i < box.lines.size(); i++) panel.child(lineRow(box, i));
		if (box.lines.isEmpty()) panel.child(label("§7No lines yet. Add one to show a value."));

		panel.child(heading("Box"));
		TextBoxComponent title = textField(box.title, 64, text -> box.title = text);
		tip(title, "Box title. {shaft_type} shows the shaft's type.");
		FlowLayout titleRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		titleRow.gap(3);
		titleRow.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout showTitle = check(null, box.showTitle, checked -> box.showTitle = checked);
		tip(showTitle, "Show the title");
		titleRow.child(showTitle);
		titleRow.child(title);
		panel.child(titleRow);

		DiscreteSliderComponent scale = UIComponents.discreteSlider(Sizing.expand(), HudLayout.MIN_SCALE, HudLayout.MAX_SCALE);
		scale.decimalPlaces(1);
		scale.setFromDiscreteValue(box.scale);
		scale.verticalSizing(Sizing.fixed(CHOICE));
		scale.scrollStep(SCALE_STEP / (HudLayout.MAX_SCALE - HudLayout.MIN_SCALE));
		scale.message(text -> Component.literal(text + "×"));
		scale.onChanged().subscribe(value -> box.scale = (float) scale.discreteValue());
		tip(scale, "Box size. You can also scroll on the box.");
		scaleSlider = scale;
		panel.child(row("Size", scale));

		Grid background = new Grid(Background.values().length, ROW_LABEL);
		for (Background option : Background.values()) {
			background.add(choice(name(option), box.background == option, () -> box.background = option));
		}
		panel.child(row("Background", background));

		Grid align = new Grid(Align.values().length, ROW_LABEL);
		for (Align option : Align.values()) {
			align.add(choice(name(option), box.align == option, () -> box.align = option));
		}
		panel.child(row("Align", align));

		FlowLayout places = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		places.child(place("Shafts", box.showInShaft, on -> box.showInShaft = on, "In a Glacite Mineshaft"));
		places.child(place("Mines", box.showInMines, on -> box.showInMines = on, "In the Dwarven Mines, outside a shaft"));
		places.child(place("Elsewhere", box.showElsewhere, on -> box.showElsewhere = on,
				"Anywhere else: the Hub, other islands, other servers"));
		FlowLayout shows = row("Show in", places);
		shows.verticalAlignment(VerticalAlignment.TOP);
		// label level w/ the 1st checkbox
		shows.children().getFirst().margins(Insets.top(4));
		shows.margins(Insets.top(1));
		panel.child(shows);

		panel.child(boxColor("Labels", box.labelColor, code -> box.labelColor = code, box.labelBold, b -> box.labelBold = b));
		panel.child(boxColor("Values", box.valueColor, code -> box.valueColor = code, box.valueBold, b -> box.valueBold = b));

		deleteRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		deleteRow.margins(Insets.top(5));
		panel.child(deleteRow);
		armDelete(false);
	}

	private FlowLayout boxColor(String name, String current, Consumer<String> setColor, boolean bold,
			Consumer<Boolean> setBold) {
		FlowLayout row = row(name);
		row.margins(Insets.top(1));
		ButtonComponent color = colorButton(current, null, setColor);
		tip(color, name + " colour of this box: " + COLOR_NAMES[colorIndex(current)]
				+ ". Lines with their own colour keep it.");
		row.child(color);
		ButtonComponent boldButton = button((bold ? "§e" : "§f") + "§lB", b -> {
			setBold.accept(!bold);
			rebuildPanel();
		});
		boldButton.verticalSizing(Sizing.fixed(CHOICE));
		tip(boldButton, "Bold");
		row.child(boldButton);
		return row;
	}

	private void buildPicker(Box box) {
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(4);
		header.verticalAlignment(VerticalAlignment.CENTER);
		ButtonComponent back = button("‹ Back", b -> backToLines());
		tip(back, "Back without adding (Esc)");
		header.child(back);
		header.child(label("§fAdd a line"));
		panel.child(header);
		for (HudValue.Group group : HudValue.Group.values()) {
			panel.child(section(group.displayName()));
			for (HudValue value : HudValue.values()) {
				if (value.group() != group) continue;
				HoverRow row = new HoverRow();
				LabelComponent name = label(clip("§f" + value.displayName(), 86));
				name.horizontalSizing(Sizing.fixed(88));
				row.child(name);
				LabelComponent shows = label(clip(HudBoxText.line(box, new Line(value), hud.data(), true), INNER - 88 - 20));
				shows.horizontalSizing(Sizing.expand());
				row.child(shows);
				if (box.lines.stream().anyMatch(line -> line.hudValue() == value)) {
					LabelComponent have = label("§a✔");
					tip(have, "Already in this box. You can add it again, f.e. with another range.");
					row.child(have);
				}
				tip(row, "Add " + value.displayName());
				row.mouseDown().subscribe((click, doubled) -> {
					box.lines.add(new Line(value));
					picking = false;
					openLine(box.lines.size() - 1);
					return true;
				});
				panel.child(row);
			}
		}
	}

	private FlowLayout lineRow(Box box, int index) {
		Line line = box.lines.get(index);
		HoverRow row = new HoverRow();
		row.verticalSizing(Sizing.fixed(CHOICE));
		row.gap(4);
		FlowLayout grip = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(CHOICE - 2));
		grip.surface(GRIP);
		grip.cursorStyle(CursorStyle.MOVE);
		tip(grip, "Drag to move this line");
		row.child(grip);
		SmallCheckboxComponent shown = UIComponents.smallCheckbox(null);
		shown.checked(line.shown);
		shown.onChanged().subscribe(checked -> {
			line.shown = checked;
			rebuildPanel();
		});
		tip(shown, "Show this line");
		row.child(shown);
		String text = HudBoxText.line(box, line, hud.data(), true);
		if (!line.shown) text = "§8" + strip(text);
		// grip, checkbox, marker, gaps + slack so it never wraps
		LabelComponent look = label(clip(text, INNER - 40));
		look.horizontalSizing(Sizing.expand());
		tip(look, "Click to change the label, range, format and colours");
		row.child(look);
		UIComponent marker;
		if (line.advanced) {
			FlowLayout pencil = UIContainers.horizontalFlow(Sizing.fixed(8), Sizing.fixed(PENCIL_ICON.length));
			pencil.surface((graphics, c) -> drawIcon(graphics, PENCIL_ICON, c.x(), c.y(), false, 0xFF55FFFF));
			marker = pencil;
		} else {
			marker = label("§8›");
		}
		marker.margins(Insets.right(2));
		if (line.advanced) tip(marker, "Uses custom text");
		row.child(marker);
		row.mouseDown().subscribe((click, doubled) -> {
			openLine(index);
			return true;
		});
		lineRows.add(row);
		lineHandles.add(List.of(grip));
		return row;
	}

	private void buildLinePage(Box box, int index) {
		Line line = box.lines.get(index);
		HudValue value = line.hudValue();
		if (!line.advanced) helpPinned = false;
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(3);
		header.verticalAlignment(VerticalAlignment.CENTER);
		ButtonComponent back = button("‹ Back", b -> backToLines());
		tip(back, "Back to the box (Esc)");
		header.child(back);
		ButtonComponent remove = iconTextButton(REMOVE, "§cRemove", null, b -> {
			box.lines.remove(line);
			editedIndex = -1;
			scrollToRow = Math.min(index, box.lines.size() - 1);
			rebuildPanel();
		});
		tip(remove, "Remove this line. Ctrl+Z brings it back.");
		String range = value.ranges().size() > 1 && !line.advanced ? " · " + line.range.displayName() : "";
		// back, remove, gaps
		LabelComponent title = label(clip("§7" + value.displayName() + range,
				INNER - font.width("‹ Back") - 8 - fixedWidth(remove) - 8));
		title.horizontalSizing(Sizing.expand());
		header.child(title);
		header.child(remove);
		panel.child(header);

		FlowLayout look = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		look.surface(PREVIEW);
		look.padding(Insets.of(3, 3, 5, 3));
		look.margins(Insets.vertical(3));
		preview = label("");
		previewLine = line;
		look.child(preview);
		if (!line.shown) {
			look.child(spacer());
			LabelComponent hidden = label("§7" + HIDDEN_NOTE);
			tip(hidden, "This line is hidden. Tick it in the box's line list to show it.");
			look.child(hidden);
		}
		panel.child(look);

		if (!line.advanced) {
			panel.child(heading("Label"));
			FlowLayout labelRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
			labelRow.gap(3);
			labelRow.verticalAlignment(VerticalAlignment.CENTER);
			TextBoxComponent prefix = textField(line.prefix, 64, typed -> line.prefix = typed);
			tip(prefix, "The text before the value. Spaces count.");
			labelRow.child(prefix);
			ButtonComponent labelColor = colorButton(line.labelColor, box.labelColor, code -> line.labelColor = code);
			tip(labelColor, "Label colour. \"Box\" follows the box's label colour.");
			labelRow.child(labelColor);
			panel.child(labelRow);

			panel.child(heading("Value"));
			List<HudValue.Range> ranges = value.ranges();
			if (ranges.size() > 1) {
				Grid options = new Grid(2, KEY);
				for (HudValue.Range r : ranges) {
					tip(options.add(choice(r.displayName(), line.range == r, () -> line.range = r)),
							"Shaft: the one you're in, or the last one. Per hour: per hour of this session.");
				}
				panel.child(keyRow("Range", options));
			}
			List<String> formats = HudFormat.options(value.kind());
			if (formats.size() > 1) {
				Grid options = new Grid(formats.size(), KEY);
				for (int i = 0; i < formats.size(); i++) {
					int format = i;
					tip(options.add(choice(formats.get(i), line.format == i, () -> line.format = format)), "Number format");
				}
				panel.child(keyRow("Format", options));
			}
			ButtonComponent valueColor = colorButton(line.valueColor, box.valueColor, code -> line.valueColor = code);
			tip(valueColor, "Value colour. \"Box\" follows the box's value colour. Colour rules beat it.");
			panel.child(keyRow("Colour", valueColor));
		}

		if (value.hasNumber()) {
			panel.child(heading("Colour rules"));
			String ready = HudColors.readyRule(value);
			if (ready != null) {
				FlowLayout rule = check(ready, line.readyRule, on -> line.readyRule = on);
				tip(rule, "A ready-made colour rule: " + ready + ". Your own rules below come first.");
				panel.child(rule);
			}
			List<HudLayout.ColorRule> rules = line.rules == null ? List.of() : line.rules;
			for (HudLayout.ColorRule rule : rules) ruleRow(line, rule);
			ButtonComponent add = button("+ Colour rule", b -> {
				if (line.rules == null) line.rules = new ArrayList<>();
				HudLayout.ColorRule rule = new HudLayout.ColorRule();
				Double now = hud.data().number(value, HudBoxText.ruleRange(line), true);
				if (now != null) rule.value = value.kind() == HudValue.Kind.TIME ? Math.floor(now / 1000) : Math.round(now);
				line.rules.add(rule);
				rebuildPanel();
			});
			tip(add, "Colour the value when it's over or under a number you pick. "
					+ "Rules beat the value colour, the first one that matches wins.");
			add.margins(Insets.top(2));
			panel.child(add);
		}

		// same button turns it off again, text is kept
		ButtonComponent custom = iconTextButton(pixels(PENCIL_ICON), line.advanced ? "§eTurn off" : "Turn on", null, b -> {
			if (!line.advanced && (line.text == null || line.text.isBlank())) line.text = HudBoxText.basicText(box, line);
			line.advanced = !line.advanced;
			rebuildPanel();
		});
		tip(custom, line.advanced ? "Back to the label, range, format and colours. Your text is kept for next time."
				: "Type the whole line yourself, with colours like &e and values like {spawn_timer}. "
						+ "For two values on one line, or a colour per word. Starts from how the line looks now.");
		// clipped, caxton fonts push the button out
		FlowLayout customHead = heading(clip("Custom text §8(ADVANCED)", INNER - fixedWidth(custom) - 2));
		customHead.child(spacer());
		customHead.child(custom);
		panel.child(customHead);
		if (line.advanced) customText(box, line);
	}

	private void customText(Box box, Line line) {
		FreeTextArea area = new FreeTextArea(Sizing.fill(100), Sizing.fixed(4 * font.lineHeight + 8));
		area.setCharacterLimit(256);
		area.text(line.text);
		area.margins(Insets.top(2));
		String[] last = {line.text};
		fields.add(new Field(area::getValue, typed -> {
			// pasted newlines, 1 HUD line
			if (typed.indexOf('\n') >= 0 || typed.indexOf('\r') >= 0) {
				typed = typed.replace("\r", "").replace('\n', ' ');
				area.text(typed);
			}
			if (typed.equals(last[0])) return;
			last[0] = typed;
			line.text = typed;
		}));
		freeArea = area;
		panel.child(area);

		FlowLayout insert = optionFlow();
		ButtonComponent addValue = button("+ Value ▾", this::valueMenu);
		tip(addValue, "Type a value at the cursor");
		insert.child(addValue);
		ButtonComponent codes = button("Colour ▾", this::codeMenu);
		tip(codes, "Type a colour or style at the cursor");
		insert.child(codes);
		helpValue = line.hudValue();
		helpLines = helpLines(helpValue, width - 4 * TIP_MARGIN);
		helpButton = button(helpPinned ? "§e?" : "?", b -> pinHelp(!helpPinned));
		helpButton.horizontalSizing(Sizing.fixed(BUTTON));
		tipTarget.put(helpButton, helpLines);
		insert.child(helpButton);
		panel.child(insert);

		freeHint = label(TEXT_HINT);
		freeHint.maxWidth(INNER);
		freeHintText = TEXT_HINT;
		panel.child(freeHint);
		ButtonComponent again = iconTextButton(pixels(RESET_ICON), "Reset", null, b -> {
			line.text = HudBoxText.basicText(box, line);
			rebuildPanel();
		});
		tip(again, "Replace the text with the line's label, range, format and colours");
		panel.child(again);
	}

	// click keeps the help open beside the panel while typing
	private void pinHelp(boolean pinned) {
		helpPinned = pinned;
		if (helpButton != null) helpButton.setMessage(Component.literal(pinned ? "§e?" : "?"));
	}

	// live, the text fields are polled
	private void refreshLinePage() {
		if (preview != null) {
			String text = HudBoxText.line(panelBox, previewLine, hud.data(), true);
			if (!previewLine.shown) text = "§8" + strip(text);
			if (!text.equals(previewRaw)) {
				previewRaw = text;
				// padding, + note and gap if hidden
				int room = INNER - 8 - (previewLine.shown ? 0 : font.width(HIDDEN_NOTE) + 4);
				preview.text(Component.literal(clip(text, room)));
			}
		}
		if (freeHint == null) return;
		String source = previewLine.text;
		if (noRoomFor != null && !noRoomFor.equals(source)) noRoomFor = null;
		boolean noRoom = noRoomFor != null;
		// same object = same text, the poll only swaps it on change
		if (source == hintSource && noRoom == hintNoRoom) return;
		hintSource = source;
		hintNoRoom = noRoom;
		List<String> unknown = HudBoxText.unknownTags(source);
		String hint = noRoom ? "§cNo room for that, free text is 256 characters max."
				: unknown.isEmpty() ? TEXT_HINT
				: "§cNot a value: " + String.join(" ", unknown).replace("§", "") + ". It shows as typed.";
		if (!hint.equals(freeHintText)) {
			freeHintText = hint;
			freeHint.text(Component.literal(hint));
		}
	}

	private void valueMenu(UIComponent anchor) {
		openMenu(anchor, m -> {
			for (HudValue.Group group : HudValue.Group.values()) {
				m.child(menuTitle(group.displayName()));
				for (HudValue value : HudValue.values()) {
					if (value.group() != group) continue;
					boolean more = value.ranges().size() > 1 || HudFormat.options(value.kind()).size() > 1;
					m.child(menuEntry("§f" + value.displayName() + (more ? " §7›" : ""), () -> rangeMenu(anchor, value)));
				}
			}
		});
	}

	private void rangeMenu(UIComponent anchor, HudValue value) {
		if (value.ranges().size() <= 1) {
			formatMenu(anchor, value, value.defaultRange());
			return;
		}
		openMenu(anchor, m -> {
			m.child(menuTitle(value.displayName()));
			for (HudValue.Range range : value.ranges()) {
				m.child(menuEntry("§f" + range.displayName() + " §7›", () -> formatMenu(anchor, value, range)));
			}
		});
	}

	private void formatMenu(UIComponent anchor, HudValue value, HudValue.Range range) {
		int formats = HudFormat.options(value.kind()).size();
		if (formats <= 1) {
			insertText(HudBoxText.tag(value, range, 0));
			return;
		}
		openMenu(anchor, m -> {
			m.child(menuTitle(value.displayName() + (value.ranges().size() > 1 ? " · " + range.displayName() : "")));
			// own examples, real values can look the same in every format
			List<String> examples = HudFormat.options(value.kind());
			for (int i = 0; i < formats; i++) {
				int format = i;
				m.child(menuEntry("§f" + examples.get(i), () -> insertText(HudBoxText.tag(value, range, format))));
			}
		});
	}

	private void codeMenu(UIComponent anchor) {
		openMenu(anchor, m -> {
			m.child(menuTitle("Colour"));
			swatchGrid(m, null, true, code -> insertText("&" + code));
			m.child(menuEntry("§7Box label colour §8&r", () -> insertText("&r")));
			m.child(menuEntry("§fBox value colour §8&v", () -> insertText("&v")));
			m.child(menuTitle("Style, until the next colour"));
			m.child(menuEntry("§f§lBold §8&l", () -> insertText("&l")));
			m.child(menuEntry("§f§oItalic §8&o", () -> insertText("&o")));
			m.child(menuEntry("§f§nUnderline§r §8&n", () -> insertText("&n")));
			m.child(menuEntry("§f§mStrikethrough§r §8&m", () -> insertText("&m")));
		});
	}

	private void insertText(String text) {
		closeMenu();
		if (freeArea == null) return;
		if (!freeArea.insert(text)) noRoomFor = freeArea.getValue();
		root.focusHandler().focus(freeArea, UIComponent.FocusSource.MOUSE_CLICK);
		swallowDrag = true;
	}

	private void openMenu(UIComponent anchor, Consumer<FlowLayout> fill) {
		closeMenu();
		tipTarget = menuTips;
		menu = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		menu.surface(MENU);
		menu.padding(Insets.of(2));
		// headings + gaps would pass clicks to the panel below
		menu.mouseDown().subscribe((click, doubled) -> true);
		menu.mouseScroll().subscribe((x, y, amount) -> true);
		menuAnchor = anchor;
		try {
			fill.accept(menu);
		} finally {
			tipTarget = panelTips;
		}
		menu.positioning(Positioning.absolute(anchor.x() - root.x() - PADDING,
				anchor.y() + anchor.height() + 1 - root.y() - PADDING));
		// owo lays it out right away, so no frame below the screen
		root.child(menu);
		fitMenu();
		rebuiltByClick = true;
	}

	// kept on screen, sizes known after layout
	private void fitMenu() {
		if (menu == null || menu.width() == 0) return;
		int x = Math.max(PADDING, Math.min(menu.x(), width - PADDING - menu.width()));
		int y = menu.y();
		if (y + menu.height() > height - PADDING) y = Math.max(PADDING, menuAnchor.y() - 1 - menu.height());
		if (x != menu.x() || y != menu.y()) menu.positioning(Positioning.absolute(x - root.x() - PADDING, y - root.y() - PADDING));
	}

	private void closeMenu() {
		if (menu == null) return;
		menuTips.clear();
		root.removeChild(menu);
		menu = null;
		menuAnchor = null;
	}

	private static LabelComponent menuTitle(String text) {
		LabelComponent title = label("§7" + text);
		title.margins(Insets.of(2, 1, 2, 2));
		return title;
	}

	private static FlowLayout menuEntry(String text, Runnable action) {
		HoverRow row = new HoverRow();
		row.horizontalSizing(Sizing.content());
		row.padding(Insets.of(1, 1, 3, 6));
		row.child(label(text));
		row.mouseDown().subscribe((click, doubled) -> {
			action.run();
			return true;
		});
		return row;
	}

	private void ruleRow(Line line, HudLayout.ColorRule rule) {
		HudValue.Kind kind = line.hudValue().kind();
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(2);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.top(2));
		ButtonComponent when = choice(rule.when == HudLayout.ColorRule.When.OVER ? "Over" : "Under", false,
				() -> rule.when = rule.when == HudLayout.ColorRule.When.OVER ? HudLayout.ColorRule.When.UNDER
						: HudLayout.ColorRule.When.OVER);
		tip(when, "Click to switch between over and under");
		row.child(when);
		TextBoxComponent number = textField(HudFormat.thresholdText(kind, rule.value), 12, typed -> {
			Double parsed = HudFormat.parseThreshold(kind, typed);
			if (parsed != null) rule.value = parsed;
		});
		number.horizontalSizing(Sizing.fixed(56));
		tip(number, kind == HudValue.Kind.TIME ? "A time, like 1:30 or 90 (seconds)" : "A number");
		row.child(number);
		ButtonComponent color = colorButton(rule.color, null, code -> rule.color = code);
		tip(color, "Colour when the rule matches");
		row.child(color);
		row.child(spacer());
		ButtonComponent remove = iconTextButton(REMOVE, "", "Remove this rule", b -> {
			line.rules.remove(rule);
			if (line.rules.isEmpty()) line.rules = null;
			rebuildPanel();
		});
		remove.verticalSizing(Sizing.fixed(CHOICE));
		tip(remove, "Remove this rule");
		row.child(remove);
		panel.child(row);
	}

	// wider when it wouldnt fit the screen's height
	private List<FormattedCharSequence> helpLines(HudValue own, int maxWidth) {
		StringBuilder help = new StringBuilder("§fCustom text\n§7&0-&f colours, &l bold, &o italic, &n underline, "
				+ "&m strikethrough. &r is the box's label colour, &v its value colour. && and &{ type a plain & and {.\n"
				+ "§7Values: §f{value_id}§7, options after a colon: §f{session_powder:all_time:short}");
		for (HudValue value : HudValue.values()) {
			help.append(value == own ? "\n§e" : "\n§f").append('{').append(value.id()).append('}');
			List<String> ranges = value.ranges().size() > 1 ? value.ranges().stream().map(HudValue.Range::tagName).toList() : List.of();
			List<String> formats = HudFormat.names(value.kind());
			String options = String.join(", ", ranges) + (!ranges.isEmpty() && !formats.isEmpty() ? " · " : "")
					+ String.join(", ", formats);
			if (!options.isEmpty()) help.append(" §8").append(options);
		}
		Component text = Component.literal(help.toString());
		List<FormattedCharSequence> lines = List.of();
		for (int wrap : new int[] {300, 400, width}) {
			lines = font.split(text, Math.min(wrap, maxWidth));
			// 10px a line + frame
			if (lines.size() * 10 + 8 <= height - 2 * TIP_MARGIN) break;
		}
		return lines;
	}

	private TextBoxComponent textField(String text, int maxLength, Consumer<String> apply) {
		TextBoxComponent field = UIComponents.textBox(Sizing.expand());
		field.verticalSizing(Sizing.fixed(BUTTON));
		field.setMaxLength(maxLength);
		field.text(text);
		String[] last = {text};
		fields.add(new Field(field::getValue, typed -> {
			if (typed.equals(last[0])) return;
			last[0] = typed;
			apply.accept(typed);
		}));
		// caxton: text set at 0 width can scroll 1st letter out, rehome next frame
		homeFields.add(field);
		return field;
	}

	private int lineHandleAt(double x, double y) {
		// rows scrolled out of view keep their bounds, under the toolbar on narrow screens
		if (!inPanel(x, y)) return -1;
		for (int i = 0; i < lineHandles.size(); i++) {
			for (UIComponent handle : lineHandles.get(i)) {
				if (handle.isInBoundingBox(x, y)) return i;
			}
		}
		return -1;
	}

	// boxColor null = no "Box" choice. shows the colour it ends up as
	private ButtonComponent colorButton(String current, String boxColor, Consumer<String> setColor) {
		String text = current == null ? "§fBox §7▾" : "§7▾";
		int shown = colorIndex(current != null ? current : boxColor);
		ButtonComponent button = drawnButton("Colour: " + COLOR_NAMES[shown], b -> colorMenu(b, current, boxColor, setColor));
		button.verticalSizing(Sizing.fixed(CHOICE));
		button.horizontalSizing(Sizing.fixed(4 + 8 + 3 + font.width(text) + 4));
		button.renderer((graphics, b, delta) -> {
			ButtonComponent.Renderer.VANILLA.draw(graphics, b, delta);
			int x = b.getX() + 4;
			int y = b.getY() + (b.getHeight() - 8) / 2;
			graphics.fill(x - 1, y - 1, x + 9, y + 9, 0xFF000000);
			graphics.fill(x, y, x + 8, y + 8, 0xFF000000 | COLORS[shown]);
			graphics.text(font, text, x + 11, y, 0xFFFFFFFF, true);
		});
		return button;
	}

	private void colorMenu(UIComponent anchor, String current, String boxColor, Consumer<String> setColor) {
		openMenu(anchor, m -> {
			Consumer<String> pick = code -> {
				closeMenu();
				setColor.accept(code);
				rebuildPanel();
			};
			if (boxColor != null) {
				m.child(menuEntry((current == null ? "§e" : "§f") + "Box colour §7(" + COLOR_NAMES[colorIndex(boxColor)] + ")",
						() -> pick.accept(null)));
			}
			swatchGrid(m, current, false, pick);
		});
	}

	// dark row + light row, like the code order
	private void swatchGrid(FlowLayout menu, String selected, boolean showCode, Consumer<String> pick) {
		for (int half = 0; half < 2; half++) {
			FlowLayout swatches = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			swatches.gap(2);
			swatches.margins(Insets.of(2, 1, 3, 3));
			for (int i = half * 8; i < half * 8 + 8; i++) {
				String code = String.valueOf(HudLayout.COLOR_CODES.charAt(i));
				FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(MENU_SWATCH), Sizing.fixed(MENU_SWATCH));
				swatch.surface(Surface.flat(0xFF000000 | COLORS[i])
						.and(Surface.outline(code.equals(selected) ? OUTLINE_SELECTED : 0xFF000000)));
				tip(swatch, COLOR_NAMES[i] + (showCode ? " (&" + code + ")" : ""));
				swatch.cursorStyle(CursorStyle.HAND);
				swatch.mouseDown().subscribe((click, doubled) -> {
					pick.accept(code);
					return true;
				});
				swatches.child(swatch);
			}
			menu.child(swatches);
		}
	}

	private static int colorIndex(String code) {
		return Math.max(0, HudLayout.COLOR_CODES.indexOf(code == null || code.isEmpty() ? 'f' : code.charAt(0)));
	}

	private FlowLayout keyRow(String name, UIComponent child) {
		FlowLayout row = row(name, child);
		row.children().getFirst().horizontalSizing(Sizing.fixed(KEY));
		row.margins(Insets.vertical(1));
		return row;
	}

	private static FlowLayout heading(String text) {
		FlowLayout heading = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		heading.surface(DIVIDER);
		heading.padding(Insets.top(4));
		heading.margins(Insets.top(4));
		heading.verticalAlignment(VerticalAlignment.CENTER);
		heading.child(label("§f" + text));
		return heading;
	}

	// own label w/ a gap, clicking it ticks the box too. text null = box only
	private FlowLayout check(String text, boolean on, Consumer<Boolean> set) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		row.gap(4);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.vertical(1));
		SmallCheckboxComponent box = UIComponents.smallCheckbox(null);
		box.checked(on);
		box.onChanged().subscribe(set::accept);
		row.child(box);
		if (text != null) {
			LabelComponent name = label(text);
			name.cursorStyle(CursorStyle.HAND);
			name.mouseDown().subscribe((click, doubled) -> {
				if (click.button() != 0) return false;
				box.checked(!box.checked());
				return true;
			});
			row.child(name);
		}
		return row;
	}

	private FlowLayout optionFlow() {
		FlowLayout flow = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		flow.gap(2);
		flow.margins(Insets.vertical(1));
		return flow;
	}

	private FlowLayout row(String name, io.wispforest.owo.ui.core.UIComponent... children) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(2);
		row.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent label = label("§7" + name);
		label.horizontalSizing(Sizing.fixed(ROW_LABEL));
		row.child(label);
		for (var child : children) row.child(child);
		return row;
	}

	private ButtonComponent choice(String text, boolean on, Runnable action) {
		ButtonComponent choice = button((on ? "§e" : "§f") + text, b -> {
			action.run();
			rebuildPanel();
		});
		choice.verticalSizing(Sizing.fixed(CHOICE));
		return choice;
	}

	// cut to width w/ "…", never mid § code. binary search, caxton shapes every width call
	private String clip(String text, int max) {
		if (font.width(text) <= max) return text;
		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (font.width(text.substring(0, codeSafe(text, mid)) + "…") <= max) low = mid;
			else high = mid - 1;
		}
		return text.substring(0, codeSafe(text, low)) + "…";
	}

	private static int codeSafe(String text, int cut) {
		return cut > 0 && text.charAt(cut - 1) == '§' ? cut - 1 : cut;
	}

	private static String strip(String text) {
		return text.replaceAll("§.", "");
	}

	private FlowLayout place(String name, boolean on, Consumer<Boolean> set, String tooltip) {
		FlowLayout check = check(name, on, set);
		tip(check, tooltip);
		return check;
	}

	// pixels bc fonts ↶ is tiny
	private static ButtonComponent iconButton(boolean redo, Consumer<ButtonComponent> action) {
		ButtonComponent button = drawnButton(redo ? "Redo" : "Undo", action);
		button.horizontalSizing(Sizing.fixed(BUTTON + 4));
		button.renderer((graphics, b, delta) -> {
			ButtonComponent.Renderer.VANILLA.draw(graphics, b, delta);
			drawIcon(graphics, UNDO_ICON, b.getX() + (b.getWidth() - 8) / 2, b.getY() + (b.getHeight() - UNDO_ICON.length) / 2,
					redo, b.isActive() ? 0xFFFFFFFF : 0xFF707070);
		});
		return button;
	}

	private interface Icon {
		void draw(GuiGraphicsExtractor graphics, int x, int y, int height);
	}

	// no font glyph, caxton safe
	private static final Icon REMOVE = (graphics, x, y, h) -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, REMOVE_ICON, x,
			y + (h - 7) / 2, 8, 7);

	private static Icon pixels(String[] icon) {
		return (graphics, x, y, h) -> drawIcon(graphics, icon, x, y + (h - icon.length) / 2, false, 0xFFFFFFFF);
	}

	// 8 wide icon + text. text "" = icon only
	private ButtonComponent iconTextButton(Icon icon, String text, String name, Consumer<ButtonComponent> action) {
		ButtonComponent button = drawnButton(text.isEmpty() ? name : text, action);
		button.horizontalSizing(Sizing.fixed(text.isEmpty() ? CHOICE + 2 : 5 + 8 + 4 + font.width(text) + 5));
		button.renderer((graphics, b, delta) -> {
			ButtonComponent.Renderer.VANILLA.draw(graphics, b, delta);
			int x = text.isEmpty() ? b.getX() + (b.getWidth() - 8) / 2 : b.getX() + 5;
			icon.draw(graphics, x, b.getY(), b.getHeight());
			if (!text.isEmpty()) graphics.text(font, text, x + 12, b.getY() + (b.getHeight() - 8) / 2, 0xFFFFFFFF, true);
		});
		return button;
	}

	private static int fixedWidth(UIComponent c) {
		return c.horizontalSizing().get().value;
	}

	private static void drawIcon(GuiGraphicsExtractor graphics, String[] icon, int x0, int y0, boolean mirror, int color) {
		for (int row = 0; row < icon.length; row++) {
			for (int col = 0; col < 8; col++) {
				if (icon[row].charAt(col) != 'X') continue;
				int x = x0 + (mirror ? 7 - col : col);
				graphics.fill(x, y0 + row, x + 1, y0 + row + 1, color);
			}
		}
	}

	private static ButtonComponent button(String text, Consumer<ButtonComponent> action) {
		ButtonComponent button = UIComponents.button(Component.literal(text), action);
		button.horizontalSizing(Sizing.content(4));
		button.verticalSizing(Sizing.fixed(BUTTON));
		return button;
	}

	// owo draws the message too, so empty msg + narrator name
	private static ButtonComponent drawnButton(String name, Consumer<ButtonComponent> action) {
		ButtonComponent button = new DrawnButton(strip(name), action);
		button.horizontalSizing(Sizing.content(4));
		button.verticalSizing(Sizing.fixed(BUTTON));
		return button;
	}

	private static final class DrawnButton extends ButtonComponent {
		private final String name;

		DrawnButton(String name, Consumer<ButtonComponent> action) {
			super(Component.empty(), action);
			this.name = name;
		}

		@Override
		protected MutableComponent createNarrationMessage() {
			return wrapDefaultNarrationMessage(Component.literal(name));
		}
	}

	private static FlowLayout spacer() {
		return UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1));
	}

	private static LabelComponent section(String text) {
		LabelComponent label = label("§7" + text);
		label.margins(Insets.top(4));
		return label;
	}

	private static LabelComponent label(String text) {
		return new SafeLabel(Component.literal(text));
	}

	private static String name(Enum<?> option) {
		String lower = option.name().toLowerCase(java.util.Locale.ROOT);
		return option == Align.CENTER ? "Centre" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
