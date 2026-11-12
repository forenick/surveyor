package dev.shaftjournal.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.ShaftJournal;
import dev.shaftjournal.detection.LiveTracker;
import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.hud.HudData.SessionTotals;
import dev.shaftjournal.hud.HudData.Totals;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.journal.Journal;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.LiveJournal;
import dev.shaftjournal.money.LiveMoney;
import dev.shaftjournal.money.MoneyStats;
import dev.shaftjournal.screen.ShaftNames;
import dev.shaftjournal.util.DataFolder;
import dev.shaftjournal.util.SafeWrite;

public final class LiveHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");
	private static final int REFRESH_TICKS = 4;
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_JSON = HudLayout.defaults().toJson();
	// locale's own 12h/24h
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
	private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(ShaftJournal.MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);

	private final LiveTracker tracker;
	private final LiveJournal journal;
	private final LiveMoney money;
	private HudLayout layout = HudLayout.defaults();
	private HudData data = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
	// new object per saved shaft, compared by identity
	private Journal.Session totalsSession;
	private SessionTotals totals = SessionTotals.NONE;
	private int totalsEntries;
	private JournalEntry totalsFirst;
	private Totals allTime = Totals.NONE;
	private Totals lastShaft;
	private int moneyChanges = -1;
	private Double sessionCoins;
	private Double allTimeCoins;
	private Double lastShaftCoins;
	private List<Prepared> prepared;
	private int preparedWidth;
	private int preparedHeight;
	// single thread = read before first save, saves stay in order
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ShaftJournal HUD");
		thread.setDaemon(true);
		return thread;
	});
	private boolean loaded;
	// io thread only
	private boolean backupBeforeSave;
	private boolean inMines;
	private boolean inShaft;
	private Path file;
	private int tick;
	private final TickRate tickRate = new TickRate();
	private final PingTimer pingTimer = new PingTimer();

	private record Prepared(Box box, List<String> lines, HudRenderer.Bounds bounds) {
	}

	public LiveHud(LiveTracker tracker, LiveJournal journal, LiveMoney money) {
		this.tracker = tracker;
		this.journal = journal;
		this.money = money;
	}

	public void start(Minecraft client) {
		file = DataFolder.in(client.gameDirectory.toPath()).resolve("hud.json");
		CompletableFuture.supplyAsync(this::read, io).thenAccept(read -> client.execute(() -> loaded(read)));
	}

	private void loaded(HudLayout read) {
		loaded = true;
		if (read == null) return;
		// editor changes made before the load win
		if (!unchanged()) {
			LOGGER.warn("Loaded {} after the HUD was changed in the editor; keeping the changes", file);
			return;
		}
		layout = read;
		prepared = null;
	}

	private boolean unchanged() {
		return layout.toJson().equals(DEFAULT_JSON);
	}

	private HudLayout read() {
		if (!Files.exists(file)) return null;
		String json;
		try {
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// maybe just locked, keep it, first save backs it up
			backupBeforeSave = true;
			LOGGER.warn("Could not read {}, using the default HUD", file, e);
			return null;
		}
		try {
			return HudLayout.fromJson(json);
		} catch (JsonParseException e) {
			// else the next save overwrites a hand edit
			try {
				Path backup = Files.move(file, backupPath());
				LOGGER.warn("Could not read {}, moved it to {} and using the default HUD", file, backup, e);
			} catch (IOException moveFailed) {
				backupBeforeSave = true;
				LOGGER.warn("Could not read {} or move it away, using the default HUD", file, e);
			}
			return null;
		}
	}

	private Path backupPath() {
		return file.resolveSibling("hud.json." + LocalDateTime.now().format(BACKUP_TIME) + ".bad");
	}

	// client thread, after changing the layout
	public void save() {
		prepared = null;
		// still loading, saving defaults would overwrite the file
		if (file == null || (!loaded && unchanged())) return;
		String json = layout.toJson();
		Runnable write = () -> {
			try {
				if (backupBeforeSave && Files.exists(file)) {
					Files.copy(file, backupPath());
					backupBeforeSave = false;
				}
				SafeWrite.write(file, json);
			} catch (IOException e) {
				LOGGER.error("Could not save the HUD to {}", file, e);
			}
		};
		try {
			io.execute(write);
		} catch (RejectedExecutionException e) {
			// game closing w/ editor open, io thread already gone
			write.run();
		}
	}

	public void close() {
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("HUD save did not finish in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public HudLayout layout() {
		return layout;
	}

	// client thread only, call save() after
	public void setLayout(HudLayout layout) {
		this.layout = layout;
		prepared = null;
	}

	public HudData data() {
		return data;
	}

	public boolean visible(Box box) {
		return box.visible(inMines, inShaft);
	}

	// mixins, client thread
	public void onServerPing(int id) {
		tickRate.onPing(id, System.currentTimeMillis());
	}

	public void onServerChange() {
		tickRate.reset(System.currentTimeMillis());
		pingTimer.reset();
	}

	public void onBlockSent(int sequence) {
		pingTimer.onSent(sequence, System.currentTimeMillis());
	}

	public void onBlockAck(int sequence) {
		pingTimer.onAck(sequence, System.currentTimeMillis());
	}

	public void onTick(Minecraft client) {
		if (++tick % REFRESH_TICKS != 0) return;
		long now = System.currentTimeMillis();
		Shaft shaft = tracker.currentShaft();
		inShaft = shaft != null;
		inMines = tracker.inMiningArea();
		Journal.Session session = journal.activeSession(now);
		TextPatterns patterns = TextPatterns.defaults();
		// new prices or settings = every coin value again
		boolean revalue = money.changes() != moneyChanges;
		moneyChanges = money.changes();
		if (session != totalsSession || revalue) {
			totalsSession = session;
			List<JournalEntry> entries = journal.sessionEntries(session);
			totals = SessionTotals.of(entries, patterns);
			sessionCoins = MoneyStats.coins(entries, money::value);
		}
		if (journal.entryCount() != totalsEntries || revalue) {
			// only new ones, all again if the file loaded in front of them
			JournalEntry first = journal.firstEntry();
			boolean again = first != totalsFirst || revalue;
			List<JournalEntry> added = journal.entries(again ? 0 : totalsEntries);
			allTime = (again ? Totals.NONE : allTime).plus(Totals.of(added, patterns));
			allTimeCoins = MoneyStats.plus(again ? null : allTimeCoins, MoneyStats.coins(added, money::value));
			totalsFirst = first;
			totalsEntries = journal.entryCount();
			if (!added.isEmpty()) {
				lastShaft = Totals.of(List.of(added.getLast()), patterns);
				lastShaftCoins = MoneyStats.net(money.value(added.getLast()));
			}
		}
		Totals thisShaft = shaft != null ? Totals.of(shaft, patterns) : lastShaft;
		Double shaftCoins = shaft != null ? MoneyStats.net(money.live(shaft)) : lastShaftCoins;
		data = HudData.live(totals, allTime, thisShaft, journal.spawnTimerMillis(now), shaft, now, ShaftNames.defaults())
				.money(new HudData.Money(shaftCoins, sessionCoins, allTimeCoins, allTime.shafts()))
				.game(game(client, now));
		prepared = null;
	}

	private HudData.Game game(Minecraft client, long now) {
		// java 20+ puts a narrow nbsp before AM/PM, not every font has it
		String clock = LocalTime.now().format(CLOCK).replace('\u202f', ' ').replace('\u00a0', ' ');
		return new HudData.Game(client.getFps(), pingTimer.ping(now), tickRate.tps(now), clock, MOD_VERSION,
				SharedConstants.getCurrentVersion().name());
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		// editor draws the boxes itself
		if (client.gui.hud.isHidden() || client.gui.screen() instanceof EditHudScreen) return;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (prepared == null || width != preparedWidth || height != preparedHeight) {
			prepared = prepare(client, width, height);
			preparedWidth = width;
			preparedHeight = height;
		}
		for (Prepared box : prepared) HudRenderer.draw(graphics, client.font, box.box(), box.lines(), box.bounds());
	}

	private List<Prepared> prepare(Minecraft client, int width, int height) {
		List<Prepared> result = new ArrayList<>();
		for (Box box : layout.boxes) {
			if (!visible(box)) continue;
			List<String> lines = HudBoxText.lines(box, data, false);
			if (lines.isEmpty()) continue;
			result.add(new Prepared(box, lines, HudRenderer.bounds(client.font, box, lines, width, height)));
		}
		return result;
	}
}
