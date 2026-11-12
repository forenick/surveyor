package dev.shaftjournal.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.shaftjournal.ShaftJournal;
import dev.shaftjournal.detection.CorpseLoot;
import dev.shaftjournal.detection.ItemCount;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.hud.EditHudScreen;
import dev.shaftjournal.journal.Journal;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.JournalStats;
import dev.shaftjournal.journal.JournalStats.AllTime;
import dev.shaftjournal.journal.JournalStats.CorpseCount;
import dev.shaftjournal.journal.JournalStats.SessionSummary;
import dev.shaftjournal.journal.LootSummary;
import dev.shaftjournal.journal.SummaryText;
import dev.shaftjournal.money.Coins;
import dev.shaftjournal.money.MoneySettings;
import dev.shaftjournal.money.MoneyStats;
import dev.shaftjournal.money.ShaftValue;
import dev.shaftjournal.money.ShaftValue.Line;
import dev.shaftjournal.money.ShaftValue.Part;

// mc font only so pack fonts work
public final class JournalScreen extends BaseOwoScreen<FlowLayout> {
	private enum Tab { OVERVIEW, SESSION, STATS }

	// higher dropOrder hides first, 0 = never. extra = spare room handed out by fit()
	private record Column(String key, String title, int width, boolean right, int dropOrder, int extra) {
		Column(String key, String title, int width, boolean right, int dropOrder) {
			this(key, title, width, right, dropOrder, 0);
		}

		int total() {
			return width + extra;
		}
	}

	private static final Surface PANEL = Surface.flat(0xE8141414).and(Surface.outline(0x30FFFFFF));
	private static final Surface ROW_EVEN = Surface.flat(0x0DFFFFFF);
	private static final Surface ROW_DAY = Surface.flat(0x1CFFFFFF);
	private static final Surface ROW_HOVER = Surface.flat(0x1AFFFFFF);
	private static final Surface ROW_SELECTED = Surface.flat(0x26FFFFFF).and(Surface.outline(0x50FFFFFF));
	private static final Surface SLOT = Surface.flat(0xFF8B8B8B).and(Surface.outline(0xFF373737));
	private static final Color GREEN = Color.ofArgb(0xCC55FF55);
	private static final int ROW_HEIGHT = 12;
	private static final int SPAWN_BAR = 26;
	private static final int PADDING = 8;
	private static final int GAP = 5;
	private static final int COLUMN_PADDING = 6;
	// in %, middle gets the rest
	private static final int STATS_LEFT = 32;
	private static final int STATS_RIGHT = 27;
	private static final int AXIS_GAP = 3;

	// width 0 = measured per table, see measured()
	private static final List<Column> SHAFT_COLUMNS = List.of(
			new Column("number", "#", 14, false, 5), new Column("shaft", "Shaft", 44, false, 0),
			new Column("time", "Time", 32, false, 3), new Column("spawn", "Spawn", 66, false, 0),
			new Column("inside", "Inside", 0, true, 2), new Column("corpses", "Corpses", 0, false, 0),
			new Column("powder", "Powder", 44, true, 1), new Column("gems", "Gems", 40, true, 4),
			new Column("coins", "Coins", 0, true, 0));
	// by day: start time goes under the days date, no start column
	private static final List<Column> SESSION_COLUMNS = List.of(
			new Column("date", "Date", 56, false, 0), new Column("start", "Start", 32, false, 5),
			new Column("length", "Length", 44, true, 4), new Column("shafts", "Shafts", 36, true, 0),
			new Column("spawn", "Avg spawn", 78, false, 0), new Column("rate", "Per hour", 46, true, 3),
			new Column("powder", "Powder", 58, true, 0), new Column("lapis", "Lapis", 36, true, 6),
			new Column("coins", "Coins", 0, true, 0), new Column("coinRate", "Coins / h", 0, true, 2));

	private final List<JournalEntry> entries;
	private final List<SessionSummary> sessions;
	private final ItemIcons icons = new ItemIcons();
	private final ShaftNames shaftNames = ShaftNames.defaults();
	private Tab tab;
	// an older session opened from overview or stats, tab stays where it came from
	private boolean viewing;
	private final boolean current;
	private final boolean started;
	private boolean byDay = ShaftJournal.MONEY.byDay();
	private int sessionIndex;
	private int selected;
	private FlowLayout root;
	private int builtWidth;
	private int builtHeight;
	private FlowLayout detail;
	private final List<FlowLayout> shaftRows = new ArrayList<>();
	private int moneyChanges = ShaftJournal.MONEY.changes();

