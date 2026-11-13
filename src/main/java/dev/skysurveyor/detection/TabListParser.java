package dev.skysurveyor.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public final class TabListParser {
	private final TextPatterns patterns;

	public TabListParser(TextPatterns patterns) {
		this.patterns = patterns;
	}

	public boolean inMineshaft(List<String> lines) {
		return lines.stream().anyMatch(line -> patterns.matches("tab.mineshaft_area", TextPatterns.clean(line)));
	}

	// glacite tunnels count too
	public boolean inMiningArea(List<String> lines) {
		return lines.stream().anyMatch(line -> patterns.matches("tab.mining_area", TextPatterns.clean(line)));
	}

	public List<Corpse> corpses(List<String> lines) {
		List<Corpse> corpses = new ArrayList<>();
		boolean inWidget = false;
		for (String line : lines) {
			String text = TextPatterns.clean(line);
			if (!inWidget) {
				inWidget = patterns.matches("tab.corpses_header", text);
				continue;
			}
			Matcher m = patterns.match("tab.corpse", text);
			if (m == null) break;
			corpses.add(new Corpse(m.group("type"), m.group("status").equals("LOOTED")));
		}
		return List.copyOf(corpses);
	}
}
