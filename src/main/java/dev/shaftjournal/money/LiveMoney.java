package dev.shaftjournal.money;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.shaftjournal.detection.ShaftEvent;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.money.MoneySettings.OldShafts;

// prices refresh on shaft entry + journal open, not on a timer, so no traffic when not shafting
public final class LiveMoney {
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ShaftJournal prices");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean refreshing = new AtomicBoolean();
	private PriceFeed feed;
	private MoneyConfig config;

	public void start(Path folder, String version) {
		feed = new PriceFeed(folder, version);
		config = new MoneyConfig(folder);
		run(() -> {
			config.reloadIfChanged();
			feed.loadCache();
		});
	}

	public void refresh() {
		if (feed == null || !refreshing.compareAndSet(false, true)) return;
		run(() -> {
			try {
				config.reloadIfChanged();
				long now = System.currentTimeMillis();
				if (feed.stale(now)) feed.fetchStale(now);
			} finally {
				refreshing.set(false);
			}
		});
	}

	public void onEvent(ShaftEvent event) {
		if (event instanceof ShaftEvent.ShaftEntered) refresh();
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
		if (book.isEmpty()) return entry.withPrices(Map.of());
		return entry.withPrices(ShaftValue.snapshot(entry, book::get, settings(), ids(), TextPatterns.defaults()));
	}

	// null = no prices at all yet
	public ShaftValue value(JournalEntry entry) {
		PriceBook book = book();
		MoneySettings settings = settings();
		boolean saved = settings.oldShafts() == OldShafts.THAT_DAY && entry.prices() != null && !entry.prices().isEmpty();
		if (book.isEmpty() && !saved) return null;
		ShaftValue.Prices prices = saved ? ShaftValue.layered(entry.prices(), book::get) : book::get;
		return ShaftValue.of(entry, prices, settings, ids(), TextPatterns.defaults());
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
