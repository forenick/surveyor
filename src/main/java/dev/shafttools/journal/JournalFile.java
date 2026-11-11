package dev.shafttools.journal;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

/**
 * The journal on disk: one {@link JournalEntry} per line, appended as shafts finish, so a crash
 * loses at most the shaft in progress. All file access runs on one background thread.
 */
public final class JournalFile {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftTools Journal");

	private final Path file;
	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ShaftTools journal");
		thread.setDaemon(true);
		return thread;
	});

	public JournalFile(Path file) {
		this.file = file;
	}

	public Path path() {
		return file;
	}

	/** Reads every entry in file order, on the background thread. */
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

	/** Finishes pending writes. Call when the game closes. */
	public void close() {
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("Journal writes did not finish in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Reads every entry in file order on the calling thread. Unreadable lines are skipped and logged. */
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
					entries.add(gson.fromJson(line, JournalEntry.class));
				} catch (JsonParseException e) {
					LOGGER.warn("Skipping unreadable line {} of {}: {}", number, file, e.getMessage());
				}
			}
		}
		return entries;
	}
}
