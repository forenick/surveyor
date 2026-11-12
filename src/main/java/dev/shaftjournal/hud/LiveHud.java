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
import dev.shaftjournal.hud.HudData.Totals;
import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.journal.Journal;
import dev.shaftjournal.journal.JournalEntry;
import dev.shaftjournal.journal.LiveJournal;
import dev.shaftjournal.screen.ShaftNames;
import dev.shaftjournal.util.DataFolder;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// the in-game HUD. keeps the layout (.minecraft/shaftjournal/hud.json), refreshes the values a few
// times a second and draws the visible boxes. only draws, never sends anything
public final class LiveHud {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");
	// values refresh every this many ticks, often enough for a seconds timer
	private static final int REFRESH_TICKS = 4;
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_JSON = HudLayout.defaults().toJson();

	private final LiveTracker tracker;
	private final LiveJournal journal;
	private HudLayout layout = HudLayout.defaults();
	private HudData data = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
	// the session that totals got added up for. its a new object every time a shaft gets saved
	private Journal.Session totalsSession;
	private SessionTotals totals = SessionTotals.NONE;
	// how many saved shafts allTime and lastShaft were added up from, and the first one of them
	private int totalsEntries;
	private JournalEntry totalsFirst;
	private Totals allTime = Totals.NONE;
	// the last saved shaft, null before the first
	private Totals lastShaft;
	// the visible boxes ready to draw at preparedWidth × preparedHeight, null when stale
	private List<Prepared> prepared;
	private int preparedWidth;
	private int preparedHeight;
	// reads and writes hud.json one at a time, in order. the read always comes before the first save,
	// and an older save cant land after a newer one
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ShaftJournal HUD");
		thread.setDaemon(true);
		return thread;
	});
	// the file got read (or turned out missing or unreadable). client thread
	private boolean loaded;
	// the file exists but couldnt be read, so the first save makes a copy of it first. I/O thread only
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

	// reads the saved layout off the render thread, the defaults show until its loaded
	public void start(Minecraft client) {
		file = DataFolder.in(client.gameDirectory.toPath()).resolve("hud.json");
		CompletableFuture.supplyAsync(this::read, io).thenAccept(read -> client.execute(() -> loaded(read)));
	}

	private void loaded(HudLayout read) {
		loaded = true;
		if (read == null) return;
		// changed in the editor before the file came in. those changes win (and got saved after this read)
		if (!unchanged()) {
			LOGGER.warn("Loaded {} after the HUD was changed in the editor; keeping the changes", file);
			return;
		}
		// the editor sees the new layout and drops its selection
		layout = read;
		prepared = null;
	}

	// true while the layout is still the defaults, so nothing to keep or save
	private boolean unchanged() {
		return layout.toJson().equals(DEFAULT_JSON);
	}

	// I/O thread
	private HudLayout read() {
		if (!Files.exists(file)) return null;
		String json;
		try {
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			// might be fine and just locked for a sec, so it stays. the first save copies it first
			backupBeforeSave = true;
			LOGGER.warn("Could not read {}, using the default HUD", file, e);
			return null;
		}
		try {
			return HudLayout.fromJson(json);
		} catch (JsonParseException e) {
			// keep the broken file, or the next save would overwrite a hand edit with the defaults
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

	// a new name for every backup, so a second broken file doesnt overwrite the first one's backup
	private Path backupPath() {
		return file.resolveSibling("hud.json." + LocalDateTime.now().format(BACKUP_TIME) + ".bad");
	}

	// writes the layout off the render thread. call it on the client thread after changing it
	public void save() {
		prepared = null;
		// file is still being read and nothing changed, writing the defaults now would overwrite it
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
			// game is closing and the I/O thread already stopped (editor was still open), so write it here
			write.run();
		}
	}

	// lets queued saves finish. call when the game closes
	public void close() {
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("HUD save did not finish in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// the layout the editor changes in place. client thread only
	public HudLayout layout() {
		return layout;
	}

	// swaps in another layout, f.e. an undo step. client thread only, call save() after
	public void setLayout(HudLayout layout) {
		this.layout = layout;
		prepared = null;
	}

	public HudData data() {
		return data;
	}

	public boolean visible(Box box) {
		return box.visible(inMines, inShaft);
	}

	public void onTick(Minecraft client) {
		if (++tick % REFRESH_TICKS != 0) return;
		long now = System.currentTimeMillis();
		Shaft shaft = tracker.currentShaft();
		inShaft = shaft != null;
		inMines = tracker.inMiningArea();
		Journal.Session session = journal.activeSession(now);
		TextPatterns patterns = TextPatterns.defaults();
		if (session != totalsSession) {
			totalsSession = session;
			totals = SessionTotals.of(journal.sessionEntries(session), patterns);
		}
		if (journal.entryCount() != totalsEntries) {
			// only the new shafts get added. if the file just loaded in front of them its all of them again
			JournalEntry first = journal.firstEntry();
			boolean again = first != totalsFirst;
			List<JournalEntry> added = journal.entries(again ? 0 : totalsEntries);
			allTime = (again ? Totals.NONE : allTime).plus(Totals.of(added, patterns));
			totalsFirst = first;
			totalsEntries = journal.entryCount();
			lastShaft = added.isEmpty() ? lastShaft : Totals.of(List.of(added.getLast()), patterns);
		}
		// this shaft = the one ur in, else the last one, so u can still read it while searching
		Totals thisShaft = shaft != null ? Totals.of(shaft, patterns) : lastShaft;
		data = HudData.live(totals, allTime, thisShaft, journal.spawnTimerMillis(now), shaft, now, ShaftNames.defaults());
		prepared = null;
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		// F1 hides the HUD. the editor draws the boxes itself
		if (client.gui.hud.isHidden() || client.gui.screen() instanceof EditHudScreen) return;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		// text only changes when the values refresh, so build it then and not every frame
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
