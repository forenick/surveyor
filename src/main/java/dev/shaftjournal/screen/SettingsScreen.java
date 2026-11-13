package dev.shaftjournal.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector2i;

import dev.shaftjournal.ShaftJournal;
import dev.shaftjournal.money.LiveMoney;
import dev.shaftjournal.money.PriceBook;
import dev.shaftjournal.settings.Setting;
import dev.shaftjournal.settings.Setting.Choice;
import dev.shaftjournal.settings.Setting.Option;
import dev.shaftjournal.settings.Settings;
import dev.shaftjournal.settings.SettingsList;
import dev.shaftjournal.settings.SettingsList.Category;
import dev.shaftjournal.settings.SettingsList.Section;

// mockup: docs/mockups/settings-v2.html. every change saves right away
public final class SettingsScreen extends BaseOwoScreen<FlowLayout> {
	private static final Surface PANEL = Surface.flat(0xE8141414).and(Surface.outline(0x30FFFFFF));
	private static final Surface CARD = Surface.flat(0x0CFFFFFF);
	private static final Surface CARD_HOVER = Surface.flat(0x16FFFFFF);
	private static final Surface ROW_HOVER = Surface.flat(0x18FFFFFF);
	private static final Surface ROW_SELECTED = Surface.flat(0x26FFFFFF);
	private static final int PADDING = 8;
	private static final int GAP = 5;
	private static final int BAR_PADDING = 4;
	private static final int BAR_GAP = 4;
	private static final int SIDE_PADDING = 3;
	private static final int MAIN_PADDING = 7;
	private static final int SCROLLBAR = 3;
	private static final int CARD_PADDING = 4;
	private static final int CARD_GAP = 6;
	private static final int BUTTON = 16;
	private static final int CHOICE_GAP = 2;
	private static final int FIELD = 44;
	// below this the control goes under the text
	private static final int MIN_TEXT = 140;
	private static final int TIP_WIDTH = 200;
	private static final int TIP_MARGIN = 5;
	private static final Identifier SEARCH_ICON = icon("search_icon");
	private static final Identifier RESET_ICON = icon("reset_icon");
	private static final Identifier CHECK_ICON = icon("check_icon");
	private static final Identifier CROSS_ICON = icon("cross_icon");
	private static final Map<String, Identifier> CATEGORY_ICONS = Map.of(SettingsList.MONEY, icon("money_icon"),
			SettingsList.JOURNAL, icon("journal_icon"));
	// owo's tooltips dont wrap or stay on screen
	private static final ClientTooltipPositioner FIT = (screenW, screenH, x, y, w, h) -> new Vector2i(
			clamp(x + 12 + w <= screenW - TIP_MARGIN ? x + 12 : x - 12 - w, w, screenW), clamp(y - 12, h, screenH));

