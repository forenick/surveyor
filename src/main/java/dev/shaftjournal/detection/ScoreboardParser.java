package dev.shaftjournal.detection;

import java.util.List;
import java.util.regex.Matcher;

// reads the sidebar scoreboard as § coded lines (title first, same as the recorder writes them)
public final class ScoreboardParser {
	private final TextPatterns patterns;

	public ScoreboardParser(TextPatterns patterns) {
		this.patterns = patterns;
	}

	// shaft id at the end of the date/server line, null when ur not in a shaft
	public ShaftId shaftId(List<String> lines) {
		for (String line : lines) {
			Matcher m = patterns.match("scoreboard.shaft_id", TextPatterns.clean(line));
			if (m != null) return new ShaftId(m.group("id"), m.group("type"), m.group("variant"));
		}
		return null;
	}

	// null when theres no Cold line
	public Integer cold(List<String> lines) {
		for (String line : lines) {
			Matcher m = patterns.match("scoreboard.cold", TextPatterns.clean(line));
			if (m != null) return Integer.parseInt(m.group("cold"));
		}
		return null;
	}
}
