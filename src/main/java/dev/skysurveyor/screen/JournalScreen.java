package dev.skysurveyor.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.EditHudScreen;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.AllTime;
import dev.skysurveyor.journal.JournalStats.CorpseCount;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.LootSummary;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.journal.SummaryText;
import dev.skysurveyor.money.ChartTips;
import dev.skysurveyor.money.CoinBreakdown;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.Setting;
import dev.skysurveyor.util.Format;

// mc font only so pack fonts work
public final class JournalScreen extends BaseOwoScreen<FlowLayout> {
	private enum Tab { OVERVIEW, SESSION, STATS }

	private enum ChartType {
		BARS("Bars"),
		LINE("Line");

		private final String displayName;

		ChartType(String displayName) {
			this.displayName = displayName;
		}

		String displayName() {
			return displayName;
		}

		ChartType next(int step) {
			return values()[Math.floorMod(ordinal() + step, values().length)];
		}
	}

	private record ShiftTip(FlowLayout row, List<ClientTooltipComponent> normal, List<ClientTooltipComponent> shifted) {
	}

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
	private static final int BAR_GREEN = 0xCC55FF55;
	private static final int BAR_GOLD = 0xCCFFAA00;
	private static final int BAR_RED = 0xCCFF5555;
	private static final Color GREEN = Color.ofArgb(BAR_GREEN);
	private static final int ROW_HEIGHT = 12;
	private static final int SPAWN_BAR = 26;
	private static final int PADDING = 8;
	private static final int GAP = 5;
	private static final int COLUMN_PADDING = 6;
	private static final int COLUMN_GAP = 6;
	// table padding + row padding + scrollbar
	private static final int TABLE_CHROME = 10 + 4 + 4;
	// in %, middle gets the rest
	private static final int STATS_LEFT = 32;
	private static final int STATS_RIGHT = 27;
	private static final int AXIS_GAP = 3;
	private static final int BAR_PADDING = 4;
	private static final int BAR_GAP = 4;
	private static final int ARROW_WIDTH = 16;
	private static final int INDENT = 6;
	// display choice
	private static final int DAY_GAP = 8;
	// display choice
	private static final int LINE_GAP = 8;
	private static final Identifier OVERVIEW_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/overview_icon.png");
	private static final Identifier SESSION_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/session_icon.png");
	private static final Identifier STATS_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/stats_icon.png");
	static final Identifier SETTINGS_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/settings_icon.png");
	private static final Identifier MOVE_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/move_icon.png");
	private static final Identifier OPEN_ARROW = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/open_arrow.png");
	private static final Identifier PREV_ARROW = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/prev_arrow.png");
	private static final int RECENT_SESSIONS = 5;
	// seconds
	private static final double[] SPAWN_STEPS = {5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};
	// 1, 2, 5 x 10^n coins
	private static final double[] COIN_STEPS = coinSteps();
	private static final ZoneId ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("MMM d yyyy", Locale.ENGLISH);

	// width 0 = measured per table, see measured()
	private static final List<Column> SHAFT_COLUMNS = List.of(
			new Column("number", "#", 14, false, 5), new Column("shaft", "Shaft", 44, false, 0),
			new Column("time", "Time", 32, false, 3), new Column("spawn", "Spawn", 66, false, 0),
			new Column("inside", "Inside", 0, true, 2), new Column("corpses", "Corpses", 0, false, 0),
			new Column("powder", "Powder", 44, true, 1), new Column("gems", "Gems", 40, true, 4),
			new Column("coins", "Coins", 0, true, 0), new Column("campfire", "Campfire", 0, false, 6));
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
	private boolean byDay = byDaySetting();
	private int sessionIndex;
	private int selected;
	private FlowLayout root;
	private int builtWidth;
	private int builtHeight;
	private FlowLayout detail;
	private final List<FlowLayout> shaftRows = new ArrayList<>();
	private final List<KeptScroll> scrolls = new ArrayList<>();
	private final List<ShiftTip> shiftTips = new ArrayList<>();
	// kept while the game runs, not saved
	private static StatsRange statsRange = StatsRange.ALL_TIME;
	private static ChartType spawnChartType = ChartType.BARS;
	private static ChartType coinChartType = ChartType.BARS;
	private int statsShafts;
	private boolean shiftShown;
	private int moneyChanges = Surveyor.MONEY.changes();

	public JournalScreen(List<JournalEntry> entries) {
		super(Component.literal("Shaft Journal"));
		this.entries = List.copyOf(entries);
		this.sessions = JournalStats.sessions(this.entries);
		selectLastShaft();
		// session tab = only the one still going
		Journal.Session active = Surveyor.JOURNAL.activeSession(System.currentTimeMillis());
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
		if (byDayChanged()) {
			rebuild();
			return;
		}
		int changes = Surveyor.MONEY.changes();
		if (changes == moneyChanges) return;
		moneyChanges = changes;
		rebuild(true);
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
		// back from settings, before the 1st frame
		if (root != null && (byDayChanged() || width != builtWidth || height != builtHeight)) rebuild();
	}

	// by day lives on the settings page now
	private boolean byDayChanged() {
		if (byDaySetting() == byDay) return false;
		byDay = byDaySetting();
		moneyChanges = Surveyor.MONEY.changes();
		return true;
	}

	private static boolean byDaySetting() {
		return Surveyor.SETTINGS.get().journal().byDay();
	}

	private void rebuild() {
		rebuild(false);
	}

	// keepScroll = same view w/ new prices, scrolls stay put
	private void rebuild(boolean keepScroll) {
		List<Double> offsets = keepScroll ? scrolls.stream().map(KeptScroll::offset).toList() : List.of();
		scrolls.clear();
		buildScreen();
		if (offsets.size() == scrolls.size()) {
			for (int i = 0; i < offsets.size(); i++) scrolls.get(i).offset(offsets.get(i));
		}
	}

