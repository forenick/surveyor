package dev.shafttools.journal;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shafttools.detection.ShaftEvent;
import dev.shafttools.detection.TextPatterns;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Connects the {@link Journal} to the game: the only class in this package that touches
 * Minecraft. Saves each finished shaft and shows its summary in chat. The summary is added to
 * the local chat only; nothing is sent to the server.
 */
public final class LiveJournal {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftTools Journal");

	private final Journal journal = new Journal(TextPatterns.defaults(), this::onEntry);
	private final BooleanSupplier inMiningArea;
	private JournalFile file;

	public LiveJournal(BooleanSupplier inMiningArea) {
		this.inMiningArea = inMiningArea;
	}

	/** Opens the journal file and continues the last session if it is recent enough. */
	public void start(Minecraft client) {
		file = new JournalFile(client.gameDirectory.toPath().resolve("shafttools").resolve("journal.jsonl"));
		file.load().thenAccept(entries -> client.execute(() -> {
			journal.resume(entries);
			LOGGER.info("Loaded {} journal entries from {}", entries.size(), file.path());
		}));
	}

	public void onEvent(ShaftEvent event) {
		journal.onEvent(event, System.currentTimeMillis());
	}

	/** The player started or kept hitting a block. */
	public void onBlockHit() {
		boolean wasRunning = journal.searchRunning();
		journal.onBlockHit(System.currentTimeMillis(), inMiningArea.getAsBoolean());
		if (!wasRunning && journal.searchRunning()) LOGGER.info("Spawn timer started");
	}

	public void newSession() {
		journal.newSession(System.currentTimeMillis());
	}

	public void close() {
		if (file != null) file.close();
	}

	private void onEntry(JournalEntry entry) {
		if (file != null) file.append(entry);
		var chat = Minecraft.getInstance().gui.hud.getChat();
		for (String line : SummaryText.lines(entry, journal.session())) {
			chat.addClientSystemMessage(Component.literal(line));
		}
	}
}
