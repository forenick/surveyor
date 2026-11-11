package dev.shafttools.screen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.shafttools.detection.CorpseLoot;
import dev.shafttools.detection.ItemCount;
import dev.shafttools.detection.TextPatterns;
import dev.shafttools.journal.JournalEntry;
import dev.shafttools.journal.JournalStats;
import dev.shafttools.journal.JournalStats.AllTime;
import dev.shafttools.journal.JournalStats.CorpseCount;
import dev.shafttools.journal.JournalStats.SessionSummary;
import dev.shafttools.journal.LootSummary;
import dev.shafttools.journal.SummaryText;
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

/**
 * The journal screen (mockup v2, approved by Nick on 2026-11-11): the Session tab (one session's
 * shafts and the selected shaft's details), the Sessions tab (every session) and the Stats tab
 * (all-time stats). It only reads the saved journal. All text goes through Minecraft's own font,
 * so resource-pack fonts apply.
 */
public final class JournalScreen extends BaseOwoScreen<FlowLayout> {
	private enum Tab { SESSION, SESSIONS, STATS }

	/** A table column. Columns with a higher {@code dropOrder} are hidden first when the table is too narrow; 0 never. */
	private record Column(String key, String title, int width, boolean right, int dropOrder) {
	}

	private static final Surface PANEL = Surface.flat(0xE8141414).and(Surface.outline(0x30FFFFFF));
	private static final Surface ROW_EVEN = Surface.flat(0x0DFFFFFF);
	private static final Surface ROW_HOVER = Surface.flat(0x1AFFFFFF);
	private static final Surface ROW_SELECTED = Surface.flat(0x26FFFFFF).and(Surface.outline(0x50FFFFFF));
	private static final Surface SLOT = Surface.flat(0xFF8B8B8B).and(Surface.outline(0xFF373737));
	private static final Color GREEN = Color.ofArgb(0xCC55FF55);
	private static final int ROW_HEIGHT = 12;
	private static final int SPAWN_BAR = 26;

	private static final List<Column> SHAFT_COLUMNS = List.of(
			new Column("number", "#", 14, false, 4), new Column("shaft", "Shaft", 44, false, 0),
			new Column("time", "Time", 32, false, 2), new Column("spawn", "Spawn", 66, false, 0),
			new Column("inside", "Inside", 32, true, 1), new Column("corpses", "Corpses", 92, false, 0),
			new Column("powder", "Powder", 44, true, 0), new Column("gems", "Gems", 40, true, 3));
	private static final List<Column> SESSION_COLUMNS = List.of(
			new Column("date", "Date", 56, false, 0), new Column("start", "Start", 32, false, 3),
			new Column("length", "Length", 44, true, 2), new Column("shafts", "Shafts", 36, true, 0),
			new Column("spawn", "Avg spawn", 78, false, 0), new Column("rate", "Per hour", 46, true, 1),
			new Column("powder", "Powder", 58, true, 0), new Column("lapis", "Lapis", 36, true, 4));

	private final List<JournalEntry> entries;
	private final List<SessionSummary> sessions;
	private final ItemIcons icons = new ItemIcons();
	private final ShaftNames shaftNames = ShaftNames.load();
	private Tab tab = Tab.SESSION;
	private int sessionIndex;
	private int selected;
	private FlowLayout root;
	private FlowLayout detail;
	private final List<FlowLayout> shaftRows = new ArrayList<>();

	/** @param entries every saved journal entry, in file order */
	public JournalScreen(List<JournalEntry> entries) {
		super(Component.literal("Shaft Journal"));
		this.entries = List.copyOf(entries);
		this.sessions = JournalStats.sessions(this.entries);
		selectLastShaft();
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
		root.surface(Surface.VANILLA_TRANSLUCENT);
		root.padding(Insets.of(8));
		root.gap(5);
		rebuild();
	}