	private KeptScroll scroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
		KeptScroll scroll = new KeptScroll(horizontalSizing, verticalSizing, child);
		scrolls.add(scroll);
		return scroll;
	}

	private void buildScreen() {
		builtWidth = width;
		builtHeight = height;
		root.clearChildren();
		shaftRows.clear();
		shiftTips.clear();
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
				footer.child(label("§8Counted from your own " + statsShafts + " shafts"));
				footer.child(label("§8Hover a row for details"));
				footer.child(label("§8Click an arrow to open it"));
			}
		}
		root.child(footer);
	}

	private FlowLayout topBar() {
		FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(PANEL);
		bar.padding(Insets.of(BAR_PADDING));
		bar.gap(BAR_GAP);
		bar.verticalAlignment(VerticalAlignment.CENTER);
		// fixed widths so navRoom() can add them up
		LabelComponent title = label("§fShaft §bJournal");
		title.horizontalSizing(Sizing.fixed(font.width("Shaft Journal")));
		title.margins(Insets.of(0, 0, 2, 8));
		List<ButtonComponent> tabs = List.of(tabButton("Overview", Tab.OVERVIEW), tabButton("Session", Tab.SESSION),
				tabButton("Stats", Tab.STATS));
		IconButton editHud = new IconButton(Component.literal("Edit HUD"), MOVE_ICON, 8, 8,
				b -> minecraft.gui.setScreen(new EditHudScreen(this, Surveyor.HUD)));
		IconButton settings = new IconButton(Component.literal("§fSettings"), SETTINGS_ICON, 8, 8,
				b -> minecraft.gui.setScreen(new SettingsScreen(this)));
		settings.margins(Insets.left(6));
		List<UIComponent> items = new ArrayList<>(tabs);
		items.add(settings);
		items.add(editHud);
		boolean showTitle = true;
		ButtonComponent back = null;
		if (viewing) {
			int date = font.width(day(sessions.get(sessionIndex).start()));
			// 480 wide: title steps aside first, then "<" goes, so the date still fits
			showTitle = navRoom(withTitle(items, title), backWidth("Back")) >= date;
			if (showTitle) items = withTitle(items, title);
			back = backButton(navRoom(items, backWidth("< Back")) < date ? "Back" : "< Back");
		} else {
			items = withTitle(items, title);
		}
		if (showTitle) bar.child(title);
		tabs.forEach(bar::child);

		FlowLayout nav = UIContainers.horizontalFlow(Sizing.expand(), Sizing.content());
		nav.horizontalAlignment(HorizontalAlignment.RIGHT);
		nav.verticalAlignment(VerticalAlignment.CENTER);
		nav.gap(BAR_GAP);
		if (viewing) {
			SessionSummary shown = sessions.get(sessionIndex);
			nav.child(back);
			nav.child(button("<", sessionIndex < sessions.size() - 1, () -> showSession(sessionIndex + 1)));
			nav.child(label("§f" + sessionRange(shown, navRoom(items, fixedWidth(back)))));
			nav.child(button(">", sessionIndex > 0, () -> showSession(sessionIndex - 1)));
		} else if (tab == Tab.SESSION && current) {
			nav.child(label("§f" + sessionRange(sessions.getFirst(), navRoom(items, 0))));
		}
		bar.child(nav);
		bar.child(settings);
		bar.child(editHud);
		return bar;
	}

	private static List<UIComponent> withTitle(List<UIComponent> items, UIComponent title) {
		List<UIComponent> all = new ArrayList<>(items);
		all.addFirst(title);
		return all;
	}

	private ButtonComponent backButton(String text) {
		ButtonComponent back = PackButton.button(Component.literal(text), b -> {
			viewing = false;
			rebuild();
		});
		back.horizontalSizing(Sizing.fixed(font.width(text) + 12));
		back.margins(Insets.right(4));
		return back;
	}

	// backButton() width + margin, w/o building one
	private int backWidth(String text) {
		return font.width(text) + 12 + 4;
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
			panel.child(wrapped("§8A session starts when you mine in the Dwarven Mines and ends after "
					+ Journal.SESSION_GAP_MILLIS / 60_000 + " minutes without mining or shafting. Older sessions are in the Overview."));
		}
		content.child(panelWith(panel, Sizing.fill(100), Sizing.content()));
	}

	private ButtonComponent button(String text, boolean active, Runnable action) {
		ButtonComponent button = PackButton.button(Component.literal(text), b -> action.run());
		button.active(active);
		button.horizontalSizing(Sizing.fixed(ARROW_WIDTH));
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
		Double sessionCoins = MoneyStats.coins(session.entries(), Surveyor.MONEY::value);
		FlowLayout tiles = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		tiles.gap(GAP);
		int tileWidth = (width - 2 * PADDING - 5 * GAP) / 6;
		tiles.child(tile(tileWidth, "Shafts", "§f" + session.shafts()));
		tiles.child(tile(tileWidth, "Avg spawn", spawn(session.averageSearchMillis())));
		tiles.child(tile(tileWidth, "Per hour", rate == null ? "§8-" : "§f" + Format.decimal(rate)));
		tiles.child(tile(tileWidth, "Powder", "§b" + Format.number(session.powder())));
		tiles.child(tile(tileWidth, "Lapis looted", "§9" + session.looted(CorpseType.LAPIS.label())));
		FlowLayout coinsTile = tile(tileWidth, "Coins", coinText(sessionCoins));
		if (sessionCoins != null) {
			Double perHour = session.perHour(sessionCoins, session.end());
			withTooltip(coinsTile, List.of(Component.literal("§fCoins this session"),
					Component.literal("§7Per shaft " + CoinBreakdown.coins(sessionCoins / session.shafts())),
					Component.literal("§7Per hour " + (perHour == null ? "§8after " + JournalStats.MIN_RATE_MILLIS / 60_000 + " minutes"
							: CoinBreakdown.coins(perHour)))));
		}
		tiles.child(coinsTile);
		content.child(tiles);

		FlowLayout main = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
		main.gap(GAP);

		long slowest = session.entries().stream().filter(e -> e.searchMillis() != null)
				.mapToLong(JournalEntry::searchMillis).max().orElse(1);
		int detailWidth = Math.clamp(width * 30 / 100, 140, 230);
		List<Double> shaftCoins = session.entries().stream().map(e -> MoneyStats.net(Surveyor.MONEY.value(e))).toList();
		List<Column> measured = measured(SHAFT_COLUMNS, "inside",
				session.entries().stream().map(e -> Format.duration(e.durationMillis())).toList());
		measured = measured(measured, "corpses", session.entries().stream().map(JournalScreen::corpses).toList());
		measured = measured(measured, "coins", shaftCoins.stream().map(JournalScreen::coinText).toList());
		if (session.entries().stream().allMatch(e -> campfires(e) == 0)) {
			measured = measured.stream().filter(c -> !c.key().equals("campfire")).toList();
		} else {
			measured = measured(measured, "campfire", session.entries().stream().map(JournalScreen::campfireCell).toList());
		}
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
					case "inside" -> cell(column, "§f" + Format.duration(entry.durationMillis()));
					case "corpses" -> cell(column, corpses(entry));
					case "powder" -> cell(column, "§f" + Format.number(JournalStats.powder(entry)));
					case "coins" -> cell(column, coinText(shaftCoins.get(i)));
					case "campfire" -> cell(column, campfireCell(entry));
					default -> cell(column, entry.gems() == null ? "§8-" : "§f≈" + Format.decimal(entry.gems().fineEquivalent()));
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
		KeptScroll detailScroll = scroll(Sizing.fixed(detailWidth), Sizing.fill(100), detail);
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
		ShaftValue value = Surveyor.MONEY.value(entry);

		String id = entry.shaftId() == null ? "Unknown shaft" : entry.shaftId();
		detail.child(wrapped("§b" + id + (ShaftNames.CRYSTAL.equals(entry.variant()) ? " §7(crystal)" : "")));
		detail.child(spacer(4));
		detail.child(keyValue("Entered", "§f" + time(entry.enteredAt())));
		detail.child(keyValue("Exit", "§f" + SummaryText.exitName(entry.exitReason())));
		detail.child(keyValue("Spawned in", entry.searchMillis() == null ? "§8not timed" : spawn(entry.searchMillis())));
		detail.child(keyValue("Inside", "§f" + Format.duration(entry.durationMillis())));
		if (!entry.mayhemBuffs().isEmpty()) detail.child(keyValue("Mayhem", "§e" + String.join(", ", entry.mayhemBuffs())));
		String powder = "§b" + Format.number(JournalStats.powder(entry));
		if (entry.corpsePowder() > 0 && entry.exitPowder() > 0) {
			powder += " §8(" + Format.number(entry.corpsePowder()) + " corpses + " + Format.number(entry.exitPowder()) + " exit)";
		}
		detail.child(keyValue("Powder", powder));
		if (campfires(entry) > 0) {
			String colds = colds(entry);
			detail.child(keyValue("Campfires", "§f" + campfires(entry) + (colds == null ? "" : " §7at " + colds)));
		}
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
				for (ItemCount part : loot.gems().parts()) tooltip.add(Component.literal("§7" + Format.number(part.count()) + " " + part.item()));
				// parts sorted, first = most common
				grid.child(slot(loot.gems().parts().getFirst().item(), compact(loot.gems().fine()), tooltip));
			}
			for (ItemCount item : loot.items()) {
				grid.child(slot(item.item(), Format.number(item.count()),
						List.of(Component.literal("§f" + item.item()), Component.literal("§7× " + Format.number(item.count())))));
			}
			detail.child(grid);
		}

		detail.child(section("Gems mined"));
		if (entry.gems() == null) {
			detail.child(wrapped("§8None"));
		} else {
			detail.child(wrapped("§f≈" + Format.decimal(entry.gems().fineEquivalent()) + " Fine " + entry.gems().gem()));
			if (entry.pristineProcs() > 0) detail.child(wrapped("§8" + entry.pristineProcs() + " pristine procs"));
		}
		coinsSection(entry, value);
	}

	private void coinsSection(JournalEntry entry, ShaftValue value) {
		boolean worth = value != null && !value.lines().isEmpty();
		FlowLayout heading = section("Coins" + (worth ? " " + CoinBreakdown.coins(value.net()) : ""));
		detail.child(heading);
		if (value == null) {
			detail.child(wrapped("§8No prices yet. They load when you enter a shaft or open the journal."));
			return;
		}
		if (value.lines().isEmpty()) {
			detail.child(wrapped("§8Nothing worth coins"));
			return;
		}
		MoneySettings settings = Surveyor.MONEY.settings();
		boolean ironman = settings.isIronman();
		for (Part part : Part.values()) {
			List<Line> lines = value.lines().stream().filter(l -> l.part() == part).toList();
			if (lines.isEmpty()) continue;
			// ironman keys + campfires cost 0, show them as used
			boolean used = ironman && (part == Part.KEYS || part == Part.CAMPFIRES);
			boolean priced = lines.stream().anyMatch(l -> l.coins() != null);
			List<Component> tooltip = new ArrayList<>();
			tooltip.add(Component.literal("§f" + part.displayName()));
			for (Line line : lines) {
				tooltip.add(Component.literal("§7" + count(line) + " " + line.item() + "  "
						+ (used ? "§8used" : line.coins() == null ? CoinBreakdown.noPrice(ironman) : CoinBreakdown.coins(line.coins()))));
			}
			String total = used ? "§7" + Math.round(lines.stream().mapToDouble(Line::count).sum()) + " used"
					: priced ? CoinBreakdown.coins(value.total(part)) : CoinBreakdown.noPrice(ironman);
			FlowLayout row = tipRow(part.displayName(), total, tooltip);
			// indented = breakdown of the net above
			row.margins(Insets.left(INDENT));
			detail.child(row);
		}
		List<Component> how = ironman
				? List.of(Component.literal("§fHow coins are counted"),
						Component.literal("§7Ironman: NPC sell prices"),
						Component.literal("§7Gems as Fine"),
						Component.literal("§7Keys and campfires cost nothing"),
						Component.literal("§8Change it in Settings"))
				: List.of(Component.literal("§fHow coins are counted"),
						Component.literal("§7Gems as " + (settings.gemTier() == MoneySettings.GemTier.FINE ? "Fine" : "Flawless")),
						Component.literal("§7Sold by " + (settings.sellPrice() == MoneySettings.SellPrice.INSTA_SELL ? "insta-sell" : "sell offer")
								+ ", " + Setting.Number.format(settings.bazaarTax()) + "% Bazaar tax"),
						Component.literal("§7Keys and campfires at insta-buy"),
						Component.literal("§8Change it in Settings"));
		withTooltip(heading, how);
		int unpriced = value.unpriced().size();
		if (unpriced > 0) detail.child(wrapped(CoinBreakdown.unpriced(unpriced, ironman)));
		boolean saved = Surveyor.MONEY.usesSavedPrices(entry);
		long end = entry.enteredAt() + entry.durationMillis();
		detail.child(wrapped("§8" + (saved ? "Prices from " + day(end) + " " + clock(end) : "Today's prices")));
	}

	// cold at use, count for shafts from before 0.22.0 or when some werent used
	private static String campfireCell(JournalEntry entry) {
		int count = campfires(entry);
		if (count == 0) return "§8-";
		String colds = colds(entry);
		if (colds == null) return "§f" + count;
		return count > entry.campfireColds().size() ? "§f" + count + " §7at " + colds : colds;
	}

	// placed or used, whichever saw more
	private static int campfires(JournalEntry entry) {
		return Math.max(entry.campfiresAt().size(), entry.campfireColds() == null ? 0 : entry.campfireColds().size());
	}

	// null = no cold saved
	private static String colds(JournalEntry entry) {
		if (entry.campfireColds() == null) return null;
		List<String> colds = entry.campfireColds().stream().map(c -> c == null ? "§8?" : "§b" + c + "❄").toList();
		return colds.isEmpty() ? null : String.join("§7, ", colds);
	}

	private static FlowLayout tipRow(String key, String value, List<Component> tooltip) {
		return withTooltip(keyValue(key, value), tooltip);
	}

	private static String count(Line line) {
		if (line.part() == Part.MINED) return "≈" + Format.decimal(line.count());
		return Format.number(Math.round(line.count()));
	}

	private static String lootTitle(List<CorpseLoot> loots) {
		if (loots.isEmpty()) return "Corpse loot";
		Map<String, Integer> byType = new LinkedHashMap<>();
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
			return coins == null ? null : JournalStats.perHour(coins, millis);
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
		List<Double> sessionCoins = sessions.stream().map(s -> MoneyStats.coins(s.entries(), Surveyor.MONEY::value)).toList();
		List<Double> coinRates = new ArrayList<>();
		for (int i = 0; i < sessions.size(); i++) {
			Double coins = sessionCoins.get(i);
			coinRates.add(coins == null ? null : sessions.get(i).perHour(coins, sessions.get(i).end()));
		}
		List<Day> days = days(sessionCoins);
		List<String> coinTexts = new ArrayList<>(sessionCoins.stream().map(JournalScreen::coinText).toList());
		List<String> rateTexts = new ArrayList<>(coinRates.stream().map(JournalScreen::rateText).toList());
		// day rows only drawn by day
		if (byDay) {
			for (Day day : days) {
				coinTexts.add(coinText(day.coins()));
				rateTexts.add(dayRate(day));
			}
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
						case "length" -> cell(column, "§f" + Format.duration(session.lengthMillis()));
						case "shafts" -> cell(column, "§f" + session.shafts());
						case "spawn" -> barCell(column, session.averageSearchMillis(), slowest);
						case "rate" -> cell(column, rate == null ? "§8-" : "§f" + Format.decimal(rate));
						case "powder" -> cell(column, "§b" + Format.number(session.powder()));
						case "coins" -> cell(column, coinText(sessionCoins.get(i)));
						case "coinRate" -> cell(column, rateText(coinRates.get(i)));
						default -> cell(column, "§9" + session.looted(CorpseType.LAPIS.label()));
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
				case "length" -> cell(column, "§f" + Format.duration(day.millis()));
				case "shafts" -> cell(column, "§f" + day.shafts());
				case "spawn" -> cell(column, "§7" + count + (count == 1 ? " session" : " sessions"));
				case "powder" -> cell(column, "§b" + Format.number(day.powder()));
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
		Double perHour = day.perHour();
		return rateText(perHour) + (perHour == null ? "" : " §8(avg)");
	}

	private void buildStatsTab(FlowLayout content) {
		boolean all = statsRange == StatsRange.ALL_TIME;
		List<SessionSummary> rangeSessions = statsRange.sessions(sessions, System.currentTimeMillis());
		List<JournalEntry> rangeEntries = all ? entries : StatsRange.entries(rangeSessions);
		statsShafts = rangeEntries.size();
		FlowLayout columns = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
		columns.gap(GAP);
		FlowLayout totals = column();
		totals.child(rangeHeader());
		if (rangeEntries.isEmpty()) {
			totals.child(wrapped("§8No shafts in this range"));
			columns.child(scrollColumn(Sizing.fill(STATS_LEFT), totals));
			columns.child(scrollColumn(Sizing.expand(), column()));
			columns.child(scrollColumn(Sizing.fill(STATS_RIGHT), column()));
			content.child(columns);
			return;
		}
		AllTime stats = JournalStats.allTime(rangeEntries);
		MoneyStats money = MoneyStats.of(rangeEntries, rangeSessions, Surveyor.MONEY::value, Surveyor.MONEY.settings().isIronman());

		totals.child(coinsTotal(money, stats));
		totals.child(keyValue("Powder", "§b" + Format.number(stats.powder())));
		FlowLayout shafts = keyValue("Shafts", "§f" + stats.shafts());
		shafts.margins(Insets.top(LINE_GAP));
		totals.child(shafts);
		totals.child(keyValue("Sessions", "§f" + stats.sessions()));
		totals.child(keyValue("Time shafting", "§f" + Format.duration(stats.timeMillis())));
		JournalStats.Campfires campfires = JournalStats.campfires(rangeEntries);
		if (campfires.placed() > 0) {
			Double cold = campfires.averageCold();
			totals.child(keyValue("Campfires", "§f" + campfires.placed()
					+ (cold == null ? "" : " §7avg at §b" + Math.round(cold) + "❄")));
		}
		totals.child(section("Corpses looted"));
		for (String type : stats.corpsesSeen().keySet()) {
			totals.child(corpseRow(type, stats.looted().getOrDefault(type, 0), money, stats.drops().get(type)));
		}
		totals.child(section("Personal bests"));
		JournalEntry fastest = stats.fastest();
		totals.child(record("Fastest spawn", fastest == null ? null : spawn(fastest.searchMillis()),
				fastest == null ? null : () -> openShaft(fastest), fastest == null ? null : fastest.shaftId()));
		SessionSummary fastestSession = stats.fastestSession();
		totals.child(record("Fastest session", fastestSession == null ? null : spawn(fastestSession.averageSearchMillis()) + " §7avg",
				fastestSession == null ? null : () -> openSession(fastestSession), fastestSession == null ? null : sessionName(fastestSession)));
		SessionSummary bestRate = stats.bestRate();
		totals.child(record("Shafts / h", bestRate == null ? null : "§f" + Format.decimal(bestRate.shaftsPerHour()),
				bestRate == null ? null : () -> openSession(bestRate), bestRate == null ? null : sessionName(bestRate)));
		SessionSummary mostShafts = stats.mostShafts();
		totals.child(record("Shafts / session", mostShafts == null ? null : "§f" + mostShafts.shafts(),
				mostShafts == null ? null : () -> openSession(mostShafts), mostShafts == null ? null : sessionName(mostShafts)));
		JournalStats.Best lapis = stats.mostLapis();
		totals.child(record("Lapis / shaft", lapis == null ? null : "§9" + lapis.value(),
				lapis == null ? null : () -> openShaft(lapis.entry()), lapis == null ? null : lapis.entry().shaftId()));
		JournalStats.Best powder = stats.mostPowder();
		totals.child(record("Powder / shaft", powder == null ? null : "§b" + Format.number(powder.value()),
				powder == null ? null : () -> openShaft(powder.entry()), powder == null ? null : powder.entry().shaftId()));
		JournalEntry gems = stats.mostGems();
		totals.child(record("Gems / shaft", gems == null ? null : "§f≈" + Format.decimal(gems.gems().fineEquivalent()) + " Fine",
				gems == null ? null : () -> openShaft(gems), gems == null ? null : gems.shaftId()));
		MoneyStats.Best richest = money.bestShaft();
		totals.child(record("Coins / shaft", richest == null ? null : CoinBreakdown.coins(richest.coins()),
				richest == null ? null : () -> openShaft(richest.entry()), richest == null ? null : richest.entry().shaftId()));
		MoneyStats.BestRate coinRate = money.bestRate();
		totals.child(record("Coins / h", coinRate == null ? null : CoinBreakdown.coins(coinRate.perHour()),
				coinRate == null ? null : () -> openSession(coinRate.session()), coinRate == null ? null : sessionName(coinRate.session())));
		columns.child(scrollColumn(Sizing.fill(STATS_LEFT), totals));

		FlowLayout spawn = column();
		spawn.child(header("Spawn time"));
		if (all) {
			spawn.child(keyValue("All-time avg", stats.averageSearchMillis() == null ? "§8-"
					: "§e" + Format.duration(stats.averageSearchMillis())));
			Long recent = JournalStats.recentAverage(sessions, RECENT_SESSIONS);
			if (recent != null && stats.averageSearchMillis() != null && sessions.size() > RECENT_SESSIONS) {
				spawn.child(keyValue("Last " + RECENT_SESSIONS + " sessions",
						"§f" + Format.duration(recent) + change(recent, stats.averageSearchMillis())));
			}
		} else {
			Long average = stats.averageSearchMillis();
			Long allTime = JournalStats.averageSearch(entries);
			spawn.child(keyValue("Avg", average == null ? "§8-"
					: "§e" + Format.duration(average) + (allTime == null ? "" : change(average, allTime))));
			spawn.child(keyValue("All-time avg", allTime == null ? "§8-" : "§f" + Format.duration(allTime)));
		}
		String legend = all ? "all-time avg" : "avg";
		spawn.child(chartSection("Avg spawn per session", spawnChartType, step -> spawnChartType = spawnChartType.next(step)));
		spawn.child(spawnChart(stats.oldestFirst(), stats.averageSearchMillis(), legend));
		spawn.child(section("Corpse % per shaft"));
		for (String type : stats.corpsesSeen().keySet()) {
			int with = stats.shaftsWithCorpse().getOrDefault(type, 0);
			FlowLayout row = barRow(CorpseType.color(type) + type, with / (double) stats.shafts(),
					Math.round(100.0 * with / stats.shafts()) + "%", CorpseType.argb(type));
			row.tooltip(List.of(Component.literal("§f" + type + " corpses"),
					Component.literal("§7In " + with + " of " + stats.shafts() + " shafts"),
					Component.literal("§7" + String.format(Locale.ROOT, "%.2f", stats.corpsesSeen().get(type) / (double) stats.shafts())
							+ " per shaft on average")));
			spawn.child(row);
		}
		spawn.child(chartSection("Coins / h per session", coinChartType, step -> coinChartType = coinChartType.next(step)));
		Double averageRate = money.coins() == null ? null : JournalStats.perHour(money.coins(), stats.timeMillis());
		spawn.child(coinChart(stats.oldestFirst(), averageRate, money.coins() != null, legend, stats.timeMillis()));
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
				if (!variant.equals(ShaftNames.LITTLEFOOT)) tooltip.add(Component.literal("§7" + capitalized(ShaftNames.variantName(variant)) + " §f" + count));
			});
			row.tooltip(tooltip);
			odds.child(row);
		}
		typeCoins(odds, money);
		columns.child(scrollColumn(Sizing.fill(STATS_RIGHT), odds));
		content.child(columns);
	}

	private FlowLayout coinsTotal(MoneyStats money, AllTime stats) {
		FlowLayout row = keyValue("Coins", coinText(money.coins()));
		if (money.coins() == null) return row;
		Double perHour = JournalStats.perHour(money.coins(), stats.timeMillis());
		shiftTooltip(row, CoinBreakdown.detailed(money, perHour, stats.timeMillis(), tooltipHeight()),
				CoinBreakdown.basic(money, perHour, stats.timeMillis()));
		return row;
	}

	private FlowLayout corpseRow(String type, int looted, MoneyStats money, JournalStats.Drops drops) {
		MoneyStats.CorpseCoins corpse = money.corpses().get(type);
		String value = CorpseType.color(type) + looted;
		FlowLayout row = keyValue(type, corpse == null ? value : value + " §7(" + CoinBreakdown.coins(corpse.each()) + " §7avg.)");
		if (corpse == null && drops == null) return row;
		return withTips(row, TooltipLines.of(CoinBreakdown.corpse(type, looted, corpse, drops, tooltipHeight(),
				Surveyor.MONEY.settings().isIronman())));
	}

	// best avg first, bar = share of the coins all types made
	private void typeCoins(FlowLayout column, MoneyStats money) {
		List<Map.Entry<String, MoneyStats.TypeCoins>> types = money.types().entrySet().stream()
				.sorted(Comparator.comparingDouble((Map.Entry<String, MoneyStats.TypeCoins> e) -> -e.getValue().each())).toList();
		if (types.isEmpty()) return;
		column.child(section("Avg coins per shaft"));
		double made = types.stream().mapToDouble(e -> Math.max(0, e.getValue().coins())).sum();
		int valueWidth = types.stream().mapToInt(e -> font.width(CoinBreakdown.coins(e.getValue().each()))).max().orElse(0);
		for (Map.Entry<String, MoneyStats.TypeCoins> type : types) {
			String name = shaftNames.name(type.getKey());
			Double share = made > 0 ? Math.max(0, type.getValue().coins()) / made : null;
			FlowLayout row = barRow(name, share == null ? 0 : share, CoinBreakdown.coins(type.getValue().each()),
					0xFF000000 | shaftNames.rgb(type.getKey()), valueWidth);
			column.child(withTips(row, TooltipLines.of(CoinBreakdown.shaftType(name, type.getValue(), share,
					v -> capitalized(ShaftNames.variantName(v))))));
		}
	}

	private FlowLayout rangeHeader() {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.surface(Surface.flat(0x14FFFFFF));
		row.padding(Insets.of(2, 2, 4, 1));
		row.margins(Insets.bottom(4));
		LabelComponent name = label("§7Range §e" + statsRange.displayName());
		name.horizontalSizing(Sizing.expand());
		row.child(name);
		row.child(cycler(statsRange.ordinal(), StatsRange.values().length, step -> statsRange = statsRange.next(step)));
		List<Component> tooltip = List.of(Component.literal("§fTime range"),
				Component.literal("§7Which sessions the whole Stats tab counts"),
				Component.literal("§8Click the arrows to cycle"));
		applyTooltip(row, tooltip);
		return row;
	}

	private static void applyTooltip(UIComponent component, List<Component> tooltip) {
		component.tooltip(tooltip);
		if (component instanceof ParentUIComponent parent) {
			for (UIComponent child : parent.children()) applyTooltip(child, tooltip);
		}
	}

	// dots show where u are, arrows wrap around
	private FlowLayout cycler(int index, int count, IntConsumer step) {
		FlowLayout cycler = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		cycler.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout dots = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		dots.gap(2);
		dots.margins(Insets.right(3));
		for (int i = 0; i < count; i++) {
			BoxComponent dot = UIComponents.box(Sizing.fixed(3), Sizing.fixed(3));
			dot.fill(true);
			dot.color(Color.ofArgb(i == index ? 0xFFFFFF55 : 0xFF555555));
			dots.child(dot);
		}
		cycler.child(dots);
		cycler.child(cyclerArrow(PREV_ARROW, () -> step.accept(-1)));
		cycler.child(cyclerArrow(OPEN_ARROW, () -> step.accept(1)));
		return cycler;
	}

	// padding = bigger click area
	private UIComponent cyclerArrow(Identifier texture, Runnable action) {
		FlowLayout arrow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		arrow.padding(Insets.of(2, 2, 3, 3));
		arrow.child(UIComponents.texture(texture, 0, 0, 3, 5, 3, 5).blend(true));
		arrow.cursorStyle(CursorStyle.HAND);
		arrow.mouseDown().subscribe((event, doubleClick) -> {
			action.run();
			rebuild(true);
			return true;
		});
		return arrow;
	}

	private FlowLayout chartSection(String title, ChartType type, IntConsumer step) {
		FlowLayout section = section(title);
		LabelComponent label = (LabelComponent) section.children().getLast();
		section.removeChild(label);
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.verticalAlignment(VerticalAlignment.CENTER);
		label.horizontalSizing(Sizing.expand());
		row.child(label);
		FlowLayout cycler = cycler(type.ordinal(), ChartType.values().length, step);
		applyTooltip(cycler, List.of(Component.literal("§fChart §e" + type.displayName()),
				Component.literal("§7Switches between bars and a line"), Component.literal("§8Click the arrows to cycle")));
		row.child(cycler);
		section.child(row);
		return section;
	}

	// durations show whole secs, <1s = same
	private static String change(long now, long before) {
		long difference = now - before;
		return Math.abs(difference) < 1000 ? " §7(same)"
				: difference < 0 ? " §7(§a" + Format.duration(-difference) + " faster§7)"
				: " §7(§c" + Format.duration(difference) + " slower§7)";
	}

	// room for the tooltip's own border
	private int tooltipHeight() {
		return height - 10;
	}

	private void shiftTooltip(FlowLayout row, List<String> normal, List<String> shifted) {
		ShiftTip tip = new ShiftTip(row, TooltipLines.of(normal), TooltipLines.of(shifted));
		shiftTips.add(tip);
		withTips(row, shiftShown ? tip.shifted() : tip.normal());
	}

	private static FlowLayout withTips(FlowLayout row, List<ClientTooltipComponent> tooltip) {
		row.tooltip(tooltip);
		row.children().forEach(child -> child.tooltip(tooltip));
		return row;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean shift = minecraft.hasShiftDown();
		if (shift != shiftShown) {
			shiftShown = shift;
			for (ShiftTip tip : shiftTips) withTips(tip.row(), shift ? tip.shifted() : tip.normal());
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private static FlowLayout withTooltip(FlowLayout row, List<Component> tooltip) {
		row.tooltip(tooltip);
		row.children().forEach(child -> child.tooltip(tooltip));
		return row;
	}

	private static String coinText(Double coins) {
		return coinText(coins, null);
	}

	// gray so only the totals are gold
	private static String rateText(Double coins) {
		return coinText(coins, "§7");
	}

	// color null = gold, red when negative
	private static String coinText(Double coins, String color) {
		if (coins == null) return "§8-";
		if (Math.round(coins) == 0) return "§80";
		return color == null ? CoinBreakdown.coins(coins) : color + Coins.signed(coins);
	}

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

	private static double[] coinSteps() {
		double[] steps = new double[3 * 13];
		for (int i = 0; i < steps.length; i++) steps[i] = new double[] {1, 2, 5}[i % 3] * Math.pow(10, i / 3);
		return steps;
	}

	private record Bar(SessionSummary session, double value, int argb, List<Component> tooltip) {
	}

	private FlowLayout spawnChart(List<SessionSummary> oldestFirst, Long average, String legend) {
		List<Bar> bars = new ArrayList<>();
		for (SessionSummary session : oldestFirst) {
			if (session.averageSearchMillis() == null) continue;
			bars.add(new Bar(session, session.averageSearchMillis() / 1000.0, BAR_GREEN, List.of(
					Component.literal("§f" + day(session.start()) + " " + clock(session.start())),
					Component.literal("§7Avg spawn " + spawn(session.averageSearchMillis()) + " §7over " + session.timedSpawns() + " shafts"),
					Component.literal("§8Click to open"))));
		}
		return barChart(bars, spawnChartType, 0xFF55FF55, SPAWN_STEPS, seconds -> axisLabel(Math.round(seconds)),
				average == null ? null : average / 1000.0, 0xB0FFFF55, "§eYellow line§7: " + legend, ChartTips.spawn(oldestFirst),
				"§8No timed spawns yet");
	}

	private FlowLayout coinChart(List<SessionSummary> oldestFirst, Double average, boolean priced, String legend, long totalMillis) {
		List<Bar> bars = new ArrayList<>();
		List<ChartTips.SessionCoins> sessionCoins = new ArrayList<>();
		for (SessionSummary session : oldestFirst) {
			Double coins = MoneyStats.coins(session.entries(), Surveyor.MONEY::value);
			Double rate = coins == null ? null : session.perHour(coins, session.end());
			sessionCoins.add(new ChartTips.SessionCoins(coins, rate));
			if (rate == null) continue;
			bars.add(new Bar(session, rate, Math.round(rate) < 0 ? BAR_RED : BAR_GOLD, List.of(
					Component.literal("§f" + day(session.start()) + " " + clock(session.start())),
					Component.literal("§7Coins / h " + CoinBreakdown.coins(rate)),
					Component.literal("§7Coins " + coinText(coins) + " §7in " + Format.duration(session.lengthMillis())),
					Component.literal("§7Shafts §f" + session.shafts()),
					Component.literal("§8Click to open"))));
		}
		String empty = priced ? "§8Needs a session of " + JournalStats.MIN_RATE_MILLIS / 60_000 + "+ minutes" : "§8No prices yet";
		return barChart(bars, coinChartType, 0xFFFFAA00, COIN_STEPS, JournalScreen::axisCoins, average, 0xB0FFFFFF,
				"§fWhite line§7: " + legend, ChartTips.coins(sessionCoins, totalMillis), empty);
	}

	private static String axisCoins(double coins) {
		return Coins.whole(coins);
	}

	// up to 4 steps, bars under 0 hang down from the 0 line
	private FlowLayout barChart(List<Bar> all, ChartType type, int lineArgb, double[] steps, DoubleFunction<String> axisText,
			Double average, int averageArgb, String legend, List<String> averageTip, String empty) {
		FlowLayout wrapper = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		if (all.isEmpty()) {
			wrapper.child(label(empty));
			return wrapper;
		}
		int chartHeight = Math.clamp(height / 3, 50, 110);
		double highest = Math.max(0, all.stream().mapToDouble(Bar::value).max().orElse(0));
		double lowest = Math.min(0, all.stream().mapToDouble(Bar::value).min().orElse(0));
		double step = steps[steps.length - 1];
		for (double candidate : steps) {
			if (Math.ceil(highest / candidate) + Math.ceil(-lowest / candidate) <= 4) {
				step = candidate;
				break;
			}
		}
		double top = Math.ceil(highest / step) * step;
		double bottom = -Math.ceil(-lowest / step) * step;
		if (top == bottom) top = step;
		int axisWidth = 0;
		for (double value = bottom; value <= top + step / 2; value += step) axisWidth = Math.max(axisWidth, font.width(axisText.apply(value)));
		axisWidth += 2;

		int plotWidth = Math.max(60, width - 2 * PADDING - 2 * GAP - width * STATS_LEFT / 100 - width * STATS_RIGHT / 100
				- 2 * COLUMN_PADDING - axisWidth - AXIS_GAP);
		// display choice: bars up to 24 wide, gap grows w/ them
		int barWidth = Math.clamp((plotWidth - 2) * 4 / 5 / all.size(), 3, 24);
		int barGap = Math.clamp(barWidth / 4, 2, 6);
		int maxBars = Math.max(1, (plotWidth - 2) / (barWidth + barGap));
		List<Bar> shown = all.size() > maxBars ? all.subList(all.size() - maxBars, all.size()) : all;

		int plotTop = 4;
		int base = chartHeight - 5;
		int plotHeight = base - plotTop;
		double span = top - bottom;
		DoubleUnaryOperator y = value -> base - (int) (plotHeight * (value - bottom) / span);
		FlowLayout chart = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(chartHeight));
		chart.gap(AXIS_GAP);
		StackLayout axis = UIContainers.stack(Sizing.fixed(axisWidth), Sizing.fixed(chartHeight));
		StackLayout plot = UIContainers.stack(Sizing.expand(), Sizing.fixed(chartHeight));
		for (double value = bottom; value <= top + step / 2; value += step) {
			int lineY = (int) y.applyAsDouble(value);
			LabelComponent tick = label("§8" + axisText.apply(value));
			tick.horizontalSizing(Sizing.fixed(axisWidth));
			tick.horizontalTextAlignment(HorizontalAlignment.RIGHT);
			tick.positioning(Positioning.absolute(0, lineY - 4));
			axis.child(tick);
			plot.child(line(lineY, Math.abs(value) < step / 2 ? 0x40FFFFFF : 0x18FFFFFF));
		}
		int zero = (int) y.applyAsDouble(0);
		if (type == ChartType.LINE) {
			int[] xs = new int[shown.size()];
			int[] ys = new int[shown.size()];
			int[] dots = new int[shown.size()];
			for (int i = 0; i < shown.size(); i++) {
				xs[i] = 2 + i * (barWidth + barGap) + barWidth / 2;
				ys[i] = (int) y.applyAsDouble(shown.get(i).value());
				dots[i] = 0xFF000000 | shown.get(i).argb();
			}
			LinePlot line = new LinePlot(xs, ys, dots, lineArgb);
			line.positioning(Positioning.absolute(0, 0));
			plot.child(line);
		}
		for (int i = 0; i < shown.size(); i++) {
			Bar bar = shown.get(i);
			int end = (int) y.applyAsDouble(bar.value());
			int barHeight = Math.max(1, Math.abs(zero - end));
			BoxComponent box;
			if (type == ChartType.LINE) {
				// invisible column, easy to hover
				box = UIComponents.box(Sizing.fixed(barWidth + barGap), Sizing.fixed(base - plotTop + 1));
				box.color(Color.ofArgb(0));
				box.positioning(Positioning.absolute(2 + i * (barWidth + barGap) - barGap / 2, plotTop));
			} else {
				box = UIComponents.box(Sizing.fixed(barWidth), Sizing.fixed(barHeight));
				box.color(Color.ofArgb(bar.argb()));
				box.positioning(Positioning.absolute(2 + i * (barWidth + barGap), bar.value() < 0 ? zero + 1 : zero - barHeight));
			}
			box.fill(true);
			box.cursorStyle(CursorStyle.HAND);
			box.tooltip(bar.tooltip());
			box.mouseDown().subscribe((event, doubleClick) -> {
				openSession(bar.session());
				return true;
			});
			plot.child(box);
		}
		List<ClientTooltipComponent> averageTips = TooltipLines.of(averageTip);
		if (average != null) {
			int averageY = (int) y.applyAsDouble(Math.clamp(average, bottom, top));
			plot.child(line(averageY, averageArgb));
			if (!averageTips.isEmpty()) {
				// 1 px line is too thin to hover
				BoxComponent band = UIComponents.box(Sizing.fill(100), Sizing.fixed(5));
				band.color(Color.ofArgb(0));
				band.positioning(Positioning.absolute(0, averageY - 2));
				band.tooltip(averageTips);
				plot.child(band);
			}
		}
		chart.child(axis);
		chart.child(plot);
		wrapper.child(chart);

		StackLayout dates = UIContainers.stack(Sizing.fill(100), Sizing.fixed(10));
		LabelComponent first = label("§8" + day(shown.getFirst().session().start()));
		first.positioning(Positioning.absolute(axisWidth + AXIS_GAP + 2, 1));
		dates.child(first);
		if (shown.size() > 1) {
			String lastText = "§8" + day(shown.getLast().session().start());
			int lastX = axisWidth + AXIS_GAP + 2 + (shown.size() - 1) * (barWidth + barGap) + barWidth - font.width(lastText);
			int firstEnd = axisWidth + AXIS_GAP + 2 + font.width("§8" + day(shown.getFirst().session().start()));
			if (lastX > firstEnd + 6) {
				LabelComponent last = label(lastText);
				last.positioning(Positioning.absolute(lastX, 1));
				dates.child(last);
			}
		}
		wrapper.child(dates);
		if (average != null) {
			LabelComponent legendLabel = label(legend);
			if (!averageTips.isEmpty()) legendLabel.tooltip(averageTips);
			wrapper.child(legendLabel);
		}
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
		return barRow(name, fraction, "§f" + value, argb, 26);
	}

	private FlowLayout barRow(String name, double fraction, String value, int argb, int valueWidth) {
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
		LabelComponent valueLabel = label(value);
		valueLabel.horizontalSizing(Sizing.fixed(valueWidth));
		valueLabel.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		row.child(valueLabel);
		return row;
	}

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
		KeptScroll scroll = scroll(Sizing.fill(100), Sizing.expand(), rows);
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
		LabelComponent text = label("§f" + Format.duration(millis));
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

	private KeptScroll scrollColumn(Sizing width, FlowLayout column) {
		KeptScroll scroll = scroll(width, Sizing.fill(100), column);
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
			String color = count.looted() > 0 ? CorpseType.color(e.getKey()) : "§8";
			parts.add(color + count.looted() + "/" + count.total() + " " + e.getKey().charAt(0));
		}
		return parts.isEmpty() ? "§8-" : String.join(" ", parts);
	}

	private static String capitalized(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static String spawn(Long millis) {
		return millis == null ? "§8-" : "§a" + Format.duration(millis);
	}

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

	// room left for the date: top bar minus its items, gaps, back and the < > arrows
	// backWidth 0 = no back button
	private int navRoom(List<UIComponent> items, int backWidth) {
		// nav is one more bar item
		int used = 2 * PADDING + 2 * BAR_PADDING + BAR_GAP * items.size() + 2 * ARROW_WIDTH + 2 * BAR_GAP;
		for (UIComponent item : items) used += fixedWidth(item);
		if (backWidth > 0) used += backWidth + BAR_GAP;
		return width - used;
	}

	// fixed sizing only
	private static int fixedWidth(UIComponent component) {
		Insets margins = component.margins().get();
		return component.horizontalSizing().get().value + margins.left() + margins.right();
	}

	private static String fine(double value) {
		return value > 0 && value < 0.05 ? "<0.1" : Format.decimal(value);
	}

	private static String compact(double value) {
		if (value >= 9.95 || value == Math.rint(value)) return Format.number(Math.round(value));
		return fine(value);
	}
}
