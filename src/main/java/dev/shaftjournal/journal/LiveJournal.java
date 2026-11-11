package dev.shaftjournal.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.detection.ShaftEvent;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.util.DataFolder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Connects the {@link Journal} to the game: the only class in this package that touches
 * Minecraft. Saves each finished shaft and shows its summary in chat. The summary is added to
 * the local chat only; nothing is sent to the server.
 */
public final class LiveJournal {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");

	private final Journal journal;
	/** Every saved entry, in file order. Only touched on the client thread. */
	private final List<JournalEntry> entries = new ArrayList<>();
	private JournalFile file;

	public LiveJournal(BooleanSupplier inMiningArea) {
		journal = new Journal(TextPatterns.defaults(), inMiningArea, this::onEntry);
	}

	/** Opens the journal file and continues the last session if it is recent enough. */
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

	/** The player started or kept hitting a block. */
	public void onBlockHit() {
		boolean wasRunning = journal.searchRunning();
		journal.onBlockHit(System.currentTimeMillis());
		logTimerStart(wasRunning);
	}

	private void logTimerStart(boolean wasRunning) {
		if (!wasRunning && journal.searchRunning()) LOGGER.info("Spawn timer started");
	}

	/** How many entries are saved; it only grows, so a change means new entries. Call on the client thread. */
	public int entryCount() {
		return entries.size();
	}

	/** A copy of every saved entry, in file order. Call on the client thread. */
	public List<JournalEntry> entries() {
		return List.copyOf(entries);
	}

	/** See {@link Journal#activeSession}. A new object whenever the session or its totals change. */
	public Journal.Session activeSession(long now) {
		return journal.activeSession(now);
	}

	/** The finished shafts of this session, in order; empty for null or before the first one. */
	public List<JournalEntry> sessionEntries(Journal.Session session) {
		if (session == null) return List.of();
		int first = entries.size();
		while (first > 0 && entries.get(first - 1).sessionId() == session.id()) first--;
		return List.copyOf(entries.subList(first, entries.size()));
	}

	/** See {@link Journal#spawnTimerMillis}. */
	public Long spawnTimerMillis(long now) {
		return journal.spawnTimerMillis(now);
	}

	public void newSession() {
		journal.newSession(System.currentTimeMillis());
	}

	public void close() {
		if (file != null) file.close();
	}

	private void onEntry(JournalEntry entry) {
		entries.add(entry);
		if (file != null) file.append(entry);
		var chat = Minecraft.getInstance().gui.hud.getChat();
		for (String line : SummaryText.lines(entry, journal.session())) {
			chat.addClientSystemMessage(Component.literal(line));
		}
	}
}
