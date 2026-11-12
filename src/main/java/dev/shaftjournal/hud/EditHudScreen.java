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
	private static final Surface MENU = Surface.flat(0xF0101010).and(Surface.outline(0xFFA0A0A0));
	private static final Surface PREVIEW = Surface.flat(0xFF000000).and(Surface.outline(0x40FFFFFF));
	private static final int PANEL_WIDTH = 204;
	// panel minus its padding
	private static final int INNER = PANEL_WIDTH - 10;
	private static final int ROW_LABEL = 62;
	private static final int COLOR_LABEL = 38;
	private static final int CONTROL = 12;
	private static final int SWATCH = 6;
	private static final int MENU_SWATCH = 9;
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
	private static final String TEXT_HINT = "§8The buttons type values and colours for you. &7 is a colour, {spawn_timer} a value.";

	private final Screen parent;
	private final LiveHud hud;
	private final Map<Box, HudRenderer.Bounds> drawn = new IdentityHashMap<>();
	private FlowLayout root;
	private FlowLayout toolbar;
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
	private boolean resetArmed;
	private boolean deleteArmed;
	private ButtonComponent deleteButton;
	private FlowLayout deleteRow;
	// page state, reset when another box is picked
	private String pageBox;
	private boolean boxTab;
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
		newBox.tooltip(Component.literal("Add an empty box and pick its first line"));
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
			graphics.nextStratum();
			drawLineDrag(graphics);
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
		if (menu != null && key.key() == GLFW.GLFW_KEY_ESCAPE) {
			closeMenu();
			return true;
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
			toolbar.removeChild(resetButton);
		}
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
		pageBox = box.id;
		boxTab = false;
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
			deleteRow.removeChild(deleteButton);
		}
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
		return toolbar.isInBoundingBox(x, y) || (panelScroll != null && panelScroll.isInBoundingBox(x, y))
				|| (menu != null && menu.isInBoundingBox(x, y));
	}

	// owo cant size a scroll area by content, measured after layout
	private void fitPanel() {
		if (panelScroll == null || panel.height() <= 0) return;
		// no room beside it = leave the top for the toolbar
		boolean narrow = toolbar.width() + panelScroll.width() + 3 * PADDING > width;
		int reserve = narrow ? toolbar.height() + PADDING : 0;
		int fit = Math.min(panel.height(), height - 2 * PADDING - reserve);
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
		removePanel();
		if (selected == null) {
			pageBox = null;
			return;
		}
		Box box = selected;
		pageBox = box.id;
		panelBox = box;
		panelKey = box.id + "/" + (picking ? "picker" : editedIndex >= 0 ? "line" + editedIndex : boxTab ? "box" : "lines");
		panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
		panel.padding(Insets.of(5));
		panel.gap(1);
		panelScroll = new PanelScroll(panelHeight, panel);
		panelScroll.surface(PANEL);
		panelScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x80FFFFFF)));
		panelScroll.scrollbarThiccness(2);
		panelScroll.offset(scrollMemory.getOrDefault(panelKey, 0.0));
		// measured now, after undo the box wasnt drawn yet
		HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines(box), width, height);
		boolean boxOnRight = bounds.x() + bounds.width() / 2 > width / 2;
		panelScroll.positioning(Positioning.relative(boxOnRight ? 0 : 100, 100));
		if (picking) buildPicker(box);
		else if (editedIndex >= 0) buildLinePage(box, editedIndex);
		else if (boxTab) buildBoxTab(box);
		else buildLinesTab(box);
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
		boxTab = false;
		rebuildPanel();
	}

	private void tabs() {
		FlowLayout tabs = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		tabs.gap(2);
		tabs.margins(Insets.bottom(3));
		ButtonComponent lines = choice("Lines", !boxTab, () -> boxTab = false);
		// 2 expands = 1st takes all in owo
		lines.horizontalSizing(Sizing.fixed((INNER - 2) / 2));
		lines.tooltip(Component.literal("What the box shows"));
		ButtonComponent style = choice("Box", boxTab, () -> boxTab = true);
		style.horizontalSizing(Sizing.fixed((INNER - 2) / 2));
		style.tooltip(Component.literal("Title, background, size, where it shows, colours"));
		tabs.child(lines);
		tabs.child(style);
		panel.child(tabs);
	}

	private void buildLinesTab(Box box) {
		tabs();
		for (int i = 0; i < box.lines.size(); i++) panel.child(lineRow(box, i));
		if (box.lines.isEmpty()) panel.child(label("§7No lines yet."));
		ButtonComponent addLine = button("+ Add line", b -> {
			picking = true;
			rebuildPanel();
		});
		addLine.tooltip(Component.literal("Add any value to this box"));
		addLine.margins(Insets.top(3));
		panel.child(addLine);
	}

	private void buildBoxTab(Box box) {
		tabs();
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
		FlowLayout showIn = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		showIn.gap(6);
		showIn.child(place("Shafts", box.showInShaft, on -> box.showInShaft = on, "In a Glacite Mineshaft"));
		showIn.child(place("Mines", box.showInMines, on -> box.showInMines = on, "In the Dwarven Mines, outside a shaft"));
		showIn.child(place("Elsewhere", box.showElsewhere, on -> box.showElsewhere = on,
				"Anywhere else: the Hub, other islands, other servers"));
		panel.child(showIn);

		panel.child(section("Colours"));
		panel.child(colorRow("Labels", box.labelColor, code -> box.labelColor = code, box.labelBold, b -> box.labelBold = b));
		panel.child(colorRow("Values", box.valueColor, code -> box.valueColor = code, box.valueBold, b -> box.valueBold = b));
		LabelComponent themeHint = label("§8Lines with their own colour keep it.");
		themeHint.maxWidth(INNER);
		panel.child(themeHint);

		deleteRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		deleteRow.margins(Insets.top(4));
		panel.child(deleteRow);
		armDelete(false);
	}

	private void buildPicker(Box box) {
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(4);
		header.verticalAlignment(VerticalAlignment.CENTER);
		header.child(button("‹ Lines", b -> backToLines()).tooltip(Component.literal("Back without adding")));
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
					row.child(label("§8✔").tooltip(Component.literal("Already in this box. You can add it again, e.g. with another range.")));
				}
				row.tooltip(Component.literal("Add " + value.displayName()));
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
		FlowLayout grip = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(CONTROL));
		grip.surface(GRIP);
		grip.cursorStyle(CursorStyle.MOVE);
		grip.tooltip(Component.literal("Drag to move this line"));
		row.child(grip);
		SmallCheckboxComponent shown = UIComponents.smallCheckbox(Component.empty());
		shown.checked(line.shown);
		shown.onChanged().subscribe(checked -> {
			line.shown = checked;
			rebuildPanel();
		});
		shown.tooltip(Component.literal("Show this line"));
		row.child(shown);
		String text = HudBoxText.line(box, line, hud.data(), true);
		if (!line.shown) text = "§8" + strip(text);
		// grip, checkbox, ✎, gaps + slack so it never wraps
		LabelComponent look = label(clip(text, INNER - 60));
		look.horizontalSizing(Sizing.expand());
		look.tooltip(Component.literal("Click to change the label, range, format and colours"));
		row.child(look);
		ButtonComponent edit = button(line.advanced ? "§b✎" : "✎", b -> openLine(index));
		edit.tooltip(Component.literal(line.advanced ? "Change this line (it uses free text)" : "Change this line"));
		row.child(edit);
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
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(3);
		header.verticalAlignment(VerticalAlignment.CENTER);
		header.child(button("‹ Lines", b -> backToLines()));
		header.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
		ButtonComponent remove = button("§cRemove", b -> {
			box.lines.remove(line);
			editedIndex = -1;
			scrollToRow = Math.min(index, box.lines.size() - 1);
			rebuildPanel();
		});
		remove.tooltip(Component.literal("Remove this line. Ctrl+Z brings it back."));
		header.child(remove);
		panel.child(header);
		String range = value.ranges().size() > 1 && !line.advanced ? " §7· " + line.range.displayName() : "";
		LabelComponent title = label(clip("§f" + value.displayName() + range, INNER));
		title.margins(Insets.top(3));
		panel.child(title);

		FlowLayout look = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		look.surface(PREVIEW);
		look.padding(Insets.of(3));
		look.margins(Insets.vertical(2));
		preview = label("");
		previewLine = line;
		look.child(preview);
		panel.child(look);
		SmallCheckboxComponent shown = UIComponents.smallCheckbox(Component.literal("Show this line"));
		shown.checked(line.shown);
		shown.onChanged().subscribe(on -> line.shown = on);
		panel.child(shown);

		if (!line.advanced) {
			panel.child(section("Label"));
			TextBoxComponent prefix = textField(line.prefix, 64, typed -> line.prefix = typed);
			prefix.tooltip(Component.literal("The text before the value. Spaces count."));
			panel.child(prefix);
			panel.child(colorChoice(line.labelColor, box.labelColor, code -> line.labelColor = code));

			panel.child(section("Value"));
			List<HudValue.Range> ranges = value.ranges();
			if (ranges.size() > 1) {
				FlowLayout options = optionFlow();
				for (HudValue.Range r : ranges) {
					options.child(choice(r.displayName(), line.range == r, () -> line.range = r).tooltip(Component.literal(
							"Shaft: the one you're in, or the last one. Per hour: per hour of this session.")));
				}
				panel.child(options);
			}
			List<String> formats = HudFormat.options(value.kind());
			if (formats.size() > 1) {
				FlowLayout options = optionFlow();
				for (int i = 0; i < formats.size(); i++) {
					int format = i;
					options.child(choice(formats.get(i), line.format == i, () -> line.format = format)
							.tooltip(Component.literal("Number format")));
				}
				panel.child(options);
			}
			panel.child(colorChoice(line.valueColor, box.valueColor, code -> line.valueColor = code));
		}

		if (value.hasNumber()) {
			panel.child(section("Colour rules"));
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
					+ "Rules beat the value colour, the first one that matches wins."));
			add.margins(Insets.top(2));
			panel.child(add);
		}

		panel.child(section("Free text"));
		SmallCheckboxComponent free = UIComponents.smallCheckbox(Component.literal("Write this line yourself"));
		free.checked(line.advanced);
		free.onChanged().subscribe(on -> {
			if (on && (line.text == null || line.text.isBlank())) line.text = HudBoxText.basicText(box, line);
			line.advanced = on;
			rebuildPanel();
		});
		free.tooltip(Component.literal("For more than one value on a line, or a colour per word. "
				+ "Starts from how the line looks now. Untick to go back, your text is kept."));
		panel.child(free);
		if (line.advanced) freeText(box, line);
	}

	private void freeText(Box box, Line line) {
		FreeTextArea area = new FreeTextArea(Sizing.fill(100), Sizing.fixed(4 * font.lineHeight + 8));
		area.setCharacterLimit(256);
		area.text(line.text);
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
		addValue.tooltip(Component.literal("Type a value at the cursor"));
		insert.child(addValue);
		ButtonComponent codes = button("Colour ▾", this::codeMenu);
		codes.tooltip(Component.literal("Type a colour or style at the cursor"));
		insert.child(codes);
		insert.child(button("?", b -> {
		}).tooltip(tagHelp(line.hudValue())));
		panel.child(insert);

		freeHint = label(TEXT_HINT);
		freeHint.maxWidth(INNER);
		freeHintText = TEXT_HINT;
		panel.child(freeHint);
		panel.child(button("Start again from the line", b -> {
			line.text = HudBoxText.basicText(box, line);
			rebuildPanel();
		}).tooltip(Component.literal("Start the text again from the line's label, range, format and colours")));
	}

	// live, the text fields are polled
	private void refreshLinePage() {
		if (preview != null) {
			String text = HudBoxText.line(panelBox, previewLine, hud.data(), true);
			if (!text.equals(previewRaw)) {
				previewRaw = text;
				preview.text(Component.literal(clip(text, INNER - 8)));
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
			for (int half = 0; half < 2; half++) {
				FlowLayout swatches = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
				swatches.gap(1);
				swatches.margins(Insets.of(1, 1, 2, 2));
				for (int i = half * 8; i < half * 8 + 8; i++) {
					String code = String.valueOf(HudLayout.COLOR_CODES.charAt(i));
					FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(MENU_SWATCH), Sizing.fixed(MENU_SWATCH));
					swatch.surface(Surface.flat(0xFF000000 | COLORS[i]).and(Surface.outline(0xFF000000)));
					swatch.tooltip(Component.literal(COLOR_NAMES[i] + " (&" + code + ")"));
					swatch.cursorStyle(CursorStyle.HAND);
					swatch.mouseDown().subscribe((click, doubled) -> {
						insertText("&" + code);
						return true;
					});
					swatches.child(swatch);
				}
				m.child(swatches);
			}
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
		menu = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		menu.surface(MENU);
		menu.padding(Insets.of(2));
		// headings + gaps would pass clicks to the panel below
		menu.mouseDown().subscribe((click, doubled) -> true);
		menu.mouseScroll().subscribe((x, y, amount) -> true);
		menuAnchor = anchor;
		fill.accept(menu);
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
				+ "&m strikethrough, &r the box's label colour, &v the box's value colour, && and &{ a plain & and {.\n"
				+ "§7Values: §f{value_id}§7, "
				+ "options after a colon, e.g. §f{session_powder:all_time:short}§7.\n");
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
		if (panelScroll == null || !panelScroll.isInBoundingBox(x, y)) return -1;
		for (int i = 0; i < lineHandles.size(); i++) {
			for (UIComponent handle : lineHandles.get(i)) {
				if (handle.isInBoundingBox(x, y)) return i;
			}
		}
		return -1;
	}

	// "Box" = follow the box theme (null)
	private FlowLayout colorChoice(String current, String boxColor, Consumer<String> setColor) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(3);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.top(1));
		int box = HudLayout.COLOR_CODES.indexOf(boxColor.charAt(0));
		LabelComponent name = label("§7Colour");
		name.horizontalSizing(Sizing.fixed(COLOR_LABEL));
		row.child(name);
		ButtonComponent follow = choice("Box", current == null, () -> setColor.accept(null));
		follow.tooltip(Component.literal("Use the box's colour" + (box >= 0 ? " (" + COLOR_NAMES[box] + ")" : "")
				+ ". Changes when the box's colour does."));
		row.child(follow);
		row.child(swatches(current, setColor));
		return row;
	}

	private FlowLayout optionFlow() {
		FlowLayout flow = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		flow.gap(2);
		flow.margins(Insets.vertical(1));
		return flow;
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
