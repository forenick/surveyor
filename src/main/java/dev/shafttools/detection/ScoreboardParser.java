package dev.shafttools.detection;

import java.util.List;
import java.util.regex.Matcher;

/** Reads the sidebar scoreboard, given as "§"-coded lines (title first, as the recorder writes them). */
public final class ScoreboardParser {
	private final TextPatterns patterns;

	public ScoreboardParser(TextPatterns patterns) {
		this.patterns = patterns;
	}

	/** The shaft ID at the end of the date/server line, or null outside a shaft. */
	public ShaftId shaftId(List<String> lines) {
		for (String line : lines) {
			Matcher m = patterns.match("scoreboard.shaft_id", TextPatterns.clean(line));
			if (m != null) return new ShaftId(m.group("id"), m.group("type"), m.group("variant"));
		}
		return null;
	}

	/** The Cold value, or null when the scoreboard has no Cold line. */
	public Integer cold(List<String> lines) {
		for (String line : lines) {
			Matcher m = patterns.match("scoreboard.cold", TextPatterns.clean(line));
			if (m != null) return Integer.parseInt(m.group("cold"));
		}
		return null;
	}
}
