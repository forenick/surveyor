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
		root.child(label(switch (tab) {
			case SESSION -> "§8Click a shaft for details  ·  < > earlier sessions";
			case SESSIONS -> "§8Click a session to open it";
			case STATS -> "§8Counted from your own " + entries.size() + " shafts";
		}));
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
		detail.child(wrapped("§b" + id + ("C".equals(JournalStats.variant(entry)) ? " §8· §dCrystal" : "")));
		detail.child(wrapped("§7" + time(entry.enteredAt()) + " · " + exit(entry.exit())));
		detail.child(spacer(4));
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
		return "Corpse loot · " + String.join(", ", parts);
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
		totals.child(section("Corpses looted"));
		for (String type : corpseTypes(stats.corpsesSeen())) {
			totals.child(keyValue(type, corpseColor(type) + stats.looted().getOrDefault(type, 0)));
		}
		columns.child(scrollColumn(Sizing.fill(27), totals));

		FlowLayout spawn = column();
		spawn.child(header("Spawn time"));
		spawn.child(keyValue("All-time average", spawn(stats.averageSearchMillis())));
		JournalEntry fastest = stats.fastest();
		spawn.child(keyValue("Fastest spawn", fastest == null ? "§8-"
				: spawn(fastest.searchMillis()) + " §8" + fastest.shaftId() + " · " + day(fastest.enteredAt())));
		spawn.child(section("Avg spawn per session, oldest first"));
		spawn.child(chart(stats.oldestFirst()));
		spawn.child(section("Personal bests"));
		spawn.child(keyValue("Most powder", stats.mostPowder() == null ? "§8-"
				: "§b" + number(stats.mostPowder().value()) + " §8" + stats.mostPowder().entry().shaftId()));
		spawn.child(keyValue("Most Lapis", stats.mostLapis() == null ? "§8-"
				: "§9" + stats.mostLapis().value() + " §8" + stats.mostLapis().entry().shaftId()));
		SessionSummary best = stats.bestRate();
		spawn.child(keyValue("Best session", best == null ? "§8-"
				: "§f" + decimal(best.shaftsPerHour()) + " per hour §8" + day(best.start())));
		columns.child(scrollColumn(Sizing.expand(), spawn));

		FlowLayout odds = column();
		odds.child(header("Shaft types seen"));
		int withId = stats.variants().values().stream().mapToInt(Integer::intValue).sum();
		stats.variants().entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.forEach(e -> odds.child(barRow(variantName(e.getKey()), e.getValue() / (double) withId,
						Math.round(100.0 * e.getValue() / withId) + "%", "C".equals(e.getKey()) ? 0xFFFF55FF : 0xFF55FFFF)));
		odds.child(section("Corpses seen, per shaft"));
		double most = stats.corpsesSeen().values().stream().mapToInt(Integer::intValue).max().orElse(1) / (double) stats.shafts();
		for (String type : corpseTypes(stats.corpsesSeen())) {
			double perShaft = stats.corpsesSeen().get(type) / (double) stats.shafts();
			odds.child(barRow(corpseColor(type) + type, perShaft / most,
					String.format(Locale.ROOT, "%.2f", perShaft), corpseArgb(type)));
		}
		columns.child(scrollColumn(Sizing.fill(30), odds));
		content.child(columns);
	}

	private FlowLayout chart(List<SessionSummary> oldestFirst) {
		List<SessionSummary> timed = oldestFirst.stream().filter(s -> s.averageSearchMillis() != null).toList();
		if (timed.size() > 40) timed = timed.subList(timed.size() - 40, timed.size());
		int chartHeight = Math.clamp(height / 7, 28, 48);
		FlowLayout chart = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(chartHeight));
		chart.verticalAlignment(VerticalAlignment.BOTTOM);
		chart.gap(2);
		chart.surface(Surface.outline(0x14FFFFFF));
		chart.padding(Insets.of(2));
		long slowest = timed.stream().mapToLong(SessionSummary::averageSearchMillis).max().orElse(1);
		for (SessionSummary session : timed) {
			BoxComponent bar = UIComponents.box(Sizing.fixed(6), Sizing.fixed(Math.max(1, (int) ((chartHeight - 6) * session.averageSearchMillis() / slowest))));
			bar.fill(true);
			bar.color(GREEN);
			bar.tooltip(List.of(Component.literal("§f" + day(session.start()) + " " + clock(session.start())),
					Component.literal("§7Avg spawn " + spawn(session.averageSearchMillis()) + " §7over " + session.shafts() + " shafts")));
			chart.child(bar);
		}
		if (timed.isEmpty()) chart.child(label("§8No timed spawns yet"));
		return chart;
	}

	private FlowLayout barRow(String name, double fraction, String value, int argb) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.gap(4);
		LabelComponent nameLabel = label("§f" + name);
		nameLabel.horizontalSizing(Sizing.fixed(52));
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
			case "1" -> "Type 1";
			case "2" -> "Type 2";
			case "C" -> "Crystal";
			default -> "_" + variant;
		};
	}

	private static String exit(String exit) {
		return switch (exit) {
			case "BYE" -> "left via the exit";
			case "WARP" -> "warped out";
			case "DEATH" -> "froze";
			default -> "left";
		};
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
