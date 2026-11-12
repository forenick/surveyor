package dev.shaftjournal.journal;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// append per shaft so a crash only loses the current one
public final class JournalFile {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");

	private final Path file;
	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ShaftJournal journal");
		thread.setDaemon(true);
		return thread;
	});

	public JournalFile(Path file) {
		this.file = file;
	}

	public Path path() {
		return file;
	}

	public CompletableFuture<List<JournalEntry>> load() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return read();
			} catch (IOException e) {
				LOGGER.error("Could not read {}", file, e);
				return List.of();
			}
		}, io);
	}

	public void append(JournalEntry entry) {
		String line = gson.toJson(entry) + "\n";
		io.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				LOGGER.error("Could not write to {}", file, e);
			}
		});
	}

	public void close() {
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("Journal writes did not finish in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	List<JournalEntry> read() throws IOException {
		List<JournalEntry> entries = new ArrayList<>();
		if (!Files.exists(file)) return entries;
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			int number = 0;
			while ((line = reader.readLine()) != null) {
				number++;
				if (line.isBlank()) continue;
				try {
					JournalEntry entry = gson.fromJson(line, JournalEntry.class);
					// "null" parses to null
					if (entry != null) entries.add(entry);
					else LOGGER.warn("Skipping empty line {} of {}", number, file);
				} catch (JsonParseException e) {
					LOGGER.warn("Skipping unreadable line {} of {}: {}", number, file, e.getMessage());
				}
			}
		}
		return entries;
	}
}
