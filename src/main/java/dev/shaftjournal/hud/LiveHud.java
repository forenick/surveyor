package dev.shaftjournal.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

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

/**
 * The in-game HUD: keeps the layout ({@code .minecraft/shaftjournal/hud.json}), refreshes the
 * values a few times a second and draws the visible boxes. Only draws; it never sends anything.
 */
public final class LiveHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");
	/** Values refresh every this many ticks: often enough for a seconds timer. */
	private static final int REFRESH_TICKS = 4;
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_JSON = HudLayout.defaults().toJson();

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
	/**
	 * Reads and writes {@code hud.json} one at a time, in order: the read always comes before the
	 * first save, and an older save can't land after a newer one.
	 */
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ShaftJournal HUD");
		thread.setDaemon(true);
		return thread;
	});
	/** The file has been read (or found missing or unreadable). Client thread. */
	private boolean loaded;
	/** The file exists but couldn't be read, so the first save keeps a copy of it first. I/O thread only. */
	private boolean backupBeforeSave;
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
		CompletableFuture.supplyAsync(this::read, io).thenAccept(read -> client.execute(() -> loaded(read)));
	}

	private void loaded(HudLayout read) {
		loaded = true;
		if (read == null) return;
		// Changed in the editor before the file came in: those changes win (and were saved after this read).
		if (!unchanged()) {
			LOGGER.warn("Loaded {} after the HUD was changed in the editor; keeping the changes", file);
			return;
		}
		// The editor notices the new layout and drops its selection.
		layout = read;
		prepared = null;
	}

	/** True while the layout is still the defaults: nothing to keep or save. */
	private boolean unchanged() {
		return layout.toJson().equals(DEFAULT_JSON);
	}

	/** I/O thread. */
	private HudLayout read() {
		if (!Files.exists(file)) return null;
		String json;
		try {
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// It may be fine and only locked for a moment, so it stays; the first save copies it first.
			backupBeforeSave = true;
			LOGGER.warn("Could not read {}, using the default HUD", file, e);
			return null;
		}
		try {
			return HudLayout.fromJson(json);
		} catch (JsonParseException e) {
			// Keep the broken file, or the next save would replace a hand edit with the defaults.
			try {
				Path backup = Files.move(file, backupPath());
				LOGGER.warn("Could not read {}, moved it to {} and using the default HUD", file, backup, e);
			} catch (IOException moveFailed) {
				backupBeforeSave = true;
				LOGGER.warn("Could not read {} or move it away, using the default HUD", file, e);
			}
			return null;
		}
	}

	/** A new name for each backup, so a second broken file doesn't replace the first one's backup. */
	private Path backupPath() {
		return file.resolveSibling("hud.json." + LocalDateTime.now().format(BACKUP_TIME) + ".bad");
	}

	/** Writes the layout off the render thread. Call on the client thread after changing it. */
	public void save() {
		prepared = null;
		// The file is still being read and nothing changed: writing the defaults now would replace it.
		if (file == null || (!loaded && unchanged())) return;
		String json = layout.toJson();
		Runnable write = () -> {
			try {
				Files.createDirectories(file.getParent());
				if (backupBeforeSave && Files.exists(file)) {
					Files.copy(file, backupPath());
					backupBeforeSave = false;
				}
				Path temp = file.resolveSibling("hud.json.tmp");
				Files.writeString(temp, json, StandardCharsets.UTF_8);
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				LOGGER.error("Could not save the HUD to {}", file, e);
			}
		};
		try {
			io.execute(write);
		} catch (RejectedExecutionException e) {
			// The game is closing and the I/O thread has stopped (the editor was still open): write it here.
			write.run();
		}
	}

	/** Lets queued saves finish. Call when the game closes. */
	public void close() {
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("HUD save did not finish in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
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
