package dev.shaftjournal.hud;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import dev.shaftjournal.hud.HudLayout.Align;
import dev.shaftjournal.hud.HudLayout.Background;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.hud.HudLayout.Visibility;
import dev.shaftjournal.screen.SafeLabel;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Edit HUD screen from the approved mockup (Docs/mockups/hud-v1.html): drag a box to move it,
 * click it to change it in the side panel. Changes apply live and are saved when the screen closes.
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
	private static final String CODES = "0123456789abcdef";
	/** How much one scroll notch over the selected box changes its size; the Size slider's step. */
	private static final float SCALE_STEP = 0.1f;
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
	/** Reset was clicked once; the next click resets, any other click cancels. */
	private boolean resetArmed;
	private Box selected;
	private Box dragging;
	private double grabX;
	private double grabY;

	/** @param parent the screen to go back to, or null */
	public EditHudScreen(Screen parent, LiveHud hud) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
		this.hud = hud;
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
		root.padding(Insets.of(4));
		root.horizontalAlignment(HorizontalAlignment.CENTER);
		toolbar = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		toolbar.surface(PANEL);
		toolbar.padding(Insets.of(3));
		toolbar.gap(4);
		toolbar.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent title = label("§fEdit §bHUD");
		title.margins(Insets.of(0, 0, 2, 6));
		toolbar.child(title);
		toolbar.child(button("Done", b -> onClose()));
		armReset(false);
		root.child(toolbar);
		rebuildPanel();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		drawn.clear();
		for (Box box : hud.layout().boxes) {
			List<String> lines = HudBoxText.lines(box, hud.data(), true);
			if (lines.isEmpty()) lines = List.of("§8(empty)");
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
		if (selected == null) {
			String hint = "§7Drag a box to move it · click it to edit · scroll on it to resize · Esc to close";
			graphics.text(font, hint, (width - font.width(hint)) / 2, height - 60, 0xFFFFFFFF, true);
		}
		// Everything after this draws on top, so the panel covers the boxes, text included.
		graphics.nextStratum();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (resetArmed && !resetButton.isInBoundingBox(click.x(), click.y())) armReset(false);
		if (overPanel(click.x(), click.y())) return super.mouseClicked(click, doubled);
		if (click.button() != 0) return false;
		root.focusHandler().focus(null, null);
		Box hit = null;
		// The last drawn box is on top.
		for (Box box : hud.layout().boxes) {
			HudRenderer.Bounds bounds = drawn.get(box);
			if (bounds != null && bounds.contains(click.x(), click.y())) hit = box;
		}
		selected = hit;
		if (hit != null) {
			HudRenderer.Bounds bounds = drawn.get(hit);
			dragging = hit;
			grabX = click.x() - bounds.x();
			grabY = click.y() - bounds.y();
		}
		rebuildPanel();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent drag, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(drag, dx, dy);
		HudRenderer.Bounds bounds = drawn.get(dragging);
		if (bounds != null) {
			dragging.x = HudRenderer.fractionX((int) Math.round(drag.x() - grabX), bounds.width(), width);
			dragging.y = HudRenderer.fractionY((int) Math.round(drag.y() - grabY), bounds.height(), height);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		HudRenderer.Bounds bounds = selected == null ? null : drawn.get(selected);
		if (vertical == 0 || bounds == null || overPanel(mouseX, mouseY) || !bounds.contains(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		// Rounded to the slider's one decimal, so scrolling and the slider land on the same sizes.
		float scale = Math.round((selected.scale + Math.signum((float) vertical) * SCALE_STEP) * 10) / 10f;
		selected.scale = Math.max(HudLayout.MIN_SCALE, Math.min(HudLayout.MAX_SCALE, scale));
		if (scaleSlider != null) scaleSlider.setFromDiscreteValue(selected.scale);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent release) {
		if (dragging == null) return super.mouseReleased(release);
		dragging = null;
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
			hud.resetLayout();
			selected = null;
			rebuildPanel();
		});
		resetButton.tooltip(Component.literal(armed ? "Click again to go back to the three default boxes"
				: "Back to the three default boxes"));
		toolbar.child(resetButton);
	}

	private boolean overPanel(double x, double y) {
		return toolbar.isInBoundingBox(x, y) || (panel != null && panel.isInBoundingBox(x, y));
	}

	// ---- side panel ----

	private void rebuildPanel() {
		if (panel != null) root.removeChild(panel);
		panel = null;
		scaleSlider = null;
		if (selected == null) return;
		Box box = selected;
		panel = UIContainers.verticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content());
		panel.surface(PANEL);
		panel.padding(Insets.of(5));
		panel.gap(1);
		// Bottom corner on the side away from the box, so the box stays visible.
		HudRenderer.Bounds bounds = drawn.get(box);
		boolean boxOnRight = bounds != null && bounds.x() + bounds.width() / 2 > width / 2;
		panel.positioning(Positioning.relative(boxOnRight ? 0 : 100, 100));

		SmallCheckboxComponent showTitle = UIComponents.smallCheckbox(Component.empty());
		showTitle.checked(box.showTitle);
		showTitle.onChanged().subscribe(checked -> box.showTitle = checked);
		showTitle.tooltip(Component.literal("Show the title"));
		TextBoxComponent title = UIComponents.textBox(Sizing.expand(), box.title);
		title.verticalSizing(Sizing.fixed(CONTROL + 2));
		title.setMaxLength(64);
		title.onChanged().subscribe(text -> box.title = text);
		title.tooltip(Component.literal("Box title. {shaft_type} shows the shaft's type."));
		FlowLayout titleRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		titleRow.gap(3);
		titleRow.verticalAlignment(VerticalAlignment.CENTER);
		titleRow.child(showTitle);
		titleRow.child(title);
		panel.child(titleRow);

		panel.child(row("Visible", button(box.visibility.label, b -> {
			Visibility[] all = Visibility.values();
			box.visibility = all[(box.visibility.ordinal() + 1) % all.length];
			rebuildPanel();
		}).tooltip(Component.literal("Click to change when this box shows"))));

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
		SmallCheckboxComponent shown = UIComponents.smallCheckbox(Component.empty());
		shown.checked(line.shown);
		shown.onChanged().subscribe(checked -> line.shown = checked);
		shown.tooltip(Component.literal("Show this line"));
		row.child(shown);
		LabelComponent name = label("§f" + value.displayName());
		name.horizontalSizing(Sizing.expand());
		row.child(name);
		List<String> formats = HudFormat.options(value.kind());
		if (formats.size() > 1) {
			Object current = hud.data().getOrExample(value);
			row.child(button(HudFormat.format(value.kind(), line.format, current), b -> {
				line.format = (line.format + 1) % formats.size();
				rebuildPanel();
			}).tooltip(Component.literal("Number format, click to change")));
		}
		row.child(arrow("▲", index > 0, () -> swap(box, index, index - 1)));
		row.child(arrow("▼", index < box.lines.size() - 1, () -> swap(box, index, index + 1)));
		return row;
	}

	private void swap(Box box, int a, int b) {
		Line line = box.lines.get(a);
		box.lines.set(a, box.lines.get(b));
		box.lines.set(b, line);
		rebuildPanel();
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

	private ButtonComponent arrow(String text, boolean active, Runnable action) {
		ButtonComponent button = button(text, b -> action.run());
		button.horizontalSizing(Sizing.fixed(CONTROL));
		button.active(active);
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