	private void rebuild() {
		root.clearChildren();
		shaftRows.clear();
		detail = null;
		root.child(topBar());
		FlowLayout content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
		content.gap(5);
		if (entries.isEmpty()) {
			content.child(panelWith(label("§7No shafts yet. Each shaft shows up here after you leave it."),
					Sizing.fill(100), Sizing.content()));
		} else {
			switch (tab) {
				case SESSION -> buildSessionTab(content);
				case SESSIONS -> buildSessionsTab(content);
				case STATS -> buildStatsTab(content);
			}
		}
		root.child(content);
		FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		footer.gap(16);
		switch (tab) {
			case SESSION -> {
				footer.child(label("§8Click a shaft for details"));
				footer.child(label("§8< > earlier sessions"));
			}
			case SESSIONS -> footer.child(label("§8Click a session to open it"));
			case STATS -> {
				footer.child(label("§8Counted from your own " + entries.size() + " shafts"));
				footer.child(label("§8Hover a row for details"));
				footer.child(label("§8Click an arrow to open it"));
			}
		}
		root.child(footer);
	}

	// ---- top bar ----

	private FlowLayout topBar() {
		FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(PANEL);
		bar.padding(Insets.of(4));
		bar.gap(4);
		bar.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent title = label("§fShaft §bJournal");
		title.margins(Insets.of(0, 0, 2, 8));
		bar.child(title);
		bar.child(tabButton("Session", Tab.SESSION));
		bar.child(tabButton("Sessions", Tab.SESSIONS));
		bar.child(tabButton("Stats", Tab.STATS));

		FlowLayout nav = UIContainers.horizontalFlow(Sizing.expand(), Sizing.content());
		nav.horizontalAlignment(HorizontalAlignment.RIGHT);
		nav.verticalAlignment(VerticalAlignment.CENTER);
		nav.gap(4);
		if (tab == Tab.SESSION && !sessions.isEmpty()) {
			nav.child(button("<", sessionIndex < sessions.size() - 1, () -> showSession(sessionIndex + 1)));
			nav.child(label("§f" + sessionRange(sessions.get(sessionIndex))));
			nav.child(button(">", sessionIndex > 0, () -> showSession(sessionIndex - 1)));
		}
		bar.child(nav);
		return bar;
	}

	private ButtonComponent tabButton(String name, Tab target) {
		ButtonComponent button = UIComponents.button(Component.literal((tab == target ? "§e" : "§f") + name), b -> {
			tab = target;
			rebuild();
		});
		button.horizontalSizing(Sizing.content(6));
		return button;
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
		tab = Tab.SESSION;
		rebuild();
	}

