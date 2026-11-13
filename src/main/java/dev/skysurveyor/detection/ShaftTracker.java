package dev.skysurveyor.detection;

import java.util.List;
import java.util.function.Consumer;

import dev.skysurveyor.detection.ShaftEvent.ColdChanged;
import dev.skysurveyor.detection.ShaftEvent.CorpsesChanged;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// candidate per world change, so msgs before its confirmed still count
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

	// false right after a world change
	public boolean inMiningArea() {
		return inMiningArea;
	}

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
		// cold line can vanish for a sec, keep the last value
		Integer newCold = scoreboard.cold(lines);
		if (newCold != null && !newCold.equals(cold)) {
			cold = newCold;
			if (shaft != null) shaft.cold = newCold;
			listener.accept(new ColdChanged(newCold));
		}
	}

	public void onTabList(long now, List<String> lines) {
		if (tabList.inMineshaft(lines)) candidate(now).confirmed = true;
		// empty while loading, says nothing
		if (!lines.isEmpty()) inMiningArea = tabList.inMiningArea(lines);
		// widget missing first few secs, empty = not shown
		List<Corpse> newCorpses = tabList.corpses(lines);
		if (!newCorpses.isEmpty() && !newCorpses.equals(corpses)) {
			corpses = newCorpses;
			if (shaft != null) shaft.corpses = newCorpses;
			listener.accept(new CorpsesChanged(newCorpses));
		}
	}

	private Shaft candidate(long now) {
		if (shaft == null) shaft = new Shaft(now);
		return shaft;
	}
}