	private final Screen parent;
	private Settings settings = current();
	private String query = "";
	private String category = SettingsList.MONEY;
	private FlowLayout root;
	private FlowLayout side;
	private FlowLayout main;
	private KeptScroll mainScroll;
	private TextBoxComponent search;
	private ButtonComponent resetCategory;
	private LabelComponent bazaarAge;
	private LabelComponent binsAge;
	private final List<Card> cards = new ArrayList<>();
	private final List<TextBoxComponent> homeFields = new ArrayList<>();
	private final Map<String, UIComponent> sectionHeads = new HashMap<>();
	private final Map<UIComponent, List<FormattedCharSequence>> tips = new IdentityHashMap<>();
	private int builtWidth;
	private int builtHeight;
	private int ticks;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Shaft Journal Settings"));
		this.parent = parent;
		ShaftJournal.MONEY.refresh();
	}

	private static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("shaftjournal", "textures/gui/" + name + ".png");
	}

	private static Settings current() {
		return new Settings(ShaftJournal.MONEY.settings(), ShaftJournal.MONEY.byDay());
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
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	protected void build(FlowLayout root) {
		this.root = root;
		root.surface(Surface.VANILLA_TRANSLUCENT);
		root.padding(Insets.of(PADDING));
		root.gap(GAP);
		rebuild();
	}

	// owo calls build() once, rebuild on resize
	@Override
	protected void init() {
		super.init();
		if (root != null && (width != builtWidth || height != builtHeight)) rebuild();
	}

	@Override
	public void tick() {
		super.tick();
		String typed = search.getValue();
		if (!typed.equals(query)) {
			query = typed;
			fillBody();
		}
		for (Card card : List.copyOf(cards)) card.poll();
		// hand edit picked up on the io thread
		Settings now = current();
		if (!now.equals(settings)) {
			settings = now;
			refreshCards();
		}
		if (++ticks % 10 == 0) updatePrices();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// caxton: text set at 0 width can scroll 1st letter out, rehome next frame
		for (TextBoxComponent field : homeFields) {
			field.moveCursorToEnd(false);
			field.moveCursorToStart(false);
		}
		homeFields.clear();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		drawTip(graphics, mouseX, mouseY);
	}

	private void rebuild() {
		builtWidth = width;
		builtHeight = height;
		root.clearChildren();
		root.child(topBar());
		FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
		body.gap(GAP);
		side = UIContainers.verticalFlow(Sizing.fixed(sideWidth()), Sizing.fill(100));
		side.surface(PANEL);
		side.padding(Insets.of(SIDE_PADDING));
		side.gap(1);
		main = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		main.gap(2);
		mainScroll = new KeptScroll(Sizing.expand(), Sizing.fill(100), main);
		mainScroll.surface(PANEL);
		mainScroll.padding(Insets.of(MAIN_PADDING));
		mainScroll.scrollbarThiccness(SCROLLBAR);
		body.child(side);
		body.child(mainScroll);
		root.child(body);
		FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		footer.gap(16);
		footer.child(label("§8Saved in config.json"));
		footer.child(label("§8Changes apply right away"));
		root.child(footer);
		fillBody();
	}

	private FlowLayout topBar() {
		FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(PANEL);
		bar.padding(Insets.of(BAR_PADDING));
		bar.gap(BAR_GAP);
		bar.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent title = label("§fShaft §bJournal §7Settings");
		title.margins(Insets.left(2));
		bar.child(title);
		bar.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
		bar.child(UIComponents.texture(SEARCH_ICON, 0, 0, 8, 8, 8, 8));
		String typed = search == null ? "" : search.getValue();
		search = UIComponents.textBox(Sizing.fixed(width <= 480 ? 100 : width <= 640 ? 130 : 160));
		search.verticalSizing(Sizing.fixed(BUTTON));
		search.setMaxLength(64);
		search.text(typed);
		search.setHint(Component.literal("Search settings").setStyle(EditBox.SEARCH_HINT_STYLE));
		homeFields.add(search);
		bar.child(search);
		ButtonComponent back = UIComponents.button(Component.literal("Back"), b -> onClose());
		// same as owo's content(6)
		back.horizontalSizing(Sizing.fixed(font.width("Back") + 20));
		back.verticalSizing(Sizing.fixed(BUTTON + 2));
		back.margins(Insets.left(2));
		bar.child(back);
		return bar;
	}

	private int sideWidth() {
		return width <= 480 ? 78 : width <= 640 ? 96 : 116;
	}

	// main panel minus padding + scrollbar
	private int mainInner() {
		return width - 2 * PADDING - sideWidth() - GAP - 2 * MAIN_PADDING - SCROLLBAR - 2;
	}

	private boolean searching() {
		return !query.isBlank();
	}

	private void fillBody() {
		cards.clear();
		tips.clear();
		sectionHeads.clear();
		resetCategory = null;
		bazaarAge = null;
		binsAge = null;
		side.clearChildren();
		main.clearChildren();
		fillSide();
		if (searching()) fillSearch();
		else fillCategory(category(category));
		refreshCards();
		updatePrices();
	}

	private static Category category(String id) {
		return SettingsList.CATEGORIES.stream().filter(c -> c.id().equals(id)).findFirst().orElse(SettingsList.CATEGORIES.getFirst());
	}

	private void fillSide() {
		for (Category c : SettingsList.CATEGORIES) {
			int hits = searching() ? c.matching(query).settings().size() : 0;
			boolean on = !searching() && c.id().equals(category);
			FlowLayout row = clickRow(16, on, () -> {
				category = c.id();
				query = "";
				search.setValue("");
				fillBody();
			});
			row.padding(Insets.horizontal(4));
			row.gap(4);
			row.child(UIComponents.texture(CATEGORY_ICONS.get(c.id()), 0, 0, 8, 8, 8, 8));
			LabelComponent name = label((on ? "§e" : searching() && hits == 0 ? "§8" : "§f") + c.name());
			name.horizontalSizing(Sizing.expand());
			row.child(name);
			if (hits > 0) row.child(label("§8" + hits));
			side.child(row);
			// section jumps, no room at 480
			if (!on || c.sections().size() < 2 || width <= 480) continue;
			for (Section section : c.sections()) {
				FlowLayout sub = clickRow(12, false, () -> {
					UIComponent head = sectionHeads.get(section.name());
					if (head != null) mainScroll.scrollTo(head);
				});
				sub.padding(Insets.left(16));
				sub.child(label("§7" + section.name()));
				side.child(sub);
			}
		}
	}

	private FlowLayout clickRow(int height, boolean selected, Runnable click) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(height));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.surface(selected ? ROW_SELECTED : Surface.BLANK);
		if (selected) return row;
		row.cursorStyle(CursorStyle.HAND);
		row.mouseEnter().subscribe(() -> row.surface(ROW_HOVER));
		row.mouseLeave().subscribe(() -> row.surface(Surface.BLANK));
		row.mouseDown().subscribe((event, doubleClick) -> {
			click.run();
			return true;
		});
		return row;
	}

	private void fillSearch() {
		boolean any = false;
		for (Category c : SettingsList.CATEGORIES) {
			Category hits = c.matching(query);
			if (hits.sections().isEmpty()) continue;
			LabelComponent head = label("§e" + c.name());
			if (any) head.margins(Insets.top(10));
			main.child(head);
			sections(hits);
			any = true;
		}
		if (!any) main.child(wrapped("§8No settings match \"" + query.strip() + "\""));
	}

	private void fillCategory(Category c) {
		main.child(label("§e" + c.name()));
		main.child(wrapped("§7" + c.description()));
		sections(c);
		if (c.id().equals(SettingsList.MONEY)) prices();
		resetCategory = UIComponents.button(Component.literal("Reset " + c.name() + " to defaults"), b -> apply(c.reset(settings)));
		resetCategory.horizontalSizing(Sizing.fixed(font.width("Reset " + c.name() + " to defaults") + 20));
		resetCategory.verticalSizing(Sizing.fixed(BUTTON));
		resetCategory.margins(Insets.top(8));
		main.child(resetCategory);
	}

	private void sections(Category c) {
		for (Section section : c.sections()) {
			main.child(sectionHead(section.name()));
			for (Setting setting : section.settings()) cards.add(new Card(setting));
		}
	}

	private FlowLayout sectionHead(String name) {
		FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		head.gap(4);
		head.verticalAlignment(VerticalAlignment.CENTER);
		head.margins(Insets.of(10, 1, 0, 0));
		head.child(label("§f" + name));
		head.child(UIComponents.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0x1CFFFFFF)).fill(true));
		sectionHeads.put(name, head);
		return head;
	}

	private void prices() {
		main.child(sectionHead("Prices"));
		FlowLayout box = card();
		FlowLayout ages = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		ages.gap(12);
		bazaarAge = label("");
		binsAge = label("");
		ages.child(bazaarAge);
		ages.child(binsAge);
		box.child(ages);
		LabelComponent note = label("§7Fetched when you enter a shaft or open the journal.");
		note.horizontalSizing(Sizing.fixed(mainInner() - 2 * CARD_PADDING));
		box.child(note);
		main.child(box);
	}

	private void updatePrices() {
		if (bazaarAge == null) return;
		PriceBook book = ShaftJournal.MONEY.book();
		long now = System.currentTimeMillis();
		bazaarAge.text(Component.literal("§7Bazaar " + age(book.bazaarAt(), LiveMoney.BAZAAR_MAX_AGE, now)));
		binsAge.text(Component.literal("§7Lowest BIN " + age(book.binsAt(), LiveMoney.BINS_MAX_AGE, now)));
	}

	// green = still fresh, gray = waits for the next shaft or journal open
	private static String age(long at, long maxAge, long now) {
		boolean fresh = at > 0 && now - at <= maxAge;
		if (!fresh && ShaftJournal.MONEY.refreshing()) return "§7fetching";
		if (at <= 0) return "§8never";
		long minutes = Math.max(0, now - at) / 60_000;
		String ago = minutes < 1 ? "just now" : minutes < 60 ? minutes + "m ago" : minutes < 24 * 60 ? minutes / 60 + "h ago"
				: minutes / (24 * 60) + "d ago";
		return (fresh ? "§a" : "§7") + ago;
	}

	private static FlowLayout card() {
		FlowLayout box = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		box.surface(CARD);
		box.padding(Insets.of(CARD_PADDING));
		box.gap(2);
		return box;
	}

	private void apply(Settings next) {
		if (next.equals(settings)) return;
		if (!next.money().equals(settings.money())) ShaftJournal.MONEY.setSettings(next.money());
		if (next.byDay() != settings.byDay()) ShaftJournal.MONEY.setByDay(next.byDay());
		settings = next;
		refreshCards();
	}

	private void refreshCards() {
		for (Card card : cards) card.refresh();
		if (resetCategory != null) resetCategory.active(!category(category).isDefault(settings));
	}

	private void tip(UIComponent c, String text) {
		tips.put(c, font.split(Component.literal(text), TIP_WIDTH));
	}

	// smallest hovered wins. removed parts keep their old bounds, so hasParent
	private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!mainScroll.isInBoundingBox(mouseX, mouseY)) return;
		List<FormattedCharSequence> lines = null;
		long smallest = Long.MAX_VALUE;
		for (Map.Entry<UIComponent, List<FormattedCharSequence>> tip : tips.entrySet()) {
			UIComponent c = tip.getKey();
			long area = (long) c.width() * c.height();
			if (area < smallest && c.hasParent() && c.isInBoundingBox(mouseX, mouseY)) {
				smallest = area;
				lines = tip.getValue();
			}
		}
		if (lines != null) graphics.setTooltipForNextFrame(font, lines, FIT, mouseX, mouseY, true);
	}

	private static int clamp(int pos, int size, int screen) {
		return Math.max(TIP_MARGIN, Math.min(pos, screen - TIP_MARGIN - size));
	}

	private static LabelComponent label(String text) {
		return new SafeLabel(Component.literal(text));
	}

	private static LabelComponent wrapped(String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fill(100));
		return label;
	}

	private final class Card {
		private final Setting setting;
		private final FlowLayout nameRow;
		private final FlowLayout extra;
		private final IconButton reset;
		private final List<ButtonComponent> buttons = new ArrayList<>();
		private IconButton toggle;
		private TextBoxComponent field;
		private String lastText;

		Card(Setting setting) {
			this.setting = setting;
			int content = mainInner() - 2 * CARD_PADDING;
			int controlWidth = controlWidth(content);
			boolean beside = content - controlWidth - CARD_GAP >= MIN_TEXT;
			int textWidth = beside ? content - controlWidth - CARD_GAP : content;

			FlowLayout box = beside ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
					: UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
			box.surface(CARD);
			box.padding(Insets.of(CARD_PADDING));
			box.gap(beside ? CARD_GAP : 3);
			box.verticalAlignment(VerticalAlignment.CENTER);
			box.mouseEnter().subscribe(() -> box.surface(CARD_HOVER));
			box.mouseLeave().subscribe(() -> box.surface(CARD));

			FlowLayout text = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
			text.gap(1);
			nameRow = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(12));
			nameRow.gap(3);
			nameRow.verticalAlignment(VerticalAlignment.CENTER);
			text.child(nameRow);
			LabelComponent description = label("§7" + setting.description());
			description.horizontalSizing(Sizing.fixed(textWidth));
			text.child(description);
			extra = UIContainers.verticalFlow(Sizing.fixed(textWidth), Sizing.content());
			text.child(extra);
			box.child(text);
			box.child(control(beside ? controlWidth : content, beside));
			main.child(box);

			reset = new IconButton(Component.empty(), RESET_ICON, 8, 8, b -> apply(setting.reset(settings)));
			reset.horizontalSizing(Sizing.fixed(12));
			reset.verticalSizing(Sizing.fixed(12));
			tip(reset, "§fReset to " + setting.defaultText());
		}

		private int controlWidth(int content) {
			return switch (setting) {
				case Choice<?> choice -> {
					int n = choice.options().size();
					int fair = ((width <= 640 ? 150 : 200) - CHOICE_GAP * (n - 1)) / n;
					yield n * buttonWidth(choice, fair) + CHOICE_GAP * (n - 1);
				}
				case Setting.Number number -> FIELD + 3 + font.width(number.unit());
				case Setting.Toggle toggle -> toggleWidth();
			};
		}

		private int buttonWidth(Choice<?> choice, int fair) {
			int widest = 0;
			for (Option<?> option : choice.options()) widest = Math.max(widest, font.width(option.label()));
			return Math.max(fair, widest + 12);
		}

		private int toggleWidth() {
			return Math.max(font.width("On"), font.width("Off")) + 8 + 3 + 12;
		}

		private FlowLayout control(int available, boolean beside) {
			FlowLayout control = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			control.gap(CHOICE_GAP);
			control.verticalAlignment(VerticalAlignment.CENTER);
			switch (setting) {
				case Choice<?> choice -> choices(control, choice, available, beside);
				case Setting.Number number -> {
					field = UIComponents.textBox(Sizing.fixed(FIELD));
					field.verticalSizing(Sizing.fixed(BUTTON));
					field.setMaxLength(12);
					lastText = Setting.Number.format(number.get().applyAsDouble(settings));
					field.text(lastText);
					homeFields.add(field);
					tip(field, "§8Default: " + number.defaultText());
					control.child(field);
					LabelComponent unit = label("§f" + number.unit());
					unit.margins(Insets.left(1));
					control.child(unit);
				}
				case Setting.Toggle t -> {
					toggle = new IconButton(Component.literal("§fOff"), CROSS_ICON, 8, 8, b -> apply(t.set().apply(settings, !t.get().test(settings))));
					toggle.horizontalSizing(Sizing.fixed(toggleWidth()));
					toggle.verticalSizing(Sizing.fixed(BUTTON));
					tip(toggle, "§8Default: " + t.defaultText());
					control.child(toggle);
				}
			}
			return control;
		}

		private <T> void choices(FlowLayout control, Choice<T> choice, int available, boolean beside) {
			int n = choice.options().size();
			int share = (available - CHOICE_GAP * (n - 1)) / n;
			int buttonWidth = beside ? share : Math.max(share, buttonWidth(choice, 0));
			for (Option<T> option : choice.options()) {
				ButtonComponent button = UIComponents.button(Component.literal(option.label()),
						b -> apply(choice.set().apply(settings, option.value())));
				button.horizontalSizing(Sizing.fixed(buttonWidth));
				button.verticalSizing(Sizing.fixed(BUTTON));
				tip(button, "§e" + option.label() + "\n§7" + option.tip() + (choice.isDefault(option) ? "\n§8Default" : ""));
				buttons.add(button);
				control.child(button);
			}
		}

		// polled bc caxton cancels onValueChange
		void poll() {
			if (!(setting instanceof Setting.Number number)) return;
			String typed = field.getValue();
			if (typed.equals(lastText)) return;
			lastText = typed;
			Double value = number.parse(typed);
			if (value != null) apply(number.set().apply(settings, value));
			refresh();
		}

		void refresh() {
			nameRow.clearChildren();
			nameRow.child(label("§f" + setting.name()));
			if (!setting.isDefault(settings)) nameRow.child(reset);
			extra.clearChildren();
			switch (setting) {
				case Choice<?> choice -> refreshChoice(choice);
				case Setting.Number number -> {
					double value = number.get().applyAsDouble(settings);
					Double typed = number.parse(field.getValue());
					// reset or hand edit, never while typing
					if (!field.isFocused() && (typed == null || typed != value)) {
						lastText = Setting.Number.format(value);
						field.setValue(lastText);
						typed = value;
					}
					if (typed == null) {
						LabelComponent error = label("§cNot a number from " + Setting.Number.format(number.min()) + " to "
								+ Setting.Number.format(number.max()) + ", still using " + Setting.Number.format(value) + number.unit());
						error.horizontalSizing(Sizing.fill(100));
						extra.child(error);
					}
				}
				case Setting.Toggle t -> {
					boolean on = t.get().test(settings);
					toggle.icon(on ? CHECK_ICON : CROSS_ICON);
					toggle.setMessage(Component.literal(on ? "§fOn" : "§fOff"));
				}
			}
		}

		private <T> void refreshChoice(Choice<T> choice) {
			T value = choice.get().apply(settings);
			for (int i = 0; i < buttons.size(); i++) {
				Option<T> option = choice.options().get(i);
				boolean selected = option.value().equals(value);
				ButtonComponent button = buttons.get(i);
				// inactive = pressed look, yellow = picked
				button.active(!selected);
				button.setMessage(Component.literal((selected ? "§e" : "§f") + option.label()));
			}
		}
	}
}
