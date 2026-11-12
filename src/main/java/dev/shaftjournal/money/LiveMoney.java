package dev.shaftjournal.money;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.shaftjournal.detection.ShaftEvent;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.money.MoneySettings.OldShafts;

// prices refresh on shaft entry + journal open, not on a timer, so no traffic when not shafting
public final class LiveMoney {
	// display choice, older = refresh failed or still running, not that days prices
	static final long SNAPSHOT_MAX_AGE = 60 * 60_000L;

	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ShaftJournal prices");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean refreshing = new AtomicBoolean();
	private final AtomicInteger changes = new AtomicInteger();
	private PriceFeed feed;
	private MoneyConfig config;

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
				if (config.settings() != settings || feed.book() != book) changes.incrementAndGet();
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

	// null = no prices at all yet
	public ShaftValue value(JournalEntry entry) {
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

	public void close() {
		io.shutdownNow();
		try {
			io.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
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
