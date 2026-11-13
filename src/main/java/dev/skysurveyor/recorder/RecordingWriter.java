package dev.skysurveyor.recorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

final class RecordingWriter implements AutoCloseable {
	private static final Gson GSON = new Gson();

	private final Path file;
	private final BufferedWriter writer;
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Surveyor Recorder");
		thread.setDaemon(true);
		return thread;
	});

	RecordingWriter(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		this.file = file;
		this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
	}

	Path file() {
		return file;
	}

	void write(JsonObject event) {
		String line = GSON.toJson(event);
		executor.execute(() -> {
			try {
				writer.write(line);
				writer.newLine();
				writer.flush();
			} catch (IOException e) {
				ShaftRecorder.LOGGER.error("Failed to write recording line", e);
			}
		});
	}

	@Override
	public void close() {
		executor.execute(() -> {
			try {
				writer.close();
			} catch (IOException e) {
				ShaftRecorder.LOGGER.error("Failed to close recording", e);
			}
		});
		executor.shutdown();
		try {
			executor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