	public JournalScreen(List<JournalEntry> entries) {
		super(Component.literal("Shaft Journal"));
		this.entries = List.copyOf(entries);
		this.sessions = JournalStats.sessions(this.entries);
		selectLastShaft();
		// session tab = only the one still going
		Journal.Session active = ShaftJournal.JOURNAL.activeSession(System.currentTimeMillis());
		current = active != null && !sessions.isEmpty() && sessions.getFirst().id() == active.id();
		started = active != null && !current;
		tab = current ? Tab.SESSION : Tab.OVERVIEW;
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	// prices + settings load on the io thread after the journal opens
	@Override
	public void tick() {
		super.tick();
		int changes = ShaftJournal.MONEY.changes();
		if (changes == moneyChanges) return;
		moneyChanges = changes;
		rebuild();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
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

	private void rebuild() {
		builtWidth = width;
		builtHeight = height;
		root.clearChildren();
		shaftRows.clear();
		detail = null;
		root.child(topBar());
		FlowLayout content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
		content.gap(GAP);
		if (entries.isEmpty()) {
			content.child(panelWith(label("§7No shafts yet. Each shaft shows up here after you leave it."),
					Sizing.fill(100), Sizing.content()));
		} else {
			if (viewing) buildSessionTab(content);
			else switch (tab) {
				case OVERVIEW -> buildOverviewTab(content);
				case SESSION -> {
					if (current) buildSessionTab(content);
					else noSession(content);
				}
				case STATS -> buildStatsTab(content);
			}
		}
		root.child(content);
		FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		footer.gap(16);
		if (viewing) {
			footer.child(label("§8Click a shaft for details"));
			footer.child(label("§8< > other sessions"));
		} else switch (tab) {
			case SESSION -> {
				if (current) footer.child(label("§8Click a shaft for details"));
			}
			case OVERVIEW -> footer.child(label("§8Click a session to open it"));
			case STATS -> {
				footer.child(label("§8Counted from your own " + entries.size() + " shafts"));
				footer.child(label("§8Hover a row for details"));
				footer.child(label("§8Click an arrow to open it"));
			}
		}
		root.child(footer);
	}

	private FlowLayout topBar() {
		FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(PANEL);
		bar.padding(Insets.of(4));
		bar.gap(4);
		bar.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent title = label("§fShaft §bJournal");
		title.margins(Insets.of(0, 0, 2, 8));
		bar.child(title);
		bar.child(tabButton("Overview", Tab.OVERVIEW));
		bar.child(tabButton("Session", Tab.SESSION));
		bar.child(tabButton("Stats", Tab.STATS));

		FlowLayout nav = UIContainers.horizontalFlow(Sizing.expand(), Sizing.content());
		nav.horizontalAlignment(HorizontalAlignment.RIGHT);
		nav.verticalAlignment(VerticalAlignment.CENTER);
		nav.gap(4);
		if (viewing) {
			SessionSummary shown = sessions.get(sessionIndex);
			// 480 wide: no "<", so the date still fits
			String backText = navRoom("< Back") >= font.width(day(shown.start())) ? "< Back" : "Back";
			ButtonComponent back = UIComponents.button(Component.literal(backText), b -> {
				viewing = false;
				rebuild();
			});
			back.horizontalSizing(Sizing.fixed(font.width(backText) + 12));
			back.margins(Insets.right(4));
			nav.child(back);
			nav.child(button("<", sessionIndex < sessions.size() - 1, () -> showSession(sessionIndex + 1)));
			nav.child(label("§f" + sessionRange(shown, navRoom(backText))));
			nav.child(button(">", sessionIndex > 0, () -> showSession(sessionIndex - 1)));
		} else if (tab == Tab.SESSION && current) {
			nav.child(label("§f" + sessionRange(sessions.getFirst(), navRoom(null))));
		} else if (tab == Tab.OVERVIEW && !sessions.isEmpty()) {
			nav.child(new IconButton(Component.literal("§fBy day"), byDay ? CHECK_ICON : CROSS_ICON, 8, 8, b -> {
				byDay = !byDay;
				ShaftJournal.MONEY.setByDay(byDay);
				rebuild();
			}));
		}
		bar.child(nav);
		ButtonComponent editHud = UIComponents.button(Component.literal("Edit HUD"),
				b -> minecraft.gui.setScreen(new EditHudScreen(this, ShaftJournal.HUD)));
		editHud.horizontalSizing(Sizing.content(6));
		editHud.margins(Insets.left(6));
		bar.child(editHud);
		return bar;
	}

	private ButtonComponent tabButton(String name, Tab target) {
		Component text = Component.literal((tab == target ? "§e" : "§f") + name);
		Identifier icon = switch (target) {
			case OVERVIEW -> OVERVIEW_ICON;
			case SESSION -> SESSION_ICON;
			case STATS -> STATS_ICON;
		};
		return new IconButton(text, icon, 9, 8, b -> {
			viewing = false;
			tab = target;
			if (target == Tab.SESSION) {
				sessionIndex = 0;
				selectLastShaft();
			}
			rebuild();
		});
	}

	private void noSession(FlowLayout content) {
		FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		panel.gap(4);
		if (started) {
			panel.child(wrapped("§7Your session has started."));
			panel.child(wrapped("§8Each shaft shows up here after you leave it."));
		} else {
			panel.child(wrapped("§7No session going right now."));
			panel.child(wrapped("§8A session starts with your next shaft and ends after " + Journal.SESSION_GAP_MILLIS / 60_000
					+ " minutes without one. Older sessions are in the Overview."));
		}
		content.child(panelWith(panel, Sizing.fill(100), Sizing.content()));
	}

	private ButtonComponent button(String text, boolean active, Runnable action) {
		ButtonComponent button = UIComponents.button(Component.literal(text), b -> action.run());
		button.active(active);
		button.horizontalSizing(Sizing.fixed(16));
		return button;
	}

	private void showSession(int index) {
		sessionIndex = index;
		selectLastShaft();
		viewing = true;
		rebuild();
	}

	private void openShaft(JournalEntry entry) {
		for (int i = 0; i < sessions.size(); i++) {
			int index = sessions.get(i).entries().indexOf(entry);
			if (index >= 0) {
				sessionIndex = i;
				selected = index;
				viewing = true;
				rebuild();
				return;
			}
		}
	}

	private void openSession(SessionSummary session) {
		int index = sessions.indexOf(session);
		if (index >= 0) showSession(index);
	}

	private void selectLastShaft() {
		selected = sessions.isEmpty() ? -1 : sessions.get(sessionIndex).shafts() - 1;
	}

	private void buildSessionTab(FlowLayout content) {
		SessionSummary session = sessions.get(sessionIndex);
		Double rate = session.shaftsPerHour();
		Double sessionCoins = MoneyStats.coins(session.entries(), ShaftJournal.MONEY::value);
		FlowLayout tiles = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		tiles.gap(GAP);
		int tileWidth = (width - 2 * PADDING - 5 * GAP) / 6;
		tiles.child(tile(tileWidth, "Shafts", "§f" + session.shafts()));
		tiles.child(tile(tileWidth, "Avg spawn", spawn(session.averageSearchMillis())));
		tiles.child(tile(tileWidth, "Per hour", rate == null ? "§8-" : "§f" + decimal(rate)));
		tiles.child(tile(tileWidth, "Powder", "§b" + number(session.powder())));
		tiles.child(tile(tileWidth, "Lapis looted", "§9" + session.looted("Lapis")));
		FlowLayout coinsTile = tile(tileWidth, "Coins", coinText(sessionCoins));
		if (sessionCoins != null) {
			Double perHour = session.perHour(sessionCoins, session.end());
			withTooltip(coinsTile, List.of(Component.literal("§fCoins this session"),
					Component.literal("§7Per shaft " + coins(sessionCoins / session.shafts())),
					Component.literal("§7Per hour " + (perHour == null ? "§8after 5 minutes" : coins(perHour)))));
		}
		tiles.child(coinsTile);
		content.child(tiles);

		FlowLayout main = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
		main.gap(GAP);

		long slowest = session.entries().stream().filter(e -> e.searchMillis() != null)
				.mapToLong(JournalEntry::searchMillis).max().orElse(1);
		int detailWidth = Math.clamp(width * 30 / 100, 140, 230);
		List<Double> shaftCoins = session.entries().stream().map(e -> MoneyStats.net(ShaftJournal.MONEY.value(e))).toList();
		List<Column> measured = measured(SHAFT_COLUMNS, "inside",
				session.entries().stream().map(e -> SummaryText.duration(e.durationMillis())).toList());
		measured = measured(measured, "corpses", session.entries().stream().map(JournalScreen::corpses).toList());
		measured = measured(measured, "coins", shaftCoins.stream().map(JournalScreen::coinText).toList());
		List<Column> columns = fit(measured, width - 2 * PADDING - GAP - detailWidth - TABLE_CHROME);
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		for (int i = 0; i < session.entries().size(); i++) {
			JournalEntry entry = session.entries().get(i);
			FlowLayout row = row(i);
			for (Column column : columns) {
				row.child(switch (column.key()) {
					case "number" -> cell(column, "§8" + (i + 1));
					case "shaft" -> cell(column, "§b" + (entry.shaftId() == null ? "?" : entry.shaftId()));
					case "time" -> cell(column, "§7" + clock(entry.enteredAt()));
					case "spawn" -> barCell(column, entry.searchMillis(), slowest);
					case "inside" -> cell(column, "§f" + SummaryText.duration(entry.durationMillis()));
					case "corpses" -> cell(column, corpses(entry));
					case "powder" -> cell(column, "§f" + number(JournalStats.powder(entry)));
					case "coins" -> cell(column, coinText(shaftCoins.get(i)));
					default -> cell(column, entry.gems() == null ? "§8-" : "§f≈" + decimal(entry.gems().fineEquivalent()));
				});
			}
			int index = i;
			row.mouseDown().subscribe((event, doubleClick) -> {
				select(index);
				return true;
			});
			shaftRows.add(row);
			rows.child(row);
		}
		main.child(table(columns, rows));

		detail = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		detail.gap(2);
		ScrollContainer<FlowLayout> detailScroll = UIContainers.verticalScroll(Sizing.fixed(detailWidth), Sizing.fill(100), detail);
		detailScroll.surface(PANEL);
		detailScroll.padding(Insets.of(COLUMN_PADDING));
		main.child(detailScroll);
		content.child(main);
		fillDetail();
	}

	// measured bc pack fonts (caxton, bold) are wider. +1 slack
	private List<Column> measured(List<Column> columns, String key, List<String> texts) {
		return columns.stream().map(c -> {
			if (!c.key().equals(key)) return c;
			int widest = font.width(c.title());
			for (String text : texts) widest = Math.max(widest, font.width(text));
			return new Column(c.key(), c.title(), widest + 1, c.right(), c.dropOrder());
		}).toList();
	}

	private void select(int index) {
		selected = index;
		for (int i = 0; i < shaftRows.size(); i++) shaftRows.get(i).surface(rowSurface(i, false));
		fillDetail();
	}

	private void fillDetail() {
		if (detail == null) return;
		detail.clearChildren();
		List<JournalEntry> shafts = sessions.get(sessionIndex).entries();
		if (selected < 0 || selected >= shafts.size()) return;
		JournalEntry entry = shafts.get(selected);
		ShaftValue value = ShaftJournal.MONEY.value(entry);

		String id = entry.shaftId() == null ? "Unknown shaft" : entry.shaftId();
		detail.child(wrapped("§b" + id + ("C".equals(JournalStats.variant(entry)) ? "  §dCrystal" : "")));
		detail.child(spacer(4));
		detail.child(keyValue("Entered", "§f" + time(entry.enteredAt())));
		detail.child(keyValue("Exit", "§f" + SummaryText.exitName(entry.exit())));
		detail.child(keyValue("Spawned in", entry.searchMillis() == null ? "§8not timed" : spawn(entry.searchMillis())));
		detail.child(keyValue("Inside", "§f" + SummaryText.duration(entry.durationMillis())));
		if (value != null && !value.lines().isEmpty()) detail.child(keyValue("Coins", coins(value.net())));
		if (!entry.mayhemBuffs().isEmpty()) detail.child(keyValue("Mayhem", "§e" + String.join(", ", entry.mayhemBuffs())));
		String powder = "§b" + number(JournalStats.powder(entry));
		if (entry.corpsePowder() > 0 && entry.exitPowder() > 0) {
			powder += " §8(" + number(entry.corpsePowder()) + " corpses + " + number(entry.exitPowder()) + " exit)";
		}
		detail.child(keyValue("Powder", powder));
		if (!entry.campfiresAt().isEmpty()) detail.child(keyValue("Campfires", "§f" + entry.campfiresAt().size()));
		if (entry.coldAtExit() != null) detail.child(keyValue("Cold at exit", "§b" + entry.coldAtExit() + "❄"));

		detail.child(section(lootTitle(entry.loots())));
		LootSummary loot = LootSummary.of(entry.loots(), TextPatterns.defaults());
		if (loot.gems() == null && loot.items().isEmpty()) {
			detail.child(wrapped("§8No corpses looted"));
		} else {
			FlowLayout grid = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
			grid.gap(2);
			if (loot.gems() != null) {
				List<Component> tooltip = new ArrayList<>();
				tooltip.add(Component.literal("§fGemstones §7≈ " + fine(loot.gems().fine()) + " Fine"));
				for (ItemCount part : loot.gems().parts()) tooltip.add(Component.literal("§7" + number(part.count()) + " " + part.item()));
				// parts sorted, first = most common
				grid.child(slot(loot.gems().parts().getFirst().item(), compact(loot.gems().fine()), tooltip));
			}
			for (ItemCount item : loot.items()) {
				grid.child(slot(item.item(), number(item.count()),
						List.of(Component.literal("§f" + item.item()), Component.literal("§7× " + number(item.count())))));
			}
			detail.child(grid);
		}

		detail.child(section("Gems mined"));
		if (entry.gems() == null) {
			detail.child(wrapped("§8None"));
		} else {
			detail.child(wrapped("§f≈" + decimal(entry.gems().fineEquivalent()) + " Fine " + entry.gems().gem()));
			if (entry.pristineProcs() > 0) detail.child(wrapped("§8" + entry.pristineProcs() + " pristine procs"));
		}
		coinsSection(entry, value);
	}

	private void coinsSection(JournalEntry entry, ShaftValue value) {
		detail.child(section("Coins"));
		if (value == null) {
			detail.child(wrapped("§8No prices yet. They load when you enter a shaft or open the journal."));
			return;
		}
		if (value.lines().isEmpty()) {
			detail.child(wrapped("§8Nothing worth coins"));
			return;
		}
		for (Part part : Part.values()) {
			List<Line> lines = value.lines().stream().filter(l -> l.part() == part).toList();
			if (lines.isEmpty()) continue;
			boolean priced = lines.stream().anyMatch(l -> l.coins() != null);
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§f" + part.displayName()));
			for (Line line : lines) {
				tooltip.add(Component.literal("§7" + count(line) + " " + line.item() + "  "
						+ (line.coins() == null ? "§8no price" : coins(line.coins()))));
			}
			detail.child(tipRow(part.displayName(), priced ? coins(value.total(part)) : "§8no price", tooltip));
		}
		MoneySettings settings = ShaftJournal.MONEY.settings();
		List<Component> how = List.of(Component.literal("§fHow coins are counted"),
				Component.literal("§7Gems as " + (settings.gemTier() == MoneySettings.GemTier.FINE ? "Fine" : "Flawless")),
				Component.literal("§7Sold by " + (settings.sellPrice() == MoneySettings.SellPrice.INSTA_SELL ? "insta-sell" : "sell offer")
						+ ", " + settings.bazaarTax() + "% Bazaar tax"),
				Component.literal("§7Keys and campfires at insta-buy"),
				Component.literal("§8Change it in .minecraft/shaftjournal/config.json"));
		FlowLayout net = tipRow("Net", coins(value.net()), how);
		net.margins(Insets.top(2));
		detail.child(net);
		int unpriced = value.unpriced().size();
		if (unpriced > 0) detail.child(wrapped("§c" + unpriced + (unpriced == 1 ? " item has" : " items have") + " no price"));
		boolean saved = ShaftJournal.MONEY.usesSavedPrices(entry);
		long end = entry.enteredAt() + entry.durationMillis();
		detail.child(wrapped("§8" + (saved ? "Prices from " + day(end) + " " + clock(end) : "Today's prices")));
	}

	private static FlowLayout tipRow(String key, String value, List<Component> tooltip) {
		return withTooltip(keyValue(key, value), tooltip);
	}

	private static String coins(double coins) {
		return (Math.round(coins) < 0 ? "§c" : "§6") + Coins.signed(coins);
	}

	private static String count(Line line) {
		if (line.part() == Part.MINED) return "≈" + decimal(line.count());
		return number(Math.round(line.count()));
	}

	private static String lootTitle(List<CorpseLoot> loots) {
		if (loots.isEmpty()) return "Corpse loot";
		Map<String, Integer> byType = new java.util.LinkedHashMap<>();
		for (CorpseLoot loot : loots) byType.merge(loot.type(), 1, Integer::sum);
		List<String> parts = new ArrayList<>();
		byType.forEach((type, count) -> parts.add(count + " " + type));
		return "Corpse loot (" + String.join(", ", parts) + ")";
	}

	private StackLayout slot(String name, String count, List<Component> tooltip) {
		StackLayout slot = UIContainers.stack(Sizing.fixed(18), Sizing.fixed(18));
		slot.surface(SLOT);
		slot.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM);
		ItemComponent item = UIComponents.item(icons.stack(name));
		item.positioning(Positioning.absolute(1, 1));
		item.tooltip(tooltip);
		slot.child(item);
		LabelComponent label = label("§f" + count);
		label.shadow(true);
		label.tooltip(tooltip);
		slot.child(label);
		slot.tooltip(tooltip);
		return slot;
	}

	private record Day(long start, List<Integer> sessions, long millis, int shafts, long powder, Double coins) {
		Double perHour() {
			return coins == null || millis < JournalStats.MIN_RATE_MILLIS ? null : coins * 3_600_000.0 / millis;
		}
	}

	// sessions newest first, a session counts on the day it started
	private List<Day> days(List<Double> sessionCoins) {
		List<Day> days = new ArrayList<>();
		for (int i = 0; i < sessions.size(); i++) {
			SessionSummary session = sessions.get(i);
			Day last = days.isEmpty() ? null : days.getLast();
			if (last == null || !date(last.start()).equals(date(session.start()))) {
				days.add(new Day(session.start(), List.of(i), session.lengthMillis(), session.shafts(), session.powder(), sessionCoins.get(i)));
				continue;
			}
			List<Integer> indexes = new ArrayList<>(last.sessions());
			indexes.add(i);
			days.set(days.size() - 1, new Day(last.start(), indexes, last.millis() + session.lengthMillis(), last.shafts() + session.shafts(),
					last.powder() + session.powder(), MoneyStats.plus(last.coins(), sessionCoins.get(i))));
		}
		return days;
	}

	private void buildOverviewTab(FlowLayout content) {
		long slowest = sessions.stream().map(SessionSummary::averageSearchMillis).filter(a -> a != null)
				.mapToLong(Long::longValue).max().orElse(1);
		List<Double> sessionCoins = sessions.stream().map(s -> MoneyStats.coins(s.entries(), ShaftJournal.MONEY::value)).toList();
		List<Double> coinRates = new ArrayList<>();
		for (int i = 0; i < sessions.size(); i++) {
			Double coins = sessionCoins.get(i);
			coinRates.add(coins == null ? null : sessions.get(i).perHour(coins, sessions.get(i).end()));
		}
		List<Day> days = days(sessionCoins);
		List<String> coinTexts = new ArrayList<>(sessionCoins.stream().map(JournalScreen::coinText).toList());
		List<String> rateTexts = new ArrayList<>(coinRates.stream().map(JournalScreen::rateText).toList());
		for (Day day : days) {
			coinTexts.add(coinText(day.coins()));
			rateTexts.add(dayRate(day));
		}
		List<Column> measured = measured(SESSION_COLUMNS.stream().filter(c -> !byDay || !c.key().equals("start")).toList(),
				"coins", coinTexts);
		measured = measured(measured, "coinRate", rateTexts);
		List<Column> columns = fit(measured, width - 2 * PADDING - TABLE_CHROME);
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		for (Day day : days) {
			if (byDay) {
				FlowLayout dayRow = dayRow(columns, day);
				if (!rows.children().isEmpty()) dayRow.margins(Insets.top(DAY_GAP));
				rows.child(dayRow);
			}
			for (int i : day.sessions()) {
				SessionSummary session = sessions.get(i);
				Double rate = session.shaftsPerHour();
				FlowLayout row = row(i);
				for (Column column : columns) {
					row.child(switch (column.key()) {
						case "date" -> byDay ? indented(column, "§7" + clock(session.start())) : cell(column, "§f" + day(session.start()));
						case "start" -> cell(column, "§7" + clock(session.start()));
						case "length" -> cell(column, "§f" + SummaryText.duration(session.lengthMillis()));
						case "shafts" -> cell(column, "§f" + session.shafts());
						case "spawn" -> barCell(column, session.averageSearchMillis(), slowest);
						case "rate" -> cell(column, rate == null ? "§8-" : "§f" + decimal(rate));
						case "powder" -> cell(column, "§b" + number(session.powder()));
						case "coins" -> cell(column, coinText(sessionCoins.get(i)));
						case "coinRate" -> cell(column, rateText(coinRates.get(i)));
						default -> cell(column, "§9" + session.looted("Lapis"));
					});
				}
				int index = i;
				row.mouseDown().subscribe((event, doubleClick) -> {
					showSession(index);
					return true;
				});
				rows.child(row);
			}
		}
		content.child(table(columns, rows));
	}

	private FlowLayout dayRow(List<Column> columns, Day day) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.padding(Insets.horizontal(2));
		row.gap(COLUMN_GAP);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.surface(ROW_DAY);
		int count = day.sessions().size();
		for (Column column : columns) {
			row.child(switch (column.key()) {
				case "date" -> cell(column, "§f" + day(day.start()));
				case "length" -> cell(column, "§f" + SummaryText.duration(day.millis()));
				case "shafts" -> cell(column, "§f" + day.shafts());
				case "spawn" -> cell(column, "§7" + count + (count == 1 ? " session" : " sessions"));
				case "powder" -> cell(column, "§b" + number(day.powder()));
				case "coins" -> cell(column, coinText(day.coins()));
				case "coinRate" -> cell(column, dayRate(day));
				default -> cell(column, "");
			});
		}
		return row;
	}

