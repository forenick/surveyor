package dev.shafttools.detection;

import java.util.List;
import java.util.function.Consumer;

import dev.shafttools.detection.ShaftEvent.ColdChanged;
import dev.shafttools.detection.ShaftEvent.CorpsesChanged;
import dev.shafttools.detection.ShaftEvent.ShaftEntered;
import dev.shafttools.detection.ShaftEvent.ShaftLeft;

/**
 * Follows the player in and out of shafts and reports every recognised event to a listener.
 * Fed with the same data the recorder writes, so a recording can be replayed through it.
 *
 * <p>Every world change starts a candidate {@link Shaft}. It becomes the current shaft as soon
 * as the tab list says "Area: Mineshaft" or the scoreboard shows a shaft ID, so messages that
 * arrive between the world change and those signals still count.
 */
public final class ShaftTracker {
	private final ChatParser chat;
	private final ScoreboardParser scoreboard;
	private final TabListParser tabList;
	private final Consumer<ShaftEvent> listener;

	private Shaft shaft;
	private Integer cold;
	private List<Corpse> corpses = List.of();

	public ShaftTracker(TextPatterns patterns, Consumer<ShaftEvent> listener) {
		this.chat = new ChatParser(patterns);
		this.scoreboard = new ScoreboardParser(patterns);
		this.tabList = new TabListParser(patterns);
		this.listener = listener;
	}

	/** The shaft the player is in, or null. */
	public Shaft current() {
		return shaft != null && shaft.confirmed ? shaft : null;
	}

	public void onWorldChange(long now) {
		if (shaft != null && shaft.confirmed) {
			shaft.leave(now);
			listener.accept(new ShaftLeft(shaft));
		}
		shaft = new Shaft(now);
		cold = null;
		corpses = List.of();
	}

	public void onChat(long now, String legacy, List<String> hovers) {
		ShaftEvent event = chat.parse(legacy, hovers);
		if (event == null) return;
		if (shaft != null) shaft.apply(event, now);
		listener.accept(event);
	}

	public void onScoreboard(long now, List<String> lines) {
		ShaftId id = scoreboard.shaftId(lines);
		if (id != null) {
			Shaft candidate = candidate(now);
			candidate.confirmed = true;
			if (candidate.id == null) {
				candidate.id = id;
				listener.accept(new ShaftEntered(candidate));
			}
		}
		// The Cold line can be missing for a moment; keep the last value instead of forgetting it.
		Integer newCold = scoreboard.cold(lines);
		if (newCold != null && !newCold.equals(cold)) {
			cold = newCold;
			if (shaft != null) shaft.cold = newCold;
			listener.accept(new ColdChanged(newCold));
		}
	}

	public void onTabList(long now, List<String> lines) {
		if (tabList.inMineshaft(lines)) candidate(now).confirmed = true;
		// The widget is missing for the first seconds in a shaft; an empty list means "not shown".
		List<Corpse> newCorpses = tabList.corpses(lines);
		if (!newCorpses.isEmpty() && !newCorpses.equals(corpses)) {
			corpses = newCorpses;
			if (shaft != null) shaft.corpses = newCorpses;
			listener.accept(new CorpsesChanged(newCorpses));
		}
	}

	/** The current world's candidate; created here if tracking started without a world change. */
	private Shaft candidate(long now) {
		if (shaft == null) shaft = new Shaft(now);
		return shaft;
	}
}
