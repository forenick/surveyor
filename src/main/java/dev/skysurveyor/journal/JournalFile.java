package dev.skysurveyor.journal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");

	private final Path file;
	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Surveyor journal");
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
				// cut off write left no newline, dont glue onto it
				String text = endsMidLine() ? "\n" + line : line;
				Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
		// replaces bad utf-8, newBufferedReader throws and one cut off char would hide every line
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
			String line;
			int number = 0;
			while ((line = reader.readLine()) != null) {
				number++;
				if (line.isBlank()) continue;
				try {
					JournalEntry entry = gson.fromJson(line, JournalEntry.class);
					// "null" parses to null
					if (entry == null) LOGGER.warn("Skipping empty line {} of {}", number, file);
					// f.e. {} from a hand edit, line stays in the file
					else if (!complete(entry)) LOGGER.warn("Skipping line {} of {}: fields missing", number, file);
					else entries.add(entry);
				} catch (JsonParseException e) {
					LOGGER.warn("Skipping unreadable line {} of {}: {}", number, file, e.getMessage());
				}
			}
		}
		return entries;
	}

	// in every entry since 0.2.x, null = npe in hud and stats
	private static boolean complete(JournalEntry entry) {
		return entry.exit() != null && entry.corpses() != null && entry.loots() != null && entry.mayhemBuffs() != null
				&& entry.campfiresAt() != null && entry.pristineItems() != null && entry.sacksAdded() != null
				&& entry.sacksRemoved() != null;
	}

	// cant tell = true, a blank line is skipped on read
	private boolean endsMidLine() {
		if (!Files.exists(file)) return false;
		try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
			long size = channel.size();
			if (size == 0) return false;
			ByteBuffer last = ByteBuffer.allocate(1);
			channel.position(size - 1);
			return channel.read(last) != 1 || last.get(0) != '\n';
		} catch (IOException e) {
			return true;
		}
	}
}
