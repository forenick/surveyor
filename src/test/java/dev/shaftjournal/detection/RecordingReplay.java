package dev.shaftjournal.detection;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// feeds a recorder .jsonl file through a tracker, same way the live hooks do
public final class RecordingReplay {
	// recordings stay out of git (they have player names in them) so tests skip when theyre missing
	public static final Path RECORDINGS = Path.of("..", "Recordings");

	private RecordingReplay() {
	}

	public static List<ShaftEvent> replay(Path file) throws IOException {
		List<ShaftEvent> events = new ArrayList<>();
		replay(file, new ShaftTracker(TextPatterns.defaults(), events::add), t -> {
		}, t -> {
		});
		return events;
	}

	// clock gets each record's time before its fed, onBlockHit gets the time of every block_hit record
	public static void replay(Path file, ShaftTracker tracker, LongConsumer clock, LongConsumer onBlockHit) throws IOException {
		try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				JsonObject event = JsonParser.parseString(line).getAsJsonObject();
				long t = event.get("t").getAsLong();
				clock.accept(t);
				switch (event.get("type").getAsString()) {
					case "world_change" -> tracker.onWorldChange(t);
					case "chat" -> tracker.onChat(t, event.get("legacy").getAsString(), strings(event.getAsJsonArray("hover")));
					case "scoreboard" -> tracker.onScoreboard(t, strings(event.getAsJsonArray("lines")));
					case "tablist" -> tracker.onTabList(t, strings(event.getAsJsonArray("lines")));
					case "block_hit" -> onBlockHit.accept(t);
					default -> {
					}
				}
			}
		}
	}

	private static List<String> strings(JsonArray array) {
		if (array == null) return List.of();
		return array.asList().stream().map(JsonElement::getAsString).toList();
	}
}
