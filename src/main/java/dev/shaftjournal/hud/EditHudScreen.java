package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.shaftjournal.hud.HudLayout.Align;
import dev.shaftjournal.hud.HudLayout.Anchor;
import dev.shaftjournal.hud.HudLayout.Background;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.screen.SafeLabel;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * The Edit HUD screen from the approved mockup (Docs/mockups/hud-v1.html): drag a box to move it,
 * click it to change it in the side panel. Changes apply live and save by themselves; Ctrl+Z and
 * Ctrl+Shift+Z (or the arrow buttons) undo and redo them.
 */
public final class EditHudScreen extends BaseOwoScreen<FlowLayout> {
	private static final Surface PANEL = Surface.flat(0xE8141414).and(Surface.outline(0x30FFFFFF));
	private static final int PANEL_WIDTH = 204;
	private static final int ROW_LABEL = 62;
	private static final int COLOR_LABEL = 38;
	private static final int CONTROL = 12;
	private static final int SWATCH = 6;
	private static final int OUTLINE = 0x70FFFFFF;
	private static final int OUTLINE_SELECTED = 0xFFFFFF55;
	private static final int HIDDEN_SHADE = 0xC8000000;
	/** Snap guides: Minecraft's light purple, so they don't look like the yellow selection. */
	private static final int GUIDE = 0xFFFF55FF;
	/** The bar a dragged line will drop at, and the shade on the line being dragged. */
	private static final int DROP_LINE = 0xFFFFFF55;
	private static final int DRAGGED_LINE = 0x30FFFFFF;
	/** Six dots to grab a line by. Drawn as pixels, so it looks the same with any font. */
	private static final Surface GRIP = (graphics, c) -> {
		int x = c.x() + (c.width() - 3) / 2;
		int y = c.y() + (c.height() - 5) / 2;
		for (int dx = 0; dx <= 2; dx += 2) {
			for (int dy = 0; dy <= 4; dy += 2) graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, 0xFFA0A0A0);
		}
	};
	/**
	 * A change is an undo step (and saved) once nothing has changed for this long, so a burst of
	 * scroll notches or arrow-key nudges is one step, not twenty.
	 */
	private static final long SETTLE_MILLIS = 400;
	/** An arrow pointing left whose tail curls down: the usual undo icon. */
	private static final String[] UNDO_ICON = {
			"...X....",
			"..XX....",
			".XXXXXX.",
			"..XX...X",
			"...X...X",
			".......X",
			"....XXX."};
	private static final String CODES = "0123456789abcdef";
	/** How much one scroll notch over the selected box changes its size; the Size slider's step. */
	private static final float SCALE_STEP = 0.1f;
	/** Space around the screen's content, so the toolbar and panel don't touch the edge. */
	private static final int PADDING = 4;
	/** Where the toolbar may sit, as relative positions in order of preference: top middle, bottom middle, corners. */
	private static final int[][] TOOLBAR_SPOTS = {{50, 0}, {50, 100}, {0, 0}, {100, 0}, {0, 100}, {100, 100}};
	/** The 16 Minecraft text colours, in code order. */
	private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	private static final String[] COLOR_NAMES = {"Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red",
			"Dark Purple", "Gold", "Gray", "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White"};

	private final Screen parent;
	private final LiveHud hud;
	/** Where each box was drawn last frame, for clicks. */
	private final Map<Box, HudRenderer.Bounds> drawn = new IdentityHashMap<>();
	private FlowLayout root;
	private FlowLayout toolbar;
	private FlowLayout panel;
	/** The selected box's Size slider, kept in step when scrolling. */
	private DiscreteSliderComponent scaleSlider;
	private ButtonComponent resetButton;
	private ButtonComponent undoButton;
	private ButtonComponent redoButton;
	/** The selected box's title field; read every frame (see {@link #syncTitle}). */
	private TextBoxComponent titleField;
	/** Scroll the title field back to its start next frame, once the panel has its real size. */
	private boolean homeTitle;
	/** The box the side panel was built for. */
	private Box panelBox;
	/** The panel's line rows in order, and the parts of each row that start a drag (grip and name). */
	private final List<FlowLayout> lineRows = new ArrayList<>();
	private final List<List<UIComponent>> lineHandles = new ArrayList<>();
	/** The line being dragged, or -1, and the gap it would drop into (see {@link Box#moveLine}). */
	private int lineDrag = -1;
	private int lineGap;
	private boolean mouseHeld;
	private HudHistory history;
	/** A change not yet in the history, and when it was last seen changing. */
	private String unsettled;
	private long unsettledSince;
	/** Reset was clicked once; the next click resets, any other click cancels. */
	private boolean resetArmed;
	private int toolbarSpot = -1;
	private Box selected;
	private Box dragging;
	private double grabX;
	private double grabY;
	/** What the dragged box snapped to, drawn while dragging. */
	private List<HudSnap.Guide> guides = List.of();
	/** Scrolling not yet turned into a size step: touchpads send many small amounts per notch. */
	private double scrollLeft;
	/** The layout the selection belongs to; when the HUD swaps it (loaded late, Reset), the selection goes. */
	private HudLayout shownLayout;

	/** @param parent the screen to go back to, or null */
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
		LabelComponent title = label("§fEdit §bHUD");
		title.margins(Insets.of(0, 0, 2, 6));
		toolbar.child(title);
		undoButton = iconButton(false, b -> undo(false));
		undoButton.tooltip(Component.literal("Undo (Ctrl+Z)"));
		toolbar.child(undoButton);
		redoButton = iconButton(true, b -> undo(true));
		redoButton.tooltip(Component.literal("Redo (Ctrl+Shift+Z)"));
		toolbar.child(redoButton);
		ButtonComponent done = button("Done", b -> onClose());
		done.tooltip(Component.literal("Changes save by themselves"));
		toolbar.child(done);
		armReset(false);
		root.child(toolbar);
		rebuildPanel();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (hud.layout() != shownLayout) {
			// Only when the saved file loads after the editor opened; undo and Reset set shownLayout themselves.
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
		if (homeTitle && titleField != null) {
			homeTitle = false;
			titleField.moveCursorToEnd(false);
			titleField.moveCursorToStart(false);
		}
		syncTitle();
		recordChanges(false);
		undoButton.active(history.canUndo());
		redoButton.active(history.canRedo());
		drawn.clear();
		for (Box box : hud.layout().boxes) {
			List<String> lines = lines(box);
			HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines, width, height);
			drawn.put(box, bounds);
			HudRenderer.draw(graphics, font, box, lines, bounds);
			if (!hud.visible(box)) {
				graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), HIDDEN_SHADE);
				String hidden = "§7hidden here";
				graphics.text(font, hidden, bounds.x() + (bounds.width() - font.width(hidden)) / 2,
						bounds.y() + (bounds.height() - font.lineHeight) / 2, 0xFFFFFFFF, true);
			}
			graphics.outline(bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2,
					box == selected ? OUTLINE_SELECTED : OUTLINE);
		}
		for (HudSnap.Guide guide : guides) {
			graphics.fill(guide.x(), guide.y(), guide.x() + guide.width(), guide.y() + guide.height(), GUIDE);
			if (guide.label() != null) graphics.text(font, guide.label(), guide.labelX(), guide.labelY(), GUIDE, true);
		}
		if (selected == null) {
			// Short lines, so they fit at 1080p's auto GUI scale (480 wide) and smaller.
			String[] hint = {"§7Drag a box to move it · hold Ctrl to place it freely · click it to edit",
					"§7Scroll on the selected box to resize it · arrow keys nudge it",
					"§7Changes save by themselves · Ctrl+Z undo · Ctrl+Shift+Z redo"};
			for (int i = 0; i < hint.length; i++) {
				graphics.text(font, hint[i], (width - font.width(hint[i])) / 2, height - 64 + i * 11, 0xFFFFFFFF, true);
			}
		}
		placeToolbar();
		// Everything after this draws on top, so the panel covers the boxes, text included.
		graphics.nextStratum();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		if (lineDrag >= 0) {
			graphics.nextStratum();
			drawLineDrag(graphics);
		}
	}

	/** Shades the dragged line and draws a bar where it would drop. */
	private void drawLineDrag(GuiGraphicsExtractor graphics) {
		if (lineDrag >= lineRows.size()) return;
		FlowLayout from = lineRows.get(lineDrag);
		graphics.fill(from.x(), from.y(), from.x() + from.width(), from.y() + from.height(), DRAGGED_LINE);
		if (lineGap == lineDrag || lineGap == lineDrag + 1) return;
		FlowLayout last = lineRows.getLast();
		// Rows are 1 pixel apart; the bar covers that pixel and one on each side.
		int y = lineGap < lineRows.size() ? lineRows.get(lineGap).y() - 1 : last.y() + last.height();
		graphics.fill(from.x(), y - 1, from.x() + from.width(), y + 1, DROP_LINE);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		// Whatever came before this click (typing a title, scrolling) is its own undo step.
		syncTitle();
		recordChanges(true);
		mouseHeld = true;
		if (resetArmed && !resetButton.isInBoundingBox(click.x(), click.y())) armReset(false);
		int handle = click.button() == 0 ? lineHandleAt(click.x(), click.y()) : -1;
		if (handle >= 0) {
			root.focusHandler().focus(null, null);
			lineDrag = handle;
			lineGap = handle;
			return true;
		}
		if (overPanel(click.x(), click.y())) return super.mouseClicked(click, doubled);
		if (click.button() != 0) return false;
		root.focusHandler().focus(null, null);
		Box hit = null;
		// The last drawn box is on top.
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
			// Hidden while dragging, so it can't cover the box; it comes back on release.
			removePanel();
		} else {
			rebuildPanel();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent drag, double dx, double dy) {
		if (lineDrag >= 0) {
			// The gap above the first row whose middle is below the mouse.
			lineGap = 0;
			for (FlowLayout row : lineRows) {
				if (drag.y() > row.y() + row.height() / 2.0) lineGap++;
			}
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
		// Without a snap that says otherwise, a box is anchored to the third of the screen it's in.
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
		HudRenderer.Bounds bounds = selected == null ? null : drawn.get(selected);
		if (vertical == 0 || bounds == null || overPanel(mouseX, mouseY) || !bounds.contains(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		// One step per whole notch: a touchpad's many small amounts add up instead of each being a step.
		if (Math.signum(vertical) != Math.signum(scrollLeft)) scrollLeft = 0;
		scrollLeft += vertical;
		// The tiny nudge stops ten 0.1s (0.9999999 in floating point) from falling short of a notch.
		int steps = (int) (scrollLeft + Math.copySign(1e-6, scrollLeft));
		if (steps == 0) return true;
		scrollLeft -= steps;
		if (Math.abs(scrollLeft) < 1e-6) scrollLeft = 0;
		// Rounded to the slider's one decimal, so scrolling and the slider land on the same sizes.
		float scale = Math.round((selected.scale + steps * SCALE_STEP) * 10) / 10f;
		selected.scale = Math.max(HudLayout.MIN_SCALE, Math.min(HudLayout.MAX_SCALE, scale));
		if (scaleSlider != null) scaleSlider.setFromDiscreteValue(selected.scale);
		return true;
	}

	/** Arrow keys nudge the selected box by 1 pixel, or 10 with Shift, unless a text field is being typed in. */
	@Override
	public boolean keyPressed(KeyEvent key) {
		// Also while typing a title: the title field has no undo of its own.
		if (key.key() == GLFW.GLFW_KEY_Z && key.hasControlDownWithQuirk()) {
			undo(key.hasShiftDown());
			return true;
		}
		if (selected != null && !(root.focusHandler().focused() instanceof GreedyInputUIComponent)) {
			int step = key.hasShiftDown() ? 10 : 1;
			int dx = key.isLeft() ? -step : key.isRight() ? step : 0;
			int dy = key.isUp() ? -step : key.isDown() ? step : 0;
			if (dx != 0 || dy != 0) {
				// Measured now, not taken from the last frame, so key repeats faster than frames all count.
				// The anchor stays: nudging only moves the box.
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
		// The panel moves to the other side if the box crossed the middle.
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
		syncTitle();
		hud.save();
	}

	/** Swaps in the Reset button for this state; a new button, because owo doesn't re-measure a changed label. */
	private void armReset(boolean armed) {
		resetArmed = armed;
		if (resetButton != null) toolbar.removeChild(resetButton);
		resetButton = button(armed ? "§cReally reset?" : "Reset", b -> {
			if (!resetArmed) {
				armReset(true);
				return;
			}
			armReset(false);
			hud.setLayout(HudLayout.defaults());
			// Set here, so the swap isn't mistaken for the file loading late; Reset can be undone.
			shownLayout = hud.layout();
			selected = null;
			rebuildPanel();
		});
		resetButton.tooltip(Component.literal(armed ? "Click again to go back to the three default boxes"
				: "Back to the three default boxes"));
		toolbar.child(resetButton);
	}

	/**
	 * Copies the title field into the box. Polled, not an owo change event: Caxton (a font mod)
	 * cancels the text field's change call, so with it installed typed titles never arrived.
	 */
	private void syncTitle() {
		if (titleField != null && panelBox != null && !titleField.getValue().equals(panelBox.title)) {
			panelBox.title = titleField.getValue();
		}
	}

	/**
	 * Makes the layout's changes an undo step and saves them, once they've settled: not during a
	 * drag, while a mouse button is down (e.g. on the Size slider) or while typing a title.
	 *
	 * @param now record any change right away, e.g. before an undo
	 */
	private void recordChanges(boolean now) {
		String json = hud.layout().toJson();
		if (json.equals(history.current())) {
			unsettled = null;
			return;
		}
		if (!now) {
			if (dragging != null || lineDrag >= 0 || mouseHeld || titleFocused()) return;
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

	private boolean titleFocused() {
		return titleField != null && root.focusHandler().focused() == titleField;
	}

	/** Goes one step back, or forward with {@code redo}; the same box stays selected. */
	private void undo(boolean redo) {
		syncTitle();
		recordChanges(true);
		String json = redo ? history.redo() : history.undo();
		if (json == null) return;
		String selectedId = selected == null ? null : selected.id;
		HudLayout layout = HudLayout.fromJson(json);
		hud.setLayout(layout);
		shownLayout = layout;
		selected = null;
		for (Box box : layout.boxes) {
			if (box.id.equals(selectedId)) selected = box;
		}
		dragging = null;
		lineDrag = -1;
		guides = List.of();
		if (resetArmed) armReset(false);
		rebuildPanel();
		hud.save();
	}

	/** The box's lines as the editor shows them: example values, and a placeholder when it has none. */
	private List<String> lines(Box box) {
		List<String> lines = HudBoxText.lines(box, hud.data(), true);
		return lines.isEmpty() ? List.of("§8(empty)") : lines;
	}

	/**
	 * Moves the toolbar off any box (so every box stays clickable) and off the side panel: the first
	 * free spot in {@link #TOOLBAR_SPOTS}. When none is free, the spot that covers the least, never
	 * a whole box if it can help it. Not while dragging, so it doesn't jump around under the mouse,
	 * and not while Reset waits for its second click, so the button stays under the mouse.
	 */
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

	/**
	 * How bad it is to put the toolbar here: 0 when it covers nothing. Covering the side panel is
	 * worst, then hiding a whole box (it couldn't be clicked at all), then the pixels of boxes covered.
	 */
	private long coverCost(HudRenderer.Bounds area) {
		long cost = 0;
		for (HudRenderer.Bounds b : drawn.values()) {
			HudRenderer.Bounds outlined = b.grown(1);
			long covered = area.overlapArea(outlined);
			cost += covered;
			if (covered == (long) outlined.width() * outlined.height()) cost += 1_000_000;
		}
		if (panel != null && area.overlapArea(new HudRenderer.Bounds(panel.x(), panel.y(), panel.width(), panel.height())) > 0) {
			cost += 100_000_000;
		}
		return cost;
	}

	private boolean overPanel(double x, double y) {
		return toolbar.isInBoundingBox(x, y) || (panel != null && panel.isInBoundingBox(x, y));
	}

	// ---- side panel ----

	private void removePanel() {
		syncTitle();
		if (panel != null) root.removeChild(panel);
		panel = null;
		panelBox = null;
		scaleSlider = null;
		titleField = null;
		lineRows.clear();
		lineHandles.clear();
	}

	private void rebuildPanel() {
		removePanel();
		if (selected == null) return;
		Box box = selected;
		panelBox = box;
		panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
		panel.surface(PANEL);
		panel.padding(Insets.of(5));
		panel.gap(1);
		// Bottom corner on the side away from the box, so the box stays visible.
		// Measured now: after an undo the box is a new object that hasn't been drawn yet.
		HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines(box), width, height);
		boolean boxOnRight = bounds.x() + bounds.width() / 2 > width / 2;
		panel.positioning(Positioning.relative(boxOnRight ? 0 : 100, 100));

		SmallCheckboxComponent showTitle = UIComponents.smallCheckbox(Component.empty());
		showTitle.checked(box.showTitle);
		showTitle.onChanged().subscribe(checked -> box.showTitle = checked);
		showTitle.tooltip(Component.literal("Show the title"));
		TextBoxComponent title = UIComponents.textBox(Sizing.expand());
		title.verticalSizing(Sizing.fixed(CONTROL + 2));
		title.setMaxLength(64);
		title.text(box.title);
		titleField = title;
		// The text goes in while the field is still 0 wide, and with Caxton (a font mod) that can leave
		// the first letter scrolled out of view. Next frame, at the real width, it's scrolled back.
		homeTitle = true;
		title.tooltip(Component.literal("Box title. {shaft_type} shows the shaft's type."));
		FlowLayout titleRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		titleRow.gap(3);
		titleRow.verticalAlignment(VerticalAlignment.CENTER);
		titleRow.child(showTitle);
		titleRow.child(title);
		panel.child(titleRow);

		FlowLayout showIn = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		showIn.gap(6);
		showIn.child(place("Shafts", box.showInShaft, on -> box.showInShaft = on, "In a Glacite Mineshaft"));
		showIn.child(place("Mines", box.showInMines, on -> box.showInMines = on, "In the Dwarven Mines, outside a shaft"));
		showIn.child(place("Elsewhere", box.showElsewhere, on -> box.showElsewhere = on,
				"Anywhere else: the Hub, other islands, other servers"));

		FlowLayout background = row("Background");
		for (Background option : Background.values()) {
			background.child(choice(name(option), box.background == option, () -> box.background = option));
		}
		panel.child(background);

		DiscreteSliderComponent scale = UIComponents.discreteSlider(Sizing.expand(), HudLayout.MIN_SCALE, HudLayout.MAX_SCALE);
		scale.decimalPlaces(1);
		scale.setFromDiscreteValue(box.scale);
		scale.verticalSizing(Sizing.fixed(CONTROL));
		// One notch over the slider is one step too, as when scrolling on the box.
		scale.scrollStep(SCALE_STEP / (HudLayout.MAX_SCALE - HudLayout.MIN_SCALE));
		scale.message(text -> Component.literal(text + "×"));
		scale.onChanged().subscribe(value -> box.scale = (float) scale.discreteValue());
		scale.tooltip(Component.literal("Box size. You can also scroll on the box."));
		scaleSlider = scale;
		panel.child(row("Size", scale));

		FlowLayout align = row("Align");
		for (Align option : Align.values()) {
			align.child(choice(name(option), box.align == option, () -> box.align = option));
		}
		panel.child(align);

		panel.child(section("Show in"));
		panel.child(showIn);

		panel.child(section("Colours"));
		panel.child(colorRow("Labels", box.labelColor, code -> box.labelColor = code, box.labelBold, b -> box.labelBold = b));
		panel.child(colorRow("Values", box.valueColor, code -> box.valueColor = code, box.valueBold, b -> box.valueBold = b));

		panel.child(section("Lines"));
		for (int i = 0; i < box.lines.size(); i++) panel.child(lineRow(box, i));
		root.child(panel);
	}

	private FlowLayout lineRow(Box box, int index) {
		Line line = box.lines.get(index);
		HudValue value = line.hudValue();
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(2);
		row.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout grip = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(CONTROL));
		grip.surface(GRIP);
		grip.cursorStyle(CursorStyle.MOVE);
		grip.tooltip(Component.literal("Drag to move this line"));
		row.child(grip);
		SmallCheckboxComponent shown = UIComponents.smallCheckbox(Component.empty());
		shown.checked(line.shown);
		shown.onChanged().subscribe(checked -> line.shown = checked);
		shown.tooltip(Component.literal("Show this line"));
		row.child(shown);
		LabelComponent name = label("§f" + value.displayName());
		name.horizontalSizing(Sizing.expand());
		name.cursorStyle(CursorStyle.MOVE);
		row.child(name);
		lineRows.add(row);
		lineHandles.add(List.of(grip, name));
		List<String> formats = HudFormat.options(value.kind());
		if (formats.size() > 1) {
			Object current = hud.data().getOrExample(value);
			row.child(button(HudFormat.format(value.kind(), line.format, current), b -> {
				line.format = (line.format + 1) % formats.size();
				rebuildPanel();
			}).tooltip(Component.literal("Number format, click to change")));
		}
		return row;
	}

	/** The line whose grip or name is at this point, or -1. */
	private int lineHandleAt(double x, double y) {
		for (int i = 0; i < lineHandles.size(); i++) {
			for (UIComponent handle : lineHandles.get(i)) {
				if (handle.isInBoundingBox(x, y)) return i;
			}
		}
		return -1;
	}

	private FlowLayout colorRow(String name, String current, Consumer<String> setColor, boolean bold,
			Consumer<Boolean> setBold) {
		FlowLayout row = row(name);
		row.children().getFirst().horizontalSizing(Sizing.fixed(COLOR_LABEL));
		row.gap(1);
		FlowLayout swatches = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		swatches.gap(1);
		for (int i = 0; i < COLORS.length; i++) {
			String code = String.valueOf(CODES.charAt(i));
			FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(SWATCH), Sizing.fixed(SWATCH));
			swatch.surface(Surface.flat(0xFF000000 | COLORS[i])
					.and(Surface.outline(code.equals(current) ? 0xFFFFFFFF : 0xFF000000)));
			swatch.tooltip(Component.literal(COLOR_NAMES[i]));
			swatch.mouseDown().subscribe((click, doubled) -> {
				setColor.accept(code);
				rebuildPanel();
				return true;
			});
			swatches.child(swatch);
		}
		row.child(swatches);
		ButtonComponent boldButton = button((bold ? "§e" : "§f") + "§lB", b -> {
			setBold.accept(!bold);
			rebuildPanel();
		});
		boldButton.margins(Insets.left(2));
		boldButton.tooltip(Component.literal("Bold"));
		row.child(boldButton);
		return row;
	}

	// ---- small builders ----

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
		return button((on ? "§e" : "§f") + text, b -> {
			action.run();
			rebuildPanel();
		});
	}

	/** A "Show in" checkbox. */
	private static SmallCheckboxComponent place(String name, boolean on, Consumer<Boolean> set, String tooltip) {
		SmallCheckboxComponent checkbox = UIComponents.smallCheckbox(Component.literal(name));
		checkbox.checked(on);
		checkbox.onChanged().subscribe(set::accept);
		checkbox.tooltip(Component.literal(tooltip));
		return checkbox;
	}

	/** An undo arrow, or a redo arrow (the same, mirrored). Pixels, as the font's ↶ is tiny. */
	private static ButtonComponent iconButton(boolean redo, Consumer<ButtonComponent> action) {
		ButtonComponent button = button("", action);
		button.horizontalSizing(Sizing.fixed(CONTROL + 4));
		button.renderer((graphics, b, delta) -> {
			ButtonComponent.Renderer.VANILLA.draw(graphics, b, delta);
			int color = b.isActive() ? 0xFFFFFFFF : 0xFF707070;
			int x0 = b.getX() + (b.getWidth() - 8) / 2;
			int y0 = b.getY() + (b.getHeight() - UNDO_ICON.length) / 2;
			for (int row = 0; row < UNDO_ICON.length; row++) {
				for (int col = 0; col < 8; col++) {
					if (UNDO_ICON[row].charAt(col) != 'X') continue;
					int x = x0 + (redo ? 7 - col : col);
					graphics.fill(x, y0 + row, x + 1, y0 + row + 1, color);
				}
			}
		});
		return button;
	}

	private static ButtonComponent button(String text, Consumer<ButtonComponent> action) {
		ButtonComponent button = UIComponents.button(Component.literal(text), action);
		button.horizontalSizing(Sizing.content(4));
		button.verticalSizing(Sizing.fixed(CONTROL));
		return button;
	}

	private static LabelComponent section(String text) {
		LabelComponent label = label("§7" + text);
		label.margins(Insets.top(3));
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
