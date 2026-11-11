package dev.shaftjournal.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonParseException;

import dev.shaftjournal.detection.LiveTracker;
import dev.shaftjournal.detection.Shaft;
import dev.shaftjournal.detection.TextPatterns;
import dev.shaftjournal.hud.HudData.SessionTotals;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.journal.Journal;
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
	private HudData data = HudData.live(SessionTotals.NONE, null, null, 0, ShaftNames.defaults());
	/** The session {@link #totals} were added up for; a new object whenever a shaft is saved. */
	private Journal.Session totalsSession;
	private SessionTotals totals = SessionTotals.NONE;
	/** The visible boxes, ready to draw at {@link #preparedWidth} × {@link #preparedHeight}; null when stale. */
	private List<Prepared> prepared;
	private int preparedWidth;
	private int preparedHeight;
	/** Set once the layout was saved, so a late-loading file doesn't replace newer changes. */
	private boolean saved;
	private boolean inMines;
	private boolean inShaft;
	private Path file;
	private int tick;

	private record Prepared(Box box, List<String> lines, HudRenderer.Bounds bounds) {
	}

	public LiveHud(LiveTracker tracker, LiveJournal journal) {
		this.tracker = tracker;
		this.journal = journal;
	}

	/** Reads the saved layout off the render thread; the defaults show until it's loaded. */
	public void start(Minecraft client) {
		file = DataFolder.in(client.gameDirectory.toPath()).resolve("hud.json");
		CompletableFuture.supplyAsync(this::read, Util.ioPool()).thenAccept(read -> {
			if (read == null) return;
			client.execute(() -> {
				// Too late: the editor already works on (or saved) the layout in memory.
				if (saved || client.gui.screen() instanceof EditHudScreen) {
					LOGGER.warn("Loaded {} after the HUD editor was opened; keeping the editor's layout", file);
					return;
				}
				layout = read;
				prepared = null;
			});
		});
	}

	private HudLayout read() {
		if (!Files.exists(file)) return null;
		try {
			return HudLayout.fromJson(Files.readString(file, StandardCharsets.UTF_8));
		} catch (IOException | JsonParseException e) {
			// Keep the broken file, or the next save would replace a hand edit with the defaults.
			Path backup = file.resolveSibling("hud.json.bad");
			try {
				Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.warn("Could not read {}, moved it to {} and using the default HUD", file, backup, e);
			} catch (IOException moveFailed) {
				LOGGER.warn("Could not read {} or move it to {}, using the default HUD", file, backup, e);
			}
			return null;
		}
	}

	/** Writes the layout off the render thread. Call on the client thread after changing it. */
	public void save() {
		saved = true;
		prepared = null;
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
		prepared = null;
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
		Journal.Session session = journal.activeSession(now);
		if (session != totalsSession) {
			totalsSession = session;
			totals = SessionTotals.of(journal.sessionEntries(session), TextPatterns.defaults());
		}
		data = HudData.live(totals, journal.spawnTimerMillis(now), shaft, now, ShaftNames.defaults());
		prepared = null;
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		// F1 hides the HUD; the editor draws the boxes itself.
		if (client.gui.hud.isHidden() || client.gui.screen() instanceof EditHudScreen) return;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		// The text only changes when the values refresh, so it's built then, not every frame.
		if (prepared == null || width != preparedWidth || height != preparedHeight) {
			prepared = prepare(client, width, height);
			preparedWidth = width;
			preparedHeight = height;
		}
		for (Prepared box : prepared) HudRenderer.draw(graphics, client.font, box.box(), box.lines(), box.bounds());
	}

	private List<Prepared> prepare(Minecraft client, int width, int height) {
		List<Prepared> result = new ArrayList<>();
		for (Box box : layout.boxes) {
			if (!visible(box)) continue;
			List<String> lines = HudBoxText.lines(box, data, false);
			if (lines.isEmpty()) continue;
			result.add(new Prepared(box, lines, HudRenderer.bounds(client.font, box, lines, width, height)));
		}
		return result;
	}
}
