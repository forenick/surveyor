package dev.skysurveyor.money;

import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftId;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.money.MoneySettings.OldShafts;

// prices refresh on shaft entry + journal open, not on a timer, so no traffic when not shafting
public final class LiveMoney {
	// display choice, older = refresh failed or still running, not that days prices
	static final long SNAPSHOT_MAX_AGE = 60 * 60_000L;
	public static final long BAZAAR_MAX_AGE = PriceFeed.BAZAAR_MAX_AGE;
	public static final long BINS_MAX_AGE = PriceFeed.BINS_MAX_AGE;
	// journal groups sessions by day, nicks pick (2026-11-12)
	public static final boolean BY_DAY_DEFAULT = true;

	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Surveyor prices");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean refreshing = new AtomicBoolean();
	private final AtomicInteger changes = new AtomicInteger();
	private PriceFeed feed;
	private MoneyConfig config;
	// client thread only, saved entries only (live ones would pile up). cleared when changes() moves
	private final Map<JournalEntry, ShaftValue> values = new IdentityHashMap<>();
	private int valuesAt = -1;
	// client thread only, live value until the shaft or prices change
	private Shaft liveShaft;
	private int liveVersion;
	private ShaftId liveId;
	private int liveAt = -1;
	private ShaftValue liveValue;

	public void start(Path folder, String version) {
		feed = new PriceFeed(folder, version);
		config = new MoneyConfig(folder);
		run(() -> {
			config.reloadIfChanged();
			feed.loadCache();
			changes.incrementAndGet();
		});
	}

	public void refresh() {
		if (feed == null || !refreshing.compareAndSet(false, true)) return;
		run(() -> {
			try {
				MoneySettings settings = config.settings();
				PriceBook book = feed.book();
				config.reloadIfChanged();
				long now = System.currentTimeMillis();
				if (feed.stale(now)) feed.fetchStale(now);
				if (!config.settings().equals(settings) || feed.book() != book) changes.incrementAndGet();
			} finally {
				refreshing.set(false);
			}
		});
	}

	public void onEvent(ShaftEvent event) {
		if (event instanceof ShaftEvent.ShaftEntered) refresh();
	}

	// goes up when prices or settings change, open screens redraw on it
	public int changes() {
		return changes.get();
	}

	// journal setting, lives in config.json too
	public boolean byDay() {
		return config == null ? BY_DAY_DEFAULT : config.byDay();
	}

	public void setByDay(boolean value) {
		if (config == null) return;
		config.byDay(value);
		run(() -> config.saveByDay(value));
	}

	// settings page, saved on the io thread
	public void setSettings(MoneySettings value) {
		if (config == null) return;
		MoneySettings before = config.settings();
		if (value.equals(before)) return;
		config.settings(value);
		changes.incrementAndGet();
		run(() -> config.saveMoney(before, value));
	}

	// null = saved fine
	public String saveError() {
		return config == null ? null : config.saveError();
	}

	public boolean refreshing() {
		return refreshing.get();
	}

	public MoneySettings settings() {
		return config == null ? MoneySettings.DEFAULTS : config.settings();
	}

	public PriceBook book() {
		return feed == null ? PriceBook.EMPTY : feed.book();
	}

	public ItemIds ids() {
		return ItemIds.bundled(feed == null ? Map.of() : feed.names());
	}

	public JournalEntry withPrices(JournalEntry entry) {
		PriceBook book = book();
		if (!freshForSnapshot(book, entry.enteredAt() + entry.durationMillis())) return entry.withPrices(Map.of());
		return entry.withPrices(ShaftValue.snapshot(entry, book::get, settings(), ids(), TextPatterns.defaults()));
	}

	// no bazaar = npc/bin only, would undercut gems for good
	static boolean freshForSnapshot(PriceBook book, long end) {
		return book.hasBazaar() && end - book.bazaarAt() <= SNAPSHOT_MAX_AGE;
	}

	// null = no prices at all yet. saved entries only, cached
	public ShaftValue value(JournalEntry entry) {
		int now = changes.get();
		if (now != valuesAt) {
			values.clear();
			valuesAt = now;
		}
		if (values.containsKey(entry)) return values.get(entry);
		ShaftValue value = compute(entry);
		values.put(entry, value);
		return value;
	}

	// shaft still going, todays prices
	public ShaftValue live(Shaft shaft) {
		int now = changes.get();
		if (shaft != liveShaft || shaft.version() != liveVersion || !Objects.equals(shaft.id(), liveId) || now != liveAt) {
			liveValue = compute(JournalEntry.of(shaft, 0, null, TextPatterns.defaults()));
			liveShaft = shaft;
			liveVersion = shaft.version();
			liveId = shaft.id();
			liveAt = now;
		}
		return liveValue;
	}

	// uncached, any thread
	public ShaftValue compute(JournalEntry entry) {
		PriceBook book = book();
		MoneySettings settings = settings();
		boolean saved = usesSavedPrices(entry, settings);
		if (book.isEmpty() && !saved) return null;
		ShaftValue.Prices prices = saved ? ShaftValue.layered(entry.prices(), book::get) : book::get;
		return ShaftValue.of(entry, prices, settings, ids(), TextPatterns.defaults());
	}

	public boolean usesSavedPrices(JournalEntry entry) {
		return usesSavedPrices(entry, settings());
	}

	private static boolean usesSavedPrices(JournalEntry entry, MoneySettings settings) {
		return settings.oldShafts() == OldShafts.THAT_DAY && entry.prices() != null && !entry.prices().isEmpty();
	}

	// shutdown not shutdownNow, queued config saves must still run
	public void close() {
		if (feed != null) feed.close();
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) io.shutdownNow();
		} catch (InterruptedException e) {
			io.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private void run(Runnable task) {
		try {
			io.execute(task);
		} catch (RejectedExecutionException e) {
			// closing
			refreshing.set(false);
		}
	}
}
