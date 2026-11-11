package dev.shaftjournal.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonParseException;

import dev.shaftjournal.detection.LiveTracker;
import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.journal.LiveJournal;
import dev.shaftjournal.screen.ShaftNames;
import dev.shaftjournal.util.DataFolder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * The in-game HUD: keeps the layout ({@code .minecraft/shaftjournal/hud.json}), refreshes the
 * values a few times a second and draws the visible boxes. Only draws; it never sends anything.
 */
public final class LiveHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");
	/** Values refresh every this many ticks: often enough for a seconds timer. */
	private static final int REFRESH_TICKS = 4;

	private final LiveTracker tracker;
	private final LiveJournal journal;
	private HudLayout layout = HudLayout.defaults();
	private HudData data = HudData.live(List.of(), null, null, 0, TextPatterns.defaults(), ShaftNames.defaults());
	private boolean inMines;
	private boolean inShaft;
	private Path file;
	private int tick;

	public LiveHud(LiveTracker tracker, LiveJournal journal) {
		this.tracker = tracker;
		this.journal = journal;
	}

	/** Reads the saved layout off the render thread; the defaults show until it's loaded. */
	public void start(Minecraft client) {
		file = DataFolder.in(client.gameDirectory.toPath()).resolve("hud.json");
		CompletableFuture.supplyAsync(this::read, Util.ioPool()).thenAccept(read -> {
			if (read != null) client.execute(() -> layout = read);
		});
	}

	private HudLayout read() {
		if (!Files.exists(file)) return null;
		try {
			return HudLayout.fromJson(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Could not read {}, using the default HUD until it is saved again", file, e);
			return null;
		}
	}

	/** Writes the layout off the render thread. Call on the client thread after changing it. */
	public void save() {
		if (file == null) return;
		String json = layout.toJson();
		Path target = file;
		Util.ioPool().execute(() -> {
			try {
				Files.createDirectories(target.getParent());
				Path temp = target.resolveSibling("hud.json.tmp");
				Files.writeString(temp, json, StandardCharsets.UTF_8);
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				LOGGER.error("Could not save the HUD to {}", target, e);
			}
		});
	}

	/** The layout the editor changes in place. Client thread only. */
	public HudLayout layout() {
		return layout;
	}

	public void resetLayout() {
		layout = HudLayout.defaults();
	}

	public HudData data() {
		return data;
	}

	public boolean visible(Box box) {
		return box.visibility.visible(inMines, inShaft);
	}

	public void onTick(Minecraft client) {
		if (++tick % REFRESH_TICKS != 0) return;
		long now = System.currentTimeMillis();
		Shaft shaft = tracker.currentShaft();
		inShaft = shaft != null;
		inMines = tracker.inMiningArea();
		data = HudData.live(journal.sessionEntries(), journal.spawnTimerMillis(now), shaft, now,
				TextPatterns.defaults(), ShaftNames.defaults());
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		// F1 hides the HUD; the editor draws the boxes itself.
		if (client.gui.hud.isHidden() || client.gui.screen() instanceof EditHudScreen) return;
		for (Box box : layout.boxes) {
			if (!visible(box)) continue;
			List<String> lines = HudBoxText.lines(box, data, false);
			if (lines.isEmpty()) continue;
			HudRenderer.draw(graphics, client.font, box, lines,
					HudRenderer.bounds(client.font, box, lines, graphics.guiWidth(), graphics.guiHeight()));
		}
	}
}