	private static LabelComponent indented(Column column, String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fixed(Math.max(0, column.total() - INDENT)));
		label.margins(Insets.left(INDENT));
		return label;
	}

	private static String dayRate(Day day) {
		return rateText(day.perHour()) + (day.perHour() == null ? "" : " §8(avg)");
	}

	// gray so only the totals are gold
	private static String rateText(Double coins) {
		if (coins == null) return "§8-";
		return Math.round(coins) == 0 ? "§80" : "§7" + Coins.signed(coins);
	}

	private void buildStatsTab(FlowLayout content) {
		AllTime stats = JournalStats.allTime(entries);
		MoneyStats money = MoneyStats.of(entries, sessions, ShaftJournal.MONEY::value);
		FlowLayout columns = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
		columns.gap(GAP);

		FlowLayout totals = column();
		totals.child(header("All time"));
		totals.child(keyValue("Shafts", "§f" + stats.shafts()));
		totals.child(keyValue("Sessions", "§f" + stats.sessions()));
		totals.child(keyValue("Time shafting", "§f" + SummaryText.duration(stats.timeMillis())));
		totals.child(keyValue("Powder", "§b" + number(stats.powder())));
		totals.child(coinsTotal(money, stats));
		totals.child(section("Corpses looted"));
		for (String type : stats.corpsesSeen().keySet()) totals.child(corpseRow(type, stats.looted().getOrDefault(type, 0), money));
		totals.child(section("Personal bests"));
		JournalEntry fastest = stats.fastest();
		totals.child(record("Fastest spawn", fastest == null ? null : spawn(fastest.searchMillis()),
				fastest == null ? null : () -> openShaft(fastest), fastest == null ? null : fastest.shaftId()));
		SessionSummary fastestSession = stats.fastestSession();
		totals.child(record("Fastest session", fastestSession == null ? null : spawn(fastestSession.averageSearchMillis()) + " §7avg",
				fastestSession == null ? null : () -> openSession(fastestSession), fastestSession == null ? null : sessionName(fastestSession)));
		SessionSummary bestRate = stats.bestRate();
		totals.child(record("Shafts / h", bestRate == null ? null : "§f" + decimal(bestRate.shaftsPerHour()),
				bestRate == null ? null : () -> openSession(bestRate), bestRate == null ? null : sessionName(bestRate)));
		SessionSummary mostShafts = stats.mostShafts();
		totals.child(record("Shafts / session", mostShafts == null ? null : "§f" + mostShafts.shafts(),
				mostShafts == null ? null : () -> openSession(mostShafts), mostShafts == null ? null : sessionName(mostShafts)));
		JournalStats.Best lapis = stats.mostLapis();
		totals.child(record("Lapis / shaft", lapis == null ? null : "§9" + lapis.value(),
				lapis == null ? null : () -> openShaft(lapis.entry()), lapis == null ? null : lapis.entry().shaftId()));
		JournalStats.Best powder = stats.mostPowder();
		totals.child(record("Powder / shaft", powder == null ? null : "§b" + number(powder.value()),
				powder == null ? null : () -> openShaft(powder.entry()), powder == null ? null : powder.entry().shaftId()));
		JournalEntry gems = stats.mostGems();
		totals.child(record("Gems / shaft", gems == null ? null : "§f≈" + decimal(gems.gems().fineEquivalent()) + " Fine",
				gems == null ? null : () -> openShaft(gems), gems == null ? null : gems.shaftId()));
		MoneyStats.Best richest = money.bestShaft();
		totals.child(record("Coins / shaft", richest == null ? null : coins(richest.coins()),
				richest == null ? null : () -> openShaft(richest.entry()), richest == null ? null : richest.entry().shaftId()));
		MoneyStats.BestRate coinRate = money.bestRate();
		totals.child(record("Coins / h", coinRate == null ? null : coins(coinRate.perHour()),
				coinRate == null ? null : () -> openSession(coinRate.session()), coinRate == null ? null : sessionName(coinRate.session())));
		columns.child(scrollColumn(Sizing.fill(STATS_LEFT), totals));

		FlowLayout spawn = column();
		spawn.child(header("Spawn time"));
		spawn.child(keyValue("All-time avg", stats.averageSearchMillis() == null ? "§8-"
				: "§e" + SummaryText.duration(stats.averageSearchMillis())));
		Long recent = JournalStats.recentAverage(sessions, RECENT_SESSIONS);
		if (recent != null && stats.averageSearchMillis() != null && sessions.size() > RECENT_SESSIONS) {
			long difference = recent - stats.averageSearchMillis();
			// durations show whole secs, <1s = same
			String change = Math.abs(difference) < 1000 ? " §7(same)"
					: difference < 0 ? " §7(§a" + SummaryText.duration(-difference) + " faster§7)"
					: " §7(§c" + SummaryText.duration(difference) + " slower§7)";
			spawn.child(keyValue("Last " + RECENT_SESSIONS + " sessions", "§f" + SummaryText.duration(recent) + change));
		}
		spawn.child(section("Avg spawn per session"));
		spawn.child(chart(stats.oldestFirst(), stats.averageSearchMillis()));
		spawn.child(section("Corpse % per shaft"));
		for (String type : stats.corpsesSeen().keySet()) {
			int with = stats.shaftsWithCorpse().getOrDefault(type, 0);
			FlowLayout row = barRow(corpseColor(type) + type, with / (double) stats.shafts(),
					Math.round(100.0 * with / stats.shafts()) + "%", corpseArgb(type));
			row.tooltip(List.of(Component.literal("§f" + type + " corpses"),
					Component.literal("§7In " + with + " of " + stats.shafts() + " shafts"),
					Component.literal("§7" + String.format(Locale.ROOT, "%.2f", stats.corpsesSeen().get(type) / (double) stats.shafts())
							+ " per shaft on average")));
			spawn.child(row);
		}
		columns.child(scrollColumn(Sizing.expand(), spawn));

		FlowLayout odds = column();
		odds.child(header("Shaft types seen"));
		int typed = stats.typedShafts();
		for (Map.Entry<String, Map<String, Integer>> type : stats.types().entrySet()) {
			String name = shaftNames.name(type.getKey());
			int total = type.getValue().values().stream().mapToInt(Integer::intValue).sum();
			FlowLayout row = barRow("§f" + name, total / (double) typed,
					Math.round(100.0 * total / typed) + "%", 0xFF000000 | shaftNames.rgb(type.getKey()));
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§f" + name + " shafts §7" + total));
			type.getValue().forEach((variant, count) -> {
				if (!variant.equals("L")) tooltip.add(Component.literal("§7" + capitalized(variantName(variant)) + " §f" + count));
			});
			row.tooltip(tooltip);
			odds.child(row);
		}
		columns.child(scrollColumn(Sizing.fill(STATS_RIGHT), odds));
		content.child(columns);
	}

	private static FlowLayout coinsTotal(MoneyStats money, AllTime stats) {
		FlowLayout row = keyValue("Coins", coinText(money.coins()));
		if (money.coins() == null) return row;
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(Component.literal("§fWhere your coins came from"));
		money.parts().forEach((part, coins) -> {
			if (Math.round(coins) != 0) tooltip.add(Component.literal("§7" + part.displayName() + " " + coins(coins)));
		});
		tooltip.add(Component.literal(""));
		tooltip.add(Component.literal("§7Avg per shaft " + coins(money.perShaft()) + " §8(" + money.valued() + " shafts)"));
		if (stats.timeMillis() >= JournalStats.MIN_RATE_MILLIS) {
			tooltip.add(Component.literal("§7Avg per hour shafting " + coins(money.coins() * 3_600_000.0 / stats.timeMillis())
					+ " §8(" + SummaryText.duration(stats.timeMillis()) + ")"));
		}
		return withTooltip(row, tooltip);
	}

	private FlowLayout corpseRow(String type, int looted, MoneyStats money) {
		MoneyStats.CorpseCoins corpse = money.corpses().get(type);
		String value = corpseColor(type) + looted;
		if (corpse == null) return keyValue(type, value);
		String avg = " §7(" + coins(corpse.each()) + " §7avg.)";
		String full = value + " " + coins(corpse.net()) + avg;
		// narrow screens: total moves to the tooltip
		int room = width * STATS_LEFT / 100 - 2 * COLUMN_PADDING - 3 - font.width(type) - 6;
		FlowLayout row = keyValue(type, font.width(full) <= room ? full : value + avg);
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(Component.literal("§f" + type + " corpses"));
		tooltip.add(Component.literal("§7" + corpse.corpses() + " with loot, " + coins(corpse.net()) + " §7in all"));
		if (corpse.keys() != 0) {
			tooltip.add(Component.literal("§7Loot " + coins(corpse.loot() / corpse.corpses()) + " §7each"));
			tooltip.add(Component.literal("§7Key " + coins(corpse.keys() / corpse.corpses()) + " §7each"));
		}
		if (corpse.unpriced() > 0) {
			tooltip.add(Component.literal("§c" + corpse.unpriced() + (corpse.unpriced() == 1 ? " item" : " items") + " without a price"));
		}
		return withTooltip(row, tooltip);
	}

	private static FlowLayout withTooltip(FlowLayout row, List<Component> tooltip) {
		row.tooltip(tooltip);
		row.children().forEach(child -> child.tooltip(tooltip));
		return row;
	}

	private static String coinText(Double coins) {
		if (coins == null) return "§8-";
		return Math.round(coins) == 0 ? "§80" : coins(coins);
	}
	private static final net.minecraft.resources.Identifier STATS_ICON =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("shaftjournal", "textures/gui/stats_icon.png");
	private static final int INDENT = 6;
	// display choice
	private static final int DAY_GAP = 8;
	private static final Identifier OVERVIEW_ICON = Identifier.fromNamespaceAndPath("shaftjournal", "textures/gui/overview_icon.png");
	private static final Identifier SESSION_ICON = Identifier.fromNamespaceAndPath("shaftjournal", "textures/gui/session_icon.png");
	private static final Identifier CHECK_ICON = Identifier.fromNamespaceAndPath("shaftjournal", "textures/gui/check_icon.png");
	private static final Identifier CROSS_ICON = Identifier.fromNamespaceAndPath("shaftjournal", "textures/gui/cross_icon.png");
	private static final net.minecraft.resources.Identifier OPEN_ARROW =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("shaftjournal", "textures/gui/open_arrow.png");
	private static final int RECENT_SESSIONS = 5;

	private FlowLayout record(String key, String value, Runnable open, String target) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(4);
		row.verticalAlignment(VerticalAlignment.TOP);
		LabelComponent keyLabel = label("§7" + key);
		keyLabel.horizontalSizing(Sizing.expand());
		row.child(keyLabel);
		row.child(label(value == null ? "§8-" : value));
		if (open != null) {
			// padding = bigger click area
			FlowLayout arrow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			arrow.padding(Insets.of(2, 2, 2, 1));
			arrow.margins(Insets.top(-1));
			arrow.child(UIComponents.texture(OPEN_ARROW, 0, 0, 3, 5, 3, 5).blend(true));
			arrow.cursorStyle(CursorStyle.HAND);
			arrow.tooltip(Component.literal("§7Open " + (target == null ? "shaft" : target)));
			arrow.mouseDown().subscribe((event, doubleClick) -> {
				open.run();
				return true;
			});
			row.child(arrow);
		}
		return row;
	}

	private static String sessionName(SessionSummary session) {
		return "session from " + day(session.start()) + " " + clock(session.start());
	}

	private static final long[] AXIS_STEPS = {5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};

	private FlowLayout chart(List<SessionSummary> oldestFirst, Long average) {
		List<SessionSummary> timed = oldestFirst.stream().filter(s -> s.averageSearchMillis() != null).toList();
		FlowLayout wrapper = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		if (timed.isEmpty()) {
			wrapper.child(label("§8No timed spawns yet"));
			return wrapper;
		}
		int chartHeight = Math.clamp(height / 3, 50, 110);
		long slowestSeconds = Math.max(1, (timed.stream().mapToLong(SessionSummary::averageSearchMillis).max().orElse(1) + 999) / 1000);
		long step = AXIS_STEPS[AXIS_STEPS.length - 1];
		for (long candidate : AXIS_STEPS) {
			if ((slowestSeconds + candidate - 1) / candidate <= 4) {
				step = candidate;
				break;
			}
		}
		long topSeconds = Math.max(step, (slowestSeconds + step - 1) / step * step);
		int axisWidth = 0;
		for (long seconds = 0; seconds <= topSeconds; seconds += step) {
			axisWidth = Math.max(axisWidth, font.width(axisLabel(seconds)));
		}
		axisWidth += 2;

		int plotWidth = Math.max(60, width - 2 * PADDING - 2 * GAP - width * STATS_LEFT / 100 - width * STATS_RIGHT / 100
				- 2 * COLUMN_PADDING - axisWidth - AXIS_GAP);
		// display choice: bars up to 24 wide, gap grows w/ them
		int barWidth = Math.clamp((plotWidth - 2) * 4 / 5 / timed.size(), 3, 24);
		int barGap = Math.clamp(barWidth / 4, 2, 6);
		int maxBars = Math.max(1, (plotWidth - 2) / (barWidth + barGap));
		if (timed.size() > maxBars) timed = timed.subList(timed.size() - maxBars, timed.size());

		int top = 4;
		int base = chartHeight - 5;
		int plotHeight = base - top;
		FlowLayout chart = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(chartHeight));
		chart.gap(AXIS_GAP);
		StackLayout axis = UIContainers.stack(Sizing.fixed(axisWidth), Sizing.fixed(chartHeight));
		StackLayout plot = UIContainers.stack(Sizing.expand(), Sizing.fixed(chartHeight));
		for (long seconds = 0; seconds <= topSeconds; seconds += step) {
			int y = base - (int) (plotHeight * seconds / topSeconds);
			LabelComponent tick = label("§8" + axisLabel(seconds));
			tick.horizontalSizing(Sizing.fixed(axisWidth));
			tick.horizontalTextAlignment(HorizontalAlignment.RIGHT);
			tick.positioning(Positioning.absolute(0, y - 4));
			axis.child(tick);
			plot.child(line(y, seconds == 0 ? 0x40FFFFFF : 0x18FFFFFF));
		}
		FlowLayout bars = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(base));
		bars.verticalAlignment(VerticalAlignment.BOTTOM);
		bars.gap(barGap);
		bars.padding(Insets.left(2));
		bars.positioning(Positioning.absolute(0, 0));
		for (SessionSummary session : timed) {
			int barHeight = Math.max(1, (int) (plotHeight * session.averageSearchMillis() / 1000.0 / topSeconds));
			BoxComponent bar = UIComponents.box(Sizing.fixed(barWidth), Sizing.fixed(barHeight));
			bar.fill(true);
			bar.color(GREEN);
			bar.cursorStyle(CursorStyle.HAND);
			bar.tooltip(List.of(Component.literal("§f" + day(session.start()) + " " + clock(session.start())),
					Component.literal("§7Avg spawn " + spawn(session.averageSearchMillis()) + " §7over " + session.timedSpawns() + " shafts"),
					Component.literal("§8Click to open")));
			bar.mouseDown().subscribe((event, doubleClick) -> {
				openSession(session);
				return true;
			});
			bars.child(bar);
		}
		plot.child(bars);
		if (average != null) {
			int y = base - (int) Math.min(plotHeight, plotHeight * average / 1000.0 / topSeconds);
			plot.child(line(y, 0xB0FFFF55));
		}
		chart.child(axis);
		chart.child(plot);
		wrapper.child(chart);

		StackLayout dates = UIContainers.stack(Sizing.fill(100), Sizing.fixed(10));
		LabelComponent first = label("§8" + day(timed.getFirst().start()));
		first.positioning(Positioning.absolute(axisWidth + AXIS_GAP + 2, 1));
		dates.child(first);
		if (timed.size() > 1) {
			String lastText = "§8" + day(timed.getLast().start());
			int lastX = axisWidth + AXIS_GAP + 2 + (timed.size() - 1) * (barWidth + barGap) + barWidth - font.width(lastText);
			int firstEnd = axisWidth + AXIS_GAP + 2 + font.width("§8" + day(timed.getFirst().start()));
			if (lastX > firstEnd + 6) {
				LabelComponent last = label(lastText);
				last.positioning(Positioning.absolute(lastX, 1));
				dates.child(last);
			}
		}
		wrapper.child(dates);
		if (average != null) wrapper.child(label("§eYellow line §7all-time avg"));
		return wrapper;
	}

	private static BoxComponent line(int y, int argb) {
		BoxComponent line = UIComponents.box(Sizing.fill(100), Sizing.fixed(1));
		line.fill(true);
		line.color(Color.ofArgb(argb));
		line.positioning(Positioning.absolute(0, y));
		return line;
	}

	private static String axisLabel(long seconds) {
		if (seconds == 0) return "0";
		if (seconds < 60) return seconds + "s";
		return seconds % 60 == 0 ? seconds / 60 + "m" : seconds / 60 + "m " + seconds % 60 + "s";
	}

	private FlowLayout barRow(String name, double fraction, String value, int argb) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.gap(4);
		LabelComponent nameLabel = label("§f" + name);
		nameLabel.horizontalSizing(Sizing.fixed(62));
		row.child(nameLabel);
		FlowLayout track = UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(5));
		track.surface(Surface.flat(0x14FFFFFF));
		BoxComponent fill = UIComponents.box(Sizing.fill((int) Math.round(Math.min(1, fraction) * 100)), Sizing.fill(100));
		fill.fill(true);
		fill.color(Color.ofArgb(argb));
		track.child(fill);
		row.child(track);
		LabelComponent valueLabel = label("§f" + value);
		valueLabel.horizontalSizing(Sizing.fixed(26));
		valueLabel.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		row.child(valueLabel);
		return row;
	}

	// table padding + row padding + scrollbar
	private static final int TABLE_CHROME = 10 + 4 + 4;

	private static final int COLUMN_GAP = 6;

	private static List<Column> fit(List<Column> columns, int available) {
		List<Column> visible = new ArrayList<>(columns);
		while (visible.stream().mapToInt(Column::width).sum() + COLUMN_GAP * (visible.size() - 1) > available) {
			Column drop = visible.stream().filter(c -> c.dropOrder() > 0)
					.max(Comparator.comparingInt(Column::dropOrder)).orElse(null);
			if (drop == null) break;
			visible.remove(drop);
		}
		// wide screens (gui scale 2) spread the spare room, up to 2x each
		int base = visible.stream().mapToInt(Column::width).sum();
		int spare = available - base - COLUMN_GAP * (visible.size() - 1);
		if (spare <= 0) return visible;
		List<Column> spread = new ArrayList<>();
		for (Column c : visible) {
			int extra = Math.min(c.width(), spare * c.width() / base);
			spread.add(new Column(c.key(), c.title(), c.width(), c.right(), c.dropOrder(), extra));
		}
		return spread;
	}

	private FlowLayout table(List<Column> columns, FlowLayout rows) {
		FlowLayout table = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill(100));
		table.surface(PANEL);
		table.padding(Insets.of(5));
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		header.padding(Insets.horizontal(2));
		header.gap(COLUMN_GAP);
		header.verticalAlignment(VerticalAlignment.CENTER);
		for (Column column : columns) header.child(cell(column, "§7" + column.title()));
		table.child(header);
		table.child(UIComponents.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofArgb(0x1FFFFFFF)).fill(true));
		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), rows);
		scroll.scrollbarThiccness(3);
		scroll.margins(Insets.top(2));
		table.child(scroll);
		return table;
	}

	private FlowLayout row(int index) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.padding(Insets.horizontal(2));
		row.gap(COLUMN_GAP);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.surface(rowSurface(index, false));
		row.cursorStyle(CursorStyle.HAND);
		row.mouseEnter().subscribe(() -> row.surface(rowSurface(index, true)));
		row.mouseLeave().subscribe(() -> row.surface(rowSurface(index, false)));
		return row;
	}

	private Surface rowSurface(int index, boolean hovered) {
		if ((viewing || tab == Tab.SESSION) && index == selected) return ROW_SELECTED;
		if (hovered) return ROW_HOVER;
		return index % 2 == 1 ? ROW_EVEN : Surface.BLANK;
	}

	private static LabelComponent cell(Column column, String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fixed(column.total()));
		if (column.right()) label.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		return label;
	}

	private static FlowLayout barCell(Column column, Long millis, long slowest) {
		FlowLayout cell = UIContainers.horizontalFlow(Sizing.fixed(column.total()), Sizing.content());
		cell.verticalAlignment(VerticalAlignment.CENTER);
		cell.gap(3);
		if (millis == null) {
			cell.child(label("§8-"));
			return cell;
		}
		LabelComponent text = label("§f" + SummaryText.duration(millis));
		text.horizontalSizing(Sizing.fixed(column.width() - SPAWN_BAR - 3));
		cell.child(text);
		int barMax = SPAWN_BAR + column.extra();
		BoxComponent bar = UIComponents.box(Sizing.fixed(Math.max(1, (int) (barMax * millis / Math.max(1, slowest)))), Sizing.fixed(3));
		bar.fill(true);
		bar.color(GREEN);
		cell.child(bar);
		return cell;
	}

	private static FlowLayout tile(int width, String key, String value) {
		FlowLayout tile = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
		tile.surface(PANEL);
		tile.padding(Insets.of(5));
		tile.gap(3);
		tile.child(label("§7" + key));
		tile.child(label(bold(value)));
		return tile;
	}

	// color code resets bold, so bold goes after it
	private static String bold(String value) {
		return value.startsWith("§") && value.length() >= 2 ? value.substring(0, 2) + "§l" + value.substring(2) : "§l" + value;
	}

	private static FlowLayout column() {
		FlowLayout column = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		column.gap(2);
		return column;
	}

	private static ScrollContainer<FlowLayout> scrollColumn(Sizing width, FlowLayout column) {
		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(width, Sizing.fill(100), column);
		scroll.surface(PANEL);
		scroll.padding(Insets.of(COLUMN_PADDING));
		scroll.scrollbarThiccness(3);
		return scroll;
	}

	private static FlowLayout panelWith(UIComponent child, Sizing width, Sizing height) {
		FlowLayout panel = UIContainers.verticalFlow(width, height);
		panel.surface(PANEL);
		panel.padding(Insets.of(8));
		panel.child(child);
		return panel;
	}

	private static FlowLayout keyValue(String key, String value) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(6);
		row.child(label("§7" + key));
		LabelComponent valueLabel = label(value);
		valueLabel.horizontalSizing(Sizing.expand());
		row.child(valueLabel);
		return row;
	}

	private static LabelComponent header(String text) {
		LabelComponent label = label("§7" + text);
		label.margins(Insets.bottom(3));
		return label;
	}

	private static FlowLayout section(String title) {
		FlowLayout section = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		section.margins(Insets.vertical(4));
		section.gap(3);
		section.child(UIComponents.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofArgb(0x1FFFFFFF)).fill(true));
		section.child(wrapped("§7" + title));
		return section;
	}

	private static UIComponent spacer(int height) {
		return UIComponents.box(Sizing.fixed(1), Sizing.fixed(height)).color(Color.ofArgb(0));
	}

	private static LabelComponent label(String text) {
		return new SafeLabel(Component.literal(text));
	}

	private static LabelComponent wrapped(String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fill(100));
		return label;
	}

	private static String corpses(JournalEntry entry) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, CorpseCount> e : JournalStats.corpses(entry).entrySet()) {
			CorpseCount count = e.getValue();
			String color = count.looted() > 0 ? corpseColor(e.getKey()) : "§8";
			parts.add(color + count.looted() + "/" + count.total() + " " + e.getKey().charAt(0));
		}
		return parts.isEmpty() ? "§8-" : String.join(" ", parts);
	}

	private static String corpseColor(String type) {
		return switch (type) {
			case "Lapis" -> "§9";
			case "Umber" -> "§6";
			case "Tungsten" -> "§7";
			case "Vanguard" -> "§b";
			default -> "§f";
		};
	}

	private static int corpseArgb(String type) {
		return switch (type) {
			case "Lapis" -> 0xFF5555FF;
			case "Umber" -> 0xFFFFAA00;
			case "Tungsten" -> 0xFFAAAAAA;
			case "Vanguard" -> 0xFF55FFFF;
			default -> 0xFFFFFFFF;
		};
	}

	private static String variantName(String variant) {
		return switch (variant) {
			case "1" -> "type 1";
			case "2" -> "type 2";
			case "C" -> "crystal";
			default -> "_" + variant;
		};
	}

	private static String capitalized(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String spawn(Long millis) {
		return millis == null ? "§8-" : "§a" + SummaryText.duration(millis);
	}

	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);

	private static String clock(long millis) {
		return CLOCK.format(Instant.ofEpochMilli(millis).atZone(ZONE));
	}

	private static String time(long millis) {
		return TIME.format(Instant.ofEpochMilli(millis).atZone(ZONE));
	}

	private static LocalDate date(long millis) {
		return Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
	}

	private static String day(long millis) {
		LocalDate date = date(millis);
		LocalDate today = LocalDate.now(ZONE);
		if (date.equals(today)) return "Today";
		if (date.equals(today.minusDays(1))) return "Yesterday";
		return (date.getYear() == today.getYear() ? DAY : DAY_YEAR).format(date);
	}

	// shorter at 480 wide, the full range ran under the tabs
	private String sessionRange(SessionSummary session, int room) {
		String start = day(session.start()) + " " + clock(session.start());
		String full = start + " to " + clock(session.end());
		if (font.width(full) <= room) return full;
		return font.width(start) <= room ? start : day(session.start());
	}

	// top bar minus everything but the date: padding, title, tabs, arrows, edit hud, gaps
	private int navRoom(String back) {
		int used = 2 * PADDING + 8 + font.width("Shaft Journal") + 10 + font.width("Edit HUD") + 18 + 2 * 16 + 5 * 4 + 2 * 4;
		// tab icons + gap
		for (String tab : List.of("Overview", "Session", "Stats")) used += font.width(tab) + 12 + 9 + 3;
		if (back != null) used += font.width(back) + 12 + 4 + 4;
		return width - used;
	}

	private static String number(long value) {
		return SummaryText.number(value);
	}

	private static String decimal(double value) {
		return SummaryText.decimal(value);
	}

	private static String fine(double value) {
		return value > 0 && value < 0.05 ? "<0.1" : decimal(value);
	}

	private static String compact(double value) {
		if (value >= 9.95 || value == Math.rint(value)) return number(Math.round(value));
		return fine(value);
	}
}
