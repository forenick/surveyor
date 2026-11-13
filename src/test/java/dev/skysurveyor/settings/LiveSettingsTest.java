package dev.skysurveyor.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSettingsTest {
	private static LiveSettings started(Path tmp) {
		LiveSettings live = new LiveSettings();
		live.start(tmp, Runnable::run);
		live.reloadIfChanged();
		return live;
	}

	private static JsonObject file(Path tmp) throws Exception {
		return JsonParser.parseString(Files.readString(tmp.resolve("config.json"))).getAsJsonObject();
	}

	@Test
	void defaultsBeforeStart() {
		LiveSettings live = new LiveSettings();
		assertEquals(Settings.DEFAULTS, live.get());
		assertNull(live.saveError());
		live.reloadIfChanged();
		// nothing to save to yet, memory stays
		Settings asked = live.change(s -> s.withJournal(new JournalSettings(false)));
		assertFalse(asked.journal().byDay());
		assertEquals(Settings.DEFAULTS, live.get());
	}

	@Test
	void changeSetsMemoryAndSavesOnlyThatSection(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"),
				"{\"money\": {\"bazaarTax\": 2, \"later\": 5}, \"journal\": {\"byDay\": false}, \"summary\": {\"seconds\": 900}, \"other\": 1}");
		LiveSettings live = started(tmp);
		JsonObject before = file(tmp);

		Settings next = live.change(s -> s.withSummary(s.summary().withShow(SummarySettings.Show.CHAT)));
		assertEquals(next, live.get());
		assertEquals(SummarySettings.Show.CHAT, live.get().summary().show());
		JsonObject after = file(tmp);
		assertEquals("chat", after.getAsJsonObject("summary").get("show").getAsString());
		assertEquals(900, after.getAsJsonObject("summary").get("seconds").getAsInt(), "hand edit stays");
		after.getAsJsonObject("summary").remove("show");
		assertEquals(before, after, "nothing else written");

		live.change(s -> s.withJournal(s.journal().withByDay(true)).withMoney(s.money().withIronman(true)));
		JsonObject both = file(tmp);
		assertTrue(both.getAsJsonObject("journal").get("byDay").getAsBoolean());
		assertTrue(both.getAsJsonObject("money").get("ironman").getAsBoolean());
		assertEquals(2, both.getAsJsonObject("money").get("bazaarTax").getAsInt());
		assertEquals(5, both.getAsJsonObject("money").get("later").getAsInt());
		assertFalse(both.getAsJsonObject("money").has("gemTier"));
		assertEquals(1, both.get("other").getAsInt());

		LiveSettings restarted = started(tmp);
		assertEquals(live.get(), restarted.get());
	}

	@Test
	void sameSettingsSaveNothing(@TempDir Path tmp) throws Exception {
		LiveSettings live = started(tmp);
		List<String> heard = new ArrayList<>();
		live.onChange((before, after) -> heard.add("change"));
		Files.writeString(tmp.resolve("config.json"), "kept");
		live.change(s -> s.withMoney(s.money().withBazaarTax(s.money().bazaarTax())));
		assertEquals("kept", Files.readString(tmp.resolve("config.json")));
		assertTrue(heard.isEmpty());
	}

	@Test
	void listenersHearPageChangesNotReloads(@TempDir Path tmp) throws Exception {
		LiveSettings live = started(tmp);
		List<Settings> heard = new ArrayList<>();
		live.onChange((before, after) -> {
			heard.add(before);
			heard.add(after);
		});
		Settings next = live.change(s -> s.withMoney(s.money().withIronman(true)));
		assertEquals(List.of(Settings.DEFAULTS, next), heard);

		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		live.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, live.get().money().gemTier());
		assertEquals(2, heard.size());
	}

	@Test
	void failedSaveIsReported(@TempDir Path tmp) throws Exception {
		LiveSettings live = started(tmp);
		Files.writeString(tmp.resolve("config.json"), "{\"money\": {");
		live.change(s -> s.withJournal(new JournalSettings(false)));
		assertFalse(live.get().journal().byDay(), "memory still changes");
		assertEquals("{\"money\": {", Files.readString(tmp.resolve("config.json")));
		assertTrue(live.saveError() != null);
	}
}
