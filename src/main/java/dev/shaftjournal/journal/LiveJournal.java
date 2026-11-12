package dev.shaftjournal.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.detection.ShaftEvent;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.money.LiveMoney;
import dev.shaftjournal.util.DataFolder;

// summary only goes to local chat, nothing sent to the server
public final class LiveJournal {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");

	private final Journal journal;
	private final LiveMoney money;
	// client thread only
	private final List<JournalEntry> entries = new ArrayList<>();
	private JournalFile file;

	public LiveJournal(BooleanSupplier inMiningArea, LiveMoney money) {
		journal = new Journal(TextPatterns.defaults(), inMiningArea, this::onEntry);
		this.money = money;
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
		var chat = Minecraft.getInstance().gui.hud.getChat();
		for (String line : SummaryText.lines(entry, journal.session(), money.value(entry))) {
			chat.addClientSystemMessage(Component.literal(line));
		}
	}
}
