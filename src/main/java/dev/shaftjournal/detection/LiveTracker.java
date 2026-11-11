package dev.shaftjournal.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.detection.ShaftEvent.ShaftEntered;
import dev.shaftjournal.detection.ShaftEvent.ShaftLeft;
import dev.shaftjournal.util.HudText;
import dev.shaftjournal.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

/**
 * Feeds the live game into a {@link ShaftTracker}: the only class in this package that touches
 * Minecraft. It reads the same data at the same rate as the recorder, so replaying a recording
 * behaves like playing. Events go to the log and to the listeners.
 */
public final class LiveTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal Detection");

	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), this::onEvent);
	private final List<Consumer<ShaftEvent>> listeners = new ArrayList<>();
	private ClientLevel lastLevel;
	private int tick;

	/** Also gets every event, right after it is logged. */
	public void addListener(Consumer<ShaftEvent> listener) {
		listeners.add(listener);
	}

	/** See {@link ShaftTracker#inMiningArea()}. */
	public boolean inMiningArea() {
		return tracker.inMiningArea();
	}

	/** The shaft the player is in, or null. Call on the client thread. */
	public Shaft currentShaft() {
		return tracker.current();
	}

	/** Called for every chat and action-bar message, before other mods can hide or change it. */
	public void onGameMessage(Component message, boolean overlay) {
		if (overlay) return;
		tracker.onChat(System.currentTimeMillis(), TextUtil.toLegacy(message), TextUtil.hoverTexts(message));
	}

	public void onTick(Minecraft client) {
		tick++;
		long now = System.currentTimeMillis();
		if (client.level != lastLevel) {
			lastLevel = client.level;
			tracker.onWorldChange(now);
		}
		if (client.level == null || tick % 10 != 0) return;
		tracker.onScoreboard(now, HudText.scoreboardLines(client.level.getScoreboard()));
		tracker.onTabList(now, HudText.tabListLines(client));
	}

	private void onEvent(ShaftEvent event) {
		switch (event) {
			case ShaftEntered entered -> LOGGER.info("Entered shaft {}", entered.shaft().id().id());
			case ShaftLeft left -> LOGGER.info("Left shaft {}", summary(left.shaft()));
			default -> LOGGER.debug("{}", event);
		}
		listeners.forEach(listener -> listener.accept(event));
	}

	private static String summary(Shaft shaft) {
		return (shaft.id() == null ? "?" : shaft.id().id())
				+ " after " + shaft.durationMillis() / 1000 + "s (" + shaft.exitReason() + ", +" + shaft.exitPowder() + " powder)"
				+ ", corpses " + shaft.corpses() + ", loots " + shaft.loots().size()
				+ ", campfires " + shaft.campfiresPlaced().size() + ", cold " + shaft.cold()
				+ ", pristine " + shaft.pristineItems() + ", sacks " + shaft.sacksAdded();
	}
}
