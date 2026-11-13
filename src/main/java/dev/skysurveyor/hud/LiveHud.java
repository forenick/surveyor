package dev.skysurveyor.hud;

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

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.detection.LiveTracker;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.LiveJournal;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.screen.ShaftNames;
import dev.skysurveyor.util.DataFolder;
import dev.skysurveyor.util.SafeWrite;

public final class LiveHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	private static final int REFRESH_TICKS = 4;
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_JSON = HudLayout.defaults().toJson();
	// locale's own 12h/24h
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);
	private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(Surveyor.MOD_ID)
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
	private JournalEntry lastEntry;
	private int moneyChanges = -1;
	private List<JournalEntry> sessionEntries = List.of();
	private Double sessionCoins;
	private Double lastShaftCoins;
	// entries [0, coinsUpTo) at prices coinsAt, from coinsFirst on
	private Double allTimeCoins;
	private int coinsUpTo;
	private int coinsAt = -1;
	private JournalEntry coinsFirst;
	private boolean coinsPending;
	private List<Prepared> prepared;
	private int preparedWidth;
	private int preparedHeight;
	// single thread = read before first save, saves stay in order
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Surveyor HUD");
		thread.setDaemon(true);
		return thread;
	});
	private boolean loaded;
	// io thread only. null = no backup, ".bad" unreadable, ".old" older format
	private String backupSuffix;
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
			backupSuffix = ".bad";
			LOGGER.warn("Could not read {}, using the default HUD", file, e);
			return null;
		}
		try {
			HudLayout layout = HudLayout.fromJson(json);
			// older format or repaired hand edit, keep the original
			if (!layout.toJson().equals(json)) backupSuffix = ".old";
			return layout;
		} catch (JsonParseException e) {
			// else the next save overwrites a hand edit
			try {
				Path backup = Files.move(file, backupPath(".bad"));
				LOGGER.warn("Could not read {}, moved it to {} and using the default HUD", file, backup, e);
			} catch (IOException moveFailed) {
				backupSuffix = ".bad";
				LOGGER.warn("Could not read {} or move it away, using the default HUD", file, e);
			}
			return null;
		}
	}

	private Path backupPath(String suffix) {
		return file.resolveSibling("hud.json." + LocalDateTime.now().format(BACKUP_TIME) + suffix);
	}

	// client thread, after changing the layout
	public void save() {
		prepared = null;
		// still loading, saving defaults would overwrite the file
		if (file == null || (!loaded && unchanged())) return;
		String json = layout.toJson();
		Runnable write = () -> {
			try {
				if (backupSuffix != null && Files.exists(file)) {
					Files.copy(file, backupPath(backupSuffix));
					backupSuffix = null;
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
		// read once, the io thread bumps it. new prices or settings = coins again, totals stay
		int changes = money.changes();
		boolean revalue = changes != moneyChanges;
		moneyChanges = changes;
		boolean newSession = session != totalsSession;
		if (newSession) {
			totalsSession = session;
			sessionEntries = journal.sessionEntries(session);
			totals = SessionTotals.of(sessionEntries, patterns);
		}
		if (newSession || revalue) sessionCoins = MoneyStats.coins(sessionEntries, money::value);
		JournalEntry last = lastEntry;
		if (journal.entryCount() != totalsEntries) {
			// only new ones, all again if the file loaded in front of them
			JournalEntry first = journal.firstEntry();
			boolean again = first != totalsFirst;
			List<JournalEntry> added = journal.entries(again ? 0 : totalsEntries);
			allTime = (again ? Totals.NONE : allTime).plus(Totals.of(added, patterns));
			totalsFirst = first;
			totalsEntries = journal.entryCount();
			if (!added.isEmpty()) {
				lastEntry = added.getLast();
				lastShaft = Totals.of(List.of(lastEntry), patterns);
			}
		}
		if (lastEntry != last || revalue) lastShaftCoins = lastEntry == null ? null : MoneyStats.net(money.value(lastEntry));
		allTimeCoins(client, changes);
		Totals thisShaft = shaft != null ? Totals.of(shaft, patterns) : lastShaft;
		Double shaftCoins = shaft != null ? MoneyStats.net(money.live(shaft)) : lastShaftCoins;
		data = HudData.live(totals, allTime, thisShaft, journal.spawnTimerMillis(now), shaft, now, ShaftNames.defaults())
				.money(new HudData.Money(shaftCoins, sessionCoins, allTimeCoins, coinsUpTo))
				.game(game(client, now));
		prepared = null;
	}

	// new shafts added here, all of them again on the io thread (thousands of shafts = a hitch)
	private void allTimeCoins(Minecraft client, int changes) {
		JournalEntry first = journal.firstEntry();
		if (coinsAt == changes && coinsFirst == first) {
			int count = journal.entryCount();
			if (coinsUpTo < count) {
				allTimeCoins = MoneyStats.plus(allTimeCoins, MoneyStats.coins(journal.entries(coinsUpTo), money::value));
				coinsUpTo = count;
			}
			return;
		}
		if (coinsPending) return;
		List<JournalEntry> all = journal.entries();
		try {
			coinsPending = true;
			CompletableFuture.supplyAsync(() -> MoneyStats.coins(all, money::compute), io)
					.whenComplete((coins, error) -> client.execute(() -> {
						coinsPending = false;
						if (error != null) LOGGER.warn("Could not add up all-time coins", error);
						else allTimeCoins = coins;
						// on error too, else it retries every refresh
						coinsUpTo = all.size();
						coinsAt = changes;
						coinsFirst = all.isEmpty() ? null : all.getFirst();
					}));
		} catch (RejectedExecutionException e) {
			// closing
			coinsPending = false;
		}
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
