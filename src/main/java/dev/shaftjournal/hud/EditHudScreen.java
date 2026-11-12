package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
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
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import dev.shaftjournal.hud.HudLayout.Align;
import dev.shaftjournal.hud.HudLayout.Anchor;
import dev.shaftjournal.hud.HudLayout.Background;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.screen.SafeLabel;

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
	private static final int GUIDE = 0xFFFF55FF;
	private static final int DROP_LINE = 0xFFFFFF55;
	private static final int DRAGGED_LINE = 0x30FFFFFF;
	private static final Surface GRIP = (graphics, c) -> {
		int x = c.x() + (c.width() - 3) / 2;
		int y = c.y() + (c.height() - 5) / 2;
		for (int dx = 0; dx <= 2; dx += 2) {
			for (int dy = 0; dy <= 4; dy += 2) graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, 0xFFA0A0A0);
		}
	};
	// batches scroll/nudge spam into 1 undo step
	private static final long SETTLE_MILLIS = 400;
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

	private static final Object MAIN = "main";
	private static final Object PICKER = "picker";
	private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	private static final String[] COLOR_NAMES = {"Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red",
			"Dark Purple", "Gold", "Gray", "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White"};

	private final Screen parent;
	private final LiveHud hud;
	private final Map<Box, HudRenderer.Bounds> drawn = new IdentityHashMap<>();
	private FlowLayout root;
	private FlowLayout toolbar;
	private FlowLayout panel;
	private PanelScroll panelScroll;
	// last frame, so a rebuild doesnt flicker
	private int panelHeight = 200;
	private Object panelKind;
	// by id bc undo makes new box objects
	private String lastPanelBox;
	private Object lastPanelKind;
	private double lastPanelOffset;
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
	private boolean resetArmed;
	private boolean deleteArmed;
	private ButtonComponent deleteButton;
	private FlowLayout deleteRow;
	private Box pickerBox;
	private Line editedLine;
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
		LabelComponent title = label("§fEdit §bHUD");
		title.margins(Insets.of(0, 0, 2, 6));
		toolbar.child(title);
		undoButton = iconButton(false, b -> undo(false));
		undoButton.tooltip(Component.literal("Undo (Ctrl+Z)"));
		toolbar.child(undoButton);
		redoButton = iconButton(true, b -> undo(true));
		redoButton.tooltip(Component.literal("Redo (Ctrl+Shift+Z)"));
		toolbar.child(redoButton);
		ButtonComponent newBox = button("+ New box", b -> newBox());
		newBox.tooltip(Component.literal("Add an empty box, then add lines to it"));
		toolbar.child(newBox);
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
		if (lineDrag >= 0) {
			graphics.nextStratum();
			drawLineDrag(graphics);
		}
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
		if (resetArmed && !resetButton.isInBoundingBox(click.x(), click.y())) armReset(false);
		if (deleteArmed && !deleteButton.isInBoundingBox(click.x(), click.y())) armDelete(false);
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
		if (lineDrag >= 0) {
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
		// also while typing, title field has no own undo
		if (key.key() == GLFW.GLFW_KEY_Z && key.hasControlDownWithQuirk()) {
			undo(key.hasShiftDown());
			return true;
		}
		if (selected != null && !(root.focusHandler().focused() instanceof GreedyInputUIComponent)) {
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
		if (resetButton != null) toolbar.removeChild(resetButton);
		resetButton = button(armed ? "§cReally reset?" : "Reset", b -> {
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
		resetButton.tooltip(Component.literal(armed ? "Click again to go back to the three default boxes"
				: "Back to the three default boxes"));
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
		if (deleteButton != null) deleteRow.removeChild(deleteButton);
		Box box = panelBox;
		deleteButton = button(armed ? "§cReally delete?" : "Delete box", b -> {
			if (!deleteArmed) {
				armDelete(true);
				return;
			}
			deleteArmed = false;
			hud.layout().boxes.remove(box);
			selected = null;
			rebuildPanel();
		});
		deleteButton.tooltip(Component.literal(armed ? "Click again to delete this box. Ctrl+Z brings it back."
				: "Delete this box. Ctrl+Z brings it back."));
		deleteRow.child(deleteButton);
	}

	private record Field(TextBoxComponent box, Consumer<String> apply) {
	}

	// polled bc caxton cancels onValueChange
	private void syncFields() {
		for (Field field : fields) field.apply().accept(field.box().getValue());
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

	private boolean typing() {
		return root.focusHandler().focused() instanceof TextBoxComponent;
	}

	private void undo(boolean redo) {
		syncFields();
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
		for (HudRenderer.Bounds b : drawn.values()) {
			HudRenderer.Bounds outlined = b.grown(1);
			long covered = area.overlapArea(outlined);
			cost += covered;
			if (covered == (long) outlined.width() * outlined.height()) cost += 1_000_000;
		}
		if (panelScroll != null && area.overlapArea(
				new HudRenderer.Bounds(panelScroll.x(), panelScroll.y(), panelScroll.width(), panelScroll.height())) > 0) {
			cost += 100_000_000;
		}
		return cost;
	}

	private boolean overPanel(double x, double y) {
		return toolbar.isInBoundingBox(x, y) || (panelScroll != null && panelScroll.isInBoundingBox(x, y));
	}

	// owo cant size a scroll area by content, measured after layout
	private void fitPanel() {
		if (panelScroll == null || panel.height() <= 0) return;
		int fit = Math.min(panel.height(), height - 2 * PADDING);
		if (fit == panelHeight && panelScroll.height() == fit) return;
		panelHeight = fit;
		panelScroll.verticalSizing(Sizing.fixed(fit));
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
			currentScrollPosition = Math.max(0, Math.min(currentScrollPosition, maxScroll + .5));
		}
	}

	private void removePanel() {
		syncFields();
		// owo keeps a removed field focused, eats keys
		if (fields.stream().anyMatch(field -> root.focusHandler().focused() == field.box())) {
			root.focusHandler().focus(null, null);
		}
		if (panelScroll != null) {
			lastPanelBox = panelBox.id;
			lastPanelKind = panelKind;
			lastPanelOffset = panelScroll.offset();
			root.removeChild(panelScroll);
		}
		panelScroll = null;
		panel = null;
		panelBox = null;
		scaleSlider = null;
		fields.clear();
		homeFields.clear();
		lineRows.clear();
		lineHandles.clear();
		deleteButton = null;
		deleteRow = null;
		deleteArmed = false;
	}

	private void rebuildPanel() {
		boolean picker = selected != null && pickerBox == selected;
		if (selected == null || picker || !selected.lines.contains(editedLine)) editedLine = null;
		Object kind = picker ? PICKER : editedLine != null ? editedLine : MAIN;
		removePanel();
		double offset = selected != null && selected.id.equals(lastPanelBox) && lastPanelKind == kind ? lastPanelOffset : 0;
		if (selected == null) {
			pickerBox = null;
			return;
		}
		Box box = selected;
		panelBox = box;
		panelKind = kind;
		panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
		panel.padding(Insets.of(5));
		panel.gap(1);
		panelScroll = new PanelScroll(panelHeight, panel);
		panelScroll.surface(PANEL);
		panelScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x80FFFFFF)));
		panelScroll.scrollbarThiccness(2);
		panelScroll.offset(offset);
		// measured now, after undo the box wasnt drawn yet
		HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines(box), width, height);
		boolean boxOnRight = bounds.x() + bounds.width() / 2 > width / 2;
		panelScroll.positioning(Positioning.relative(boxOnRight ? 0 : 100, 100));
		root.child(panelScroll);
		if (picker) {
			buildPicker(box);
			return;
		}
		pickerBox = null;
		if (editedLine != null) {
			buildLinePage(box, editedLine);
			return;
		}
		SmallCheckboxComponent showTitle = UIComponents.smallCheckbox(Component.empty());
		showTitle.checked(box.showTitle);
		showTitle.onChanged().subscribe(checked -> box.showTitle = checked);
		showTitle.tooltip(Component.literal("Show the title"));
		TextBoxComponent title = textField(box.title, 64, text -> box.title = text);
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
		deleteRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		deleteRow.gap(2);
		deleteRow.margins(Insets.top(3));
		ButtonComponent addLine = button("+ Add line", b -> {
			pickerBox = box;
			rebuildPanel();
		});
		addLine.tooltip(Component.literal("Add any value to this box"));
		deleteRow.child(addLine);
		FlowLayout spacer = UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1));
		deleteRow.child(spacer);
		panel.child(deleteRow);
		armDelete(false);
	}

	private void buildPicker(Box box) {
		panel.child(label("§fAdd a line"));
		for (HudValue value : HudValue.values()) {
			LabelComponent name = label("§f" + value.displayName());
			name.horizontalSizing(Sizing.expand());
			ButtonComponent add = button("Add", b -> {
				box.lines.add(new Line(value));
				pickerBox = null;
				rebuildPanel();
			});
			add.tooltip(Component.literal("Shows as: " + value.defaultPrefix()
					+ HudBoxText.text(value, value.defaultRange(), 0, hud.data(), true)));
			FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
			row.gap(2);
			row.verticalAlignment(VerticalAlignment.CENTER);
			row.child(name);
			row.child(add);
			panel.child(row);
		}
		ButtonComponent cancel = button("Cancel", b -> {
			pickerBox = null;
			rebuildPanel();
		});
		cancel.margins(Insets.top(3));
		panel.child(cancel);
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
		List<String> formats = HudFormat.options(value.kind());
		if (line.advanced) {
			name.text(Component.literal("§b" + value.displayName()));
		} else if (formats.size() > 1) {
			Object current = hud.data().getOrExample(value, line.range);
			row.child(button(HudFormat.format(value.kind(), line.format, current), b -> {
				line.format = (line.format + 1) % formats.size();
				rebuildPanel();
			}).tooltip(Component.literal("Number format, click to change")));
		}
		ButtonComponent edit = button("✎", b -> {
			editedLine = line;
			rebuildPanel();
		});
		edit.tooltip(Component.literal(line.advanced ? "Free text and colour rules (this line uses free text)"
				: "Free text and colour rules"));
		row.child(edit);
		ButtonComponent remove = button("§c×", b -> {
			box.lines.remove(line);
			rebuildPanel();
		});
		remove.tooltip(Component.literal("Remove this line"));
		row.child(remove);

		FlowLayout whole = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		whole.gap(1);
		whole.child(row);
		List<HudValue.Range> ranges = value.ranges();
		if (ranges.size() > 1 && !line.advanced) {
			FlowLayout rangeRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
			rangeRow.gap(2);
			rangeRow.verticalAlignment(VerticalAlignment.CENTER);
			// lines up w/ name above
			rangeRow.margins(Insets.left(7 + 2 + CONTROL - 1 + 2));
			rangeRow.child(label("§7Range"));
			rangeRow.child(button(line.range.displayName(), b -> {
				line.range = ranges.get((ranges.indexOf(line.range) + 1) % ranges.size());
				rebuildPanel();
			}).tooltip(Component.literal("Time range, click to change. Shaft: the one you're in, or the last one. "
					+ "Per hour: per hour of this session.")));
			whole.child(rangeRow);
		}
		lineRows.add(whole);
		lineHandles.add(List.of(grip, name));
		return whole;
	}

	private void buildLinePage(Box box, Line line) {
		HudValue value = line.hudValue();
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent title = label("§f" + value.displayName());
		title.horizontalSizing(Sizing.expand());
		header.child(title);
		header.child(button("Back", b -> {
			editedLine = null;
			rebuildPanel();
		}));
		panel.child(header);

		if (value.hasNumber()) {
			panel.child(section("Colours"));
			String ready = HudColors.readyRule(value);
			if (ready != null) {
				SmallCheckboxComponent rule = UIComponents.smallCheckbox(Component.literal(ready));
				rule.checked(line.readyRule);
				rule.onChanged().subscribe(on -> line.readyRule = on);
				rule.tooltip(Component.literal("A ready-made colour rule: " + ready + ". Your own rules below come first."));
				panel.child(rule);
			}
			List<HudLayout.ColorRule> rules = line.rules == null ? List.of() : line.rules;
			for (HudLayout.ColorRule rule : rules) ruleRows(line, rule);
			ButtonComponent add = button("+ Add colour rule", b -> {
				if (line.rules == null) line.rules = new ArrayList<>();
				HudLayout.ColorRule rule = new HudLayout.ColorRule();
				Double now = hud.data().number(value, HudBoxText.ruleRange(line), true);
				if (now != null) rule.value = value.kind() == HudValue.Kind.TIME ? Math.floor(now / 1000) : Math.round(now);
				line.rules.add(rule);
				rebuildPanel();
			});
			add.tooltip(Component.literal("Colour the value when it's over or under a number you pick. "
					+ "The first rule that matches wins."));
			add.margins(Insets.top(2));
			panel.child(add);
		}

		panel.child(section("Free text"));
		SmallCheckboxComponent free = UIComponents.smallCheckbox(Component.literal("Use free text for this line"));
		free.checked(line.advanced);
		free.onChanged().subscribe(on -> {
			if (on && (line.text == null || line.text.isBlank())) line.text = HudBoxText.basicText(box, line);
			line.advanced = on;
			rebuildPanel();
		});
		panel.child(free);
		if (line.advanced) {
			TextBoxComponent text = textField(line.text, 256, typed -> line.text = typed);
			text.tooltip(tagHelp(value));
			panel.child(text);
			LabelComponent hint = label("§8&7 is a colour, &l bold, {spawn_timer} a value. Hover the text for all of them.");
			hint.maxWidth(PANEL_WIDTH - 10);
			panel.child(hint);
			panel.child(button("Reset to basic text", b -> {
				line.text = HudBoxText.basicText(box, line);
				rebuildPanel();
			}).tooltip(Component.literal("Start the text again from the line's basic look")));
		}
	}

	private void ruleRows(Line line, HudLayout.ColorRule rule) {
		HudValue.Kind kind = line.hudValue().kind();
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(2);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.top(2));
		row.child(button(rule.when == HudLayout.ColorRule.When.OVER ? "Over" : "Under", b -> {
			rule.when = rule.when == HudLayout.ColorRule.When.OVER ? HudLayout.ColorRule.When.UNDER : HudLayout.ColorRule.When.OVER;
			rebuildPanel();
		}).tooltip(Component.literal("Click to switch between over and under")));
		TextBoxComponent number = textField(HudFormat.thresholdText(kind, rule.value), 12, typed -> {
			Double parsed = HudFormat.parseThreshold(kind, typed);
			if (parsed != null) rule.value = parsed;
		});
		number.tooltip(Component.literal(kind == HudValue.Kind.TIME ? "A time, like 1:30 or 90 (seconds)" : "A number"));
		row.child(number);
		ButtonComponent remove = button("§c×", b -> {
			line.rules.remove(rule);
			if (line.rules.isEmpty()) line.rules = null;
			rebuildPanel();
		});
		remove.tooltip(Component.literal("Remove this rule"));
		row.child(remove);
		panel.child(row);
		FlowLayout swatches = swatches(rule.color, code -> rule.color = code);
		swatches.margins(Insets.left(2));
		panel.child(swatches);
	}

	private static Component tagHelp(HudValue own) {
		StringBuilder help = new StringBuilder("§fFree text\n§7&0-&f colours, &l bold, &o italic, &n underline, "
				+ "&m strikethrough, &r back to the label colour.\n§7Values: §f{value_id}§7, options after a colon, "
				+ "e.g. §f{session_powder:all_time:short}§7.\n");
		for (HudValue value : HudValue.values()) {
			help.append(value == own ? "\n§e" : "\n§f").append('{').append(value.id()).append('}');
			List<String> options = new ArrayList<>();
			for (HudValue.Range range : value.ranges()) options.add(range.tagName());
			options.addAll(HudFormat.names(value.kind()));
			if (!options.isEmpty()) help.append(" §8").append(String.join(", ", options));
		}
		return Component.literal(help.toString());
	}

	private TextBoxComponent textField(String text, int maxLength, Consumer<String> apply) {
		TextBoxComponent field = UIComponents.textBox(Sizing.expand());
		field.verticalSizing(Sizing.fixed(CONTROL + 2));
		field.setMaxLength(maxLength);
		field.text(text);
		String[] last = {text};
		fields.add(new Field(field, typed -> {
			if (typed.equals(last[0])) return;
			last[0] = typed;
			apply.accept(typed);
		}));
		// caxton: text set at 0 width can scroll 1st letter out, rehome next frame
		homeFields.add(field);
		return field;
	}

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
		row.child(swatches(current, setColor));
		ButtonComponent boldButton = button((bold ? "§e" : "§f") + "§lB", b -> {
			setBold.accept(!bold);
			rebuildPanel();
		});
		boldButton.margins(Insets.left(2));
		boldButton.tooltip(Component.literal("Bold"));
		row.child(boldButton);
		return row;
	}

	private FlowLayout swatches(String current, Consumer<String> setColor) {
		FlowLayout swatches = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		swatches.gap(1);
		for (int i = 0; i < COLORS.length; i++) {
			String code = String.valueOf(HudLayout.COLOR_CODES.charAt(i));
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
		return swatches;
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
		return button((on ? "§e" : "§f") + text, b -> {
			action.run();
			rebuildPanel();
		});
	}

	private static SmallCheckboxComponent place(String name, boolean on, Consumer<Boolean> set, String tooltip) {
		SmallCheckboxComponent checkbox = UIComponents.smallCheckbox(Component.literal(name));
		checkbox.checked(on);
		checkbox.onChanged().subscribe(set::accept);
		checkbox.tooltip(Component.literal(tooltip));
		return checkbox;
	}

	// pixels bc fonts ↶ is tiny
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
