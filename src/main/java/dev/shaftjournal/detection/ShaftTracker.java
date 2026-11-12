package dev.shaftjournal.detection;

import java.util.List;
import java.util.function.Consumer;

import dev.shaftjournal.detection.ShaftEvent.ColdChanged;
import dev.shaftjournal.detection.ShaftEvent.CorpsesChanged;
import dev.shaftjournal.detection.ShaftEvent.ShaftEntered;
import dev.shaftjournal.detection.ShaftEvent.ShaftLeft;

// follows u in and out of shafts and sends every event it knows to a listener.
// gets the same data the recorder writes, so u can replay a recording through it
// every world change starts a candidate Shaft. it becomes the current shaft once the tab list
// says "Area: Mineshaft" or the scoreboard shows a shaft id, so msgs that come in between the
// world change and that still count
public final class ShaftTracker {
	private final ChatParser chat;
	private final ScoreboardParser scoreboard;
	private final TabListParser tabList;
	private final Consumer<ShaftEvent> listener;

	private Shaft shaft;
	private Integer cold;
	private List<Corpse> corpses = List.of();
	private boolean inMiningArea;

	public ShaftTracker(TextPatterns patterns, Consumer<ShaftEvent> listener) {
		this.chat = new ChatParser(patterns);
		this.scoreboard = new ScoreboardParser(patterns);
		this.tabList = new TabListParser(patterns);
		this.listener = listener;
	}

	// true while the tab list says ur in the Dwarven Mines, false right after a world change
	public boolean inMiningArea() {
		return inMiningArea;
	}

	// shaft ur in, or null
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
		inMiningArea = false;
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
		// Cold line can be gone for a sec, so keep the last value instead of forgetting it
		Integer newCold = scoreboard.cold(lines);
		if (newCold != null && !newCold.equals(cold)) {
			cold = newCold;
			if (shaft != null) shaft.cold = newCold;
			listener.accept(new ColdChanged(newCold));
		}
	}

	public void onTabList(long now, List<String> lines) {
		if (tabList.inMineshaft(lines)) candidate(now).confirmed = true;
		// empty tab list (f.e. while loading) tells us nothing about where we are
		if (!lines.isEmpty()) inMiningArea = tabList.inMiningArea(lines);
		// widget isnt there for the first few secs in a shaft, empty list just means "not shown"
		List<Corpse> newCorpses = tabList.corpses(lines);
		if (!newCorpses.isEmpty() && !newCorpses.equals(corpses)) {
			corpses = newCorpses;
			if (shaft != null) shaft.corpses = newCorpses;
			listener.accept(new CorpsesChanged(newCorpses));
		}
	}

	// candidate for the current world. made here if tracking started w/o a world change
	private Shaft candidate(long now) {
		if (shaft == null) shaft = new Shaft(now);
		return shaft;
	}
}
