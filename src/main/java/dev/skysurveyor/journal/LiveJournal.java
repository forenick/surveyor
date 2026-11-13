package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.SummarySettings;
import dev.skysurveyor.util.DataFolder;

// summary only goes to local chat or a toast, nothing sent to the server
public final class LiveJournal {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");

	private final Journal journal;
	private final LiveMoney money;
	private final LiveSettings settings;
	// client thread only
	private final List<JournalEntry> entries = new ArrayList<>();
	private JournalFile file;
	private Consumer<ToastText> toasts = text -> {
	};

	public LiveJournal(BooleanSupplier inMiningArea, LiveMoney money, LiveSettings settings) {
		journal = new Journal(TextPatterns.defaults(), inMiningArea, this::onEntry);
		this.money = money;
		this.settings = settings;
	}

	public void onToast(Consumer<ToastText> listener) {
		toasts = listener;
	}

	public void start(Minecraft client) {
		file = new JournalFile(DataFolder.in(client.gameDirectory.toPath()).resolve("journal.jsonl"));
		file.load().thenAccept(entries -> client.execute(() -> {
			this.entries.addAll(0, entries);
			journal.resume(this.entries);
			LOGGER.info("Loaded {} journal entries from {}", entries.size(), file.path());
		}));
	}

	public void onEvent(ShaftEvent event) {
		boolean wasRunning = journal.searchRunning();
		journal.onEvent(event, System.currentTimeMillis());
		logTimerStart(wasRunning);
	}

	public void onBlockHit() {
		boolean wasRunning = journal.searchRunning();
		journal.onBlockHit(System.currentTimeMillis());
		logTimerStart(wasRunning);
	}

	private void logTimerStart(boolean wasRunning) {
		if (!wasRunning && journal.searchRunning()) LOGGER.info("Spawn timer started");
	}

	// only goes up, change = new entries. client thread only
	public int entryCount() {
		return entries.size();
	}

	public List<JournalEntry> entries() {
		return List.copyOf(entries);
	}

	public List<JournalEntry> entries(int from) {
		return List.copyOf(entries.subList(from, entries.size()));
	}

	// changes when the file loads (loaded entries go in front). client thread only
	public JournalEntry firstEntry() {
		return entries.isEmpty() ? null : entries.getFirst();
	}

	// new object on every change
	public Journal.Session activeSession(long now) {
		return journal.activeSession(now);
	}

	public List<JournalEntry> sessionEntries(Journal.Session session) {
		if (session == null) return List.of();
		int first = entries.size();
		while (first > 0 && entries.get(first - 1).sessionId() == session.id()) first--;
		return List.copyOf(entries.subList(first, entries.size()));
	}

	public Long spawnTimerMillis(long now) {
		return journal.spawnTimerMillis(now);
	}

	public void newSession() {
		journal.newSession(System.currentTimeMillis());
	}

	public void close() {
		if (file != null) file.close();
	}

	private void onEntry(JournalEntry finished) {
		JournalEntry entry = money.withPrices(finished);
		entries.add(entry);
		if (file != null) file.append(entry);
		SummarySettings.Show show = settings.get().summary().show();
		if (show.chat()) {
			var chat = Minecraft.getInstance().gui.hud.getChat();
			for (String line : SummaryText.lines(entry, journal.session(), money.value(entry), money.settings().isIronman())) {
				chat.addClientSystemMessage(Component.literal(line));
			}
		}
		if (show.toast()) toasts.accept(ToastText.of(entry, journal.session(), money.value(entry), money.settings().isIronman()));
	}
}