	/** Opens the session that holds this shaft, with the shaft selected. */
	private void openShaft(JournalEntry entry) {
		for (int i = 0; i < sessions.size(); i++) {
			int index = sessions.get(i).entries().indexOf(entry);
			if (index >= 0) {
				sessionIndex = i;
				selected = index;
				tab = Tab.SESSION;
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

	// ---- Session tab ----

	private void buildSessionTab(FlowLayout content) {
		SessionSummary session = sessions.get(sessionIndex);
		Double rate = session.shaftsPerHour();
		FlowLayout tiles = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		tiles.gap(5);
		int tileWidth = (width - 16 - 4 * 5) / 5;
		tiles.child(tile(tileWidth, "Shafts", "§f" + session.shafts()));
		tiles.child(tile(tileWidth, "Avg spawn", spawn(session.averageSearchMillis())));
		tiles.child(tile(tileWidth, "Per hour", rate == null ? "§8-" : "§f" + decimal(rate)));
		tiles.child(tile(tileWidth, "Powder", "§b" + number(session.powder())));
		tiles.child(tile(tileWidth, "Lapis looted", "§9" + session.looted("Lapis")));
		content.child(tiles);

		FlowLayout main = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.expand());
		main.gap(5);

		long slowest = session.entries().stream().filter(e -> e.searchMillis() != null)
				.mapToLong(JournalEntry::searchMillis).max().orElse(1);
		int detailWidth = Math.clamp(width * 30 / 100, 140, 230);
		List<Column> columns = fit(SHAFT_COLUMNS, width - 16 - 5 - detailWidth - TABLE_CHROME);
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
		detailScroll.padding(Insets.of(6));
		main.child(detailScroll);
		content.child(main);
		fillDetail();
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

		String id = entry.shaftId() == null ? "Unknown shaft" : entry.shaftId();
		detail.child(wrapped("§b" + id + ("C".equals(JournalStats.variant(entry)) ? "  §dCrystal" : "")));
		detail.child(spacer(4));
		detail.child(keyValue("Entered", "§f" + time(entry.enteredAt())));
		detail.child(keyValue("Exit", "§f" + exit(entry.exit())));
		detail.child(keyValue("Spawned in", entry.searchMillis() == null ? "§8not timed" : spawn(entry.searchMillis())));
		detail.child(keyValue("Inside", "§f" + SummaryText.duration(entry.durationMillis())));
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
				tooltip.add(Component.literal("§fGemstones §7≈ " + decimal(loot.gems().fine()) + " Fine"));
				for (ItemCount part : loot.gems().parts()) tooltip.add(Component.literal("§7" + number(part.count()) + " " + part.item()));
				grid.child(slot(IconRules.GEMSTONES, compact(loot.gems().fine()), tooltip));
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

	// ---- Sessions tab ----

	private void buildSessionsTab(FlowLayout content) {
		long slowest = sessions.stream().map(SessionSummary::averageSearchMillis).filter(a -> a != null)
				.mapToLong(Long::longValue).max().orElse(1);
		List<Column> columns = fit(SESSION_COLUMNS, width - 16 - TABLE_CHROME);
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		for (int i = 0; i < sessions.size(); i++) {
			SessionSummary session = sessions.get(i);
			Double rate = session.shaftsPerHour();
			FlowLayout row = row(i);
			for (Column column : columns) {
				row.child(switch (column.key()) {
					case "date" -> cell(column, "§f" + day(session.start()));
					case "start" -> cell(column, "§7" + clock(session.start()));
					case "length" -> cell(column, "§f" + SummaryText.duration(session.lengthMillis()));
					case "shafts" -> cell(column, "§f" + session.shafts());
					case "spawn" -> barCell(column, session.averageSearchMillis(), slowest);
					case "rate" -> cell(column, rate == null ? "§8-" : "§f" + decimal(rate));
					case "powder" -> cell(column, "§b" + number(session.powder()));
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
		content.child(table(columns, rows));
	}

	// ---- Stats tab ----

	private void buildStatsTab(FlowLayout content) {
		AllTime stats = JournalStats.allTime(entries);
		FlowLayout columns = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
		columns.gap(5);

		FlowLayout totals = column();
		totals.child(header("All time"));
		totals.child(keyValue("Shafts", "§f" + stats.shafts()));
		totals.child(keyValue("Sessions", "§f" + stats.sessions()));
		totals.child(keyValue("Time shafting", "§f" + SummaryText.duration(stats.timeMillis())));
		totals.child(keyValue("Powder", "§b" + number(stats.powder())));
		totals.child(keyValue("Coins", SOON));
		totals.child(section("Corpses looted"));
		for (String type : corpseTypes(stats.corpsesSeen())) {
			// Coin value of the loot comes with prices.
			totals.child(keyValue(type, corpseColor(type) + stats.looted().getOrDefault(type, 0) + " §e(soon)"));
		}
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
		totals.child(record("Coins / shaft", SOON, null, null));
		totals.child(record("Coins / h", SOON, null, null));
		columns.child(scrollColumn(Sizing.fill(32), totals));

		FlowLayout spawn = column();
		spawn.child(header("Spawn time"));
		spawn.child(keyValue("All-time avg", stats.averageSearchMillis() == null ? "§8-"
				: "§e" + SummaryText.duration(stats.averageSearchMillis())));
		Long recent = JournalStats.recentAverage(sessions, RECENT_SESSIONS);
		if (recent != null && stats.averageSearchMillis() != null && sessions.size() > RECENT_SESSIONS) {
			long difference = recent - stats.averageSearchMillis();
			String change = difference <= 0 ? " §7(§a" + SummaryText.duration(-difference) + " faster§7)"
					: " §7(§c" + SummaryText.duration(difference) + " slower§7)";
			spawn.child(keyValue("Last " + RECENT_SESSIONS + " sessions", "§f" + SummaryText.duration(recent) + change));
		}
		spawn.child(section("Avg spawn per session"));
		spawn.child(chart(stats.oldestFirst(), stats.averageSearchMillis()));
		spawn.child(section("Corpse % per shaft"));
		for (String type : corpseTypes(stats.corpsesSeen())) {
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
		columns.child(scrollColumn(Sizing.fill(27), odds));
		content.child(columns);
	}

	private static final String SOON = "§6Soon";
	private static final net.minecraft.resources.Identifier OPEN_ARROW =
			net.minecraft.resources.Identifier.fromNamespaceAndPath("shafttools", "textures/gui/open_arrow.png");
	private static final int RECENT_SESSIONS = 5;

	/**
	 * A personal-best row: the value and an arrow that opens the shaft or session it belongs to.
	 * Without a record yet, shows a dash.
	 */
	private FlowLayout record(String key, String value, Runnable open, String target) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(4);
		row.verticalAlignment(VerticalAlignment.TOP);
		LabelComponent keyLabel = label("§7" + key);
		keyLabel.horizontalSizing(Sizing.expand());
		row.child(keyLabel);
		row.child(label(value == null ? "§8-" : value));
		if (open != null) {
			// A 3x5 icon inside padding, so the click area is bigger than the icon.
			FlowLayout arrow = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			arrow.padding(Insets.of(2, 2, 2, 1));
			arrow.margins(Insets.top(-1));
			arrow.child(UIComponents.texture(OPEN_ARROW, 0, 0, 3, 5, 3, 5).blend(true));
			arrow.cursorStyle(CursorStyle.HAND);
			arrow.tooltip(Component.literal("§7Open " + target));
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

	/** Spawn-time axis steps; the smallest that gives at most four lines is used. */
	private static final long[] AXIS_STEPS = {5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};

	private FlowLayout chart(List<SessionSummary> oldestFirst, Long average) {
		List<SessionSummary> timed = oldestFirst.stream().filter(s -> s.averageSearchMillis() != null).toList();
		FlowLayout wrapper = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		if (timed.isEmpty()) {
			wrapper.child(label("§8No timed spawns yet"));
			return wrapper;
		}
		int chartHeight = Math.clamp(height / 3, 50, 110);
		long slowestSeconds = Math.max(1, timed.stream().mapToLong(SessionSummary::averageSearchMillis).max().orElse(1) / 1000);
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

		// The middle column's width, minus the axis; older sessions drop off the left when they don't fit.
		int plotWidth = Math.max(60, width - 16 - 10 - width * 32 / 100 - width * 27 / 100 - 12 - axisWidth - 3);
		int barWidth = Math.clamp((plotWidth - 2) / timed.size() - 2, 3, 12);
		int maxBars = Math.max(1, (plotWidth - 2) / (barWidth + 2));
		if (timed.size() > maxBars) timed = timed.subList(timed.size() - maxBars, timed.size());

		int top = 4;
		int base = chartHeight - 5;
		int plotHeight = base - top;
		FlowLayout chart = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(chartHeight));
		chart.gap(3);
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
		bars.gap(2);
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

		// Dates under the first and last bar.
		StackLayout dates = UIContainers.stack(Sizing.fill(100), Sizing.fixed(10));
		LabelComponent first = label("§8" + day(timed.getFirst().start()));
		first.positioning(Positioning.absolute(axisWidth + 3 + 2, 1));
		dates.child(first);
		if (timed.size() > 1) {
			String lastText = "§8" + day(timed.getLast().start());
			int lastX = axisWidth + 3 + 2 + (timed.size() - 1) * (barWidth + 2) + barWidth - font.width(lastText);
			int firstEnd = axisWidth + 5 + font.width("§8" + day(timed.getFirst().start()));
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

	/** "0", "30s", "1m", "1m 30s". */
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

	// ---- building blocks ----

	/** Table padding, row padding and the scrollbar, around the columns. */
	private static final int TABLE_CHROME = 10 + 4 + 4;

	/** Space between table columns. */
	private static final int COLUMN_GAP = 6;

	/** Drops columns, highest drop order first, until the rest fit in {@code available} pixels. */
	private static List<Column> fit(List<Column> columns, int available) {
		List<Column> visible = new ArrayList<>(columns);
		while (visible.stream().mapToInt(Column::width).sum() + COLUMN_GAP * (visible.size() - 1) > available) {
			Column drop = visible.stream().filter(c -> c.dropOrder() > 0)
					.max(Comparator.comparingInt(Column::dropOrder)).orElse(null);
			if (drop == null) break;
			visible.remove(drop);
		}
		return visible;
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
		if (tab == Tab.SESSION && index == selected) return ROW_SELECTED;
		if (hovered) return ROW_HOVER;
		return index % 2 == 1 ? ROW_EVEN : Surface.BLANK;
	}

	private static LabelComponent cell(Column column, String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fixed(column.width()));
		if (column.right()) label.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		return label;
	}

	private static FlowLayout barCell(Column column, Long millis, long slowest) {
		FlowLayout cell = UIContainers.horizontalFlow(Sizing.fixed(column.width()), Sizing.content());
		cell.verticalAlignment(VerticalAlignment.CENTER);
		cell.gap(3);
		if (millis == null) {
			cell.child(label("§8-"));
			return cell;
		}
		LabelComponent text = label("§f" + SummaryText.duration(millis));
		text.horizontalSizing(Sizing.fixed(column.width() - SPAWN_BAR - 3));
		cell.child(text);
		BoxComponent bar = UIComponents.box(Sizing.fixed(Math.max(1, (int) (SPAWN_BAR * millis / Math.max(1, slowest)))), Sizing.fixed(3));
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
		tile.child(label("§l" + value));
		return tile;
	}

	private static FlowLayout column() {
		FlowLayout column = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		column.gap(2);
		return column;
	}

	private static ScrollContainer<FlowLayout> scrollColumn(Sizing width, FlowLayout column) {
		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(width, Sizing.fill(100), column);
		scroll.surface(PANEL);
		scroll.padding(Insets.of(6));
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
		return UIComponents.label(Component.literal(text));
	}

	/** A label that wraps to its parent's width instead of running past it. */
	private static LabelComponent wrapped(String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fill(100));
		return label;
	}

	// ---- text ----

	private static String corpses(JournalEntry entry) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, CorpseCount> e : JournalStats.corpses(entry).entrySet()) {
			CorpseCount count = e.getValue();
			String color = count.looted() > 0 ? corpseColor(e.getKey()) : "§8";
			parts.add(color + count.looted() + "/" + count.total() + " " + e.getKey().charAt(0));
		}
		return parts.isEmpty() ? "§8-" : String.join(" ", parts);
	}

	private static List<String> corpseTypes(Map<String, Integer> seen) {
		List<String> types = new ArrayList<>(seen.keySet());
		types.sort(Comparator.comparingInt(t -> JournalStats.CORPSE_ORDER.contains(t) ? JournalStats.CORPSE_ORDER.indexOf(t) : 99));
		return types;
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

	private static String exit(String exit) {
		return switch (exit) {
			case "BYE" -> "Took the exit";
			case "WARP" -> "Warped out";
			case "DEATH" -> "Froze";
			default -> "Unknown";
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

	private static String day(long millis) {
		LocalDate date = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate();
		LocalDate today = LocalDate.now(ZONE);
		if (date.equals(today)) return "Today";
		if (date.equals(today.minusDays(1))) return "Yesterday";
		return (date.getYear() == today.getYear() ? DAY : DAY_YEAR).format(date);
	}

	private static String sessionRange(SessionSummary session) {
		return day(session.start()) + " " + clock(session.start()) + " to " + clock(session.end());
	}

	private static String number(long value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	private static String decimal(double value) {
		return String.format(Locale.ROOT, "%.1f", value);
	}

	/** Slot counts: whole numbers as is, small fractions with one decimal. */
	private static String compact(double value) {
		if (value >= 10 || value == Math.rint(value)) return number(Math.round(value));
		return decimal(value);
	}
}
