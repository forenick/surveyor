package dev.shaftjournal.detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.shaftjournal.util.Resources;

// game text patterns from shaftjournal/patterns.txt, the one place where hypixels wording lives.
// format is explained in that file
public final class TextPatterns {
	private static final String RESOURCE = "/shaftjournal/patterns.txt";
	private static final Pattern FORMATTING = Pattern.compile("§(?:#[0-9A-Fa-f]{6}|.)");
	private static final Pattern LINE = Pattern.compile("^(\\S+)\\s+(.+)$");

	private static TextPatterns defaults;

	private final Map<String, List<Pattern>> patterns;

	private TextPatterns(Map<String, List<Pattern>> patterns) {
		this.patterns = patterns;
	}

	// the patterns that ship w/ the mod
	public static synchronized TextPatterns defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines(RESOURCE);
			if (lines == null) throw new IllegalStateException("Missing " + RESOURCE);
			defaults = parse(lines);
		}
		return defaults;
	}

	static TextPatterns parse(List<String> lines) {
		Map<String, List<Pattern>> patterns = new HashMap<>();
		for (String line : lines) {
			String trimmed = line.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			Matcher matcher = LINE.matcher(trimmed);
			if (!matcher.matches()) throw new IllegalArgumentException("Bad pattern line: " + line);
			patterns.computeIfAbsent(matcher.group(1), key -> new ArrayList<>()).add(Pattern.compile(matcher.group(2)));
		}
		return new TextPatterns(patterns);
	}

	// matcher for the first pattern under key that matches, or null if none do.
	// run the text through clean() first
	public Matcher match(String key, String text) {
		List<Pattern> candidates = patterns.get(key);
		if (candidates == null) throw new IllegalArgumentException("No pattern named " + key);
		for (Pattern pattern : candidates) {
			Matcher matcher = pattern.matcher(text);
			if (matcher.find()) return matcher;
		}
		return null;
	}

	public boolean matches(String key, String text) {
		return match(key, text) != null;
	}

	// strips formatting codes, also the junk codes hypixel hides inside scoreboard words
	public static String strip(String legacy) {
		return FORMATTING.matcher(legacy).replaceAll("");
	}

	// strip() plus trailing whitespace gone. every pattern gets matched against this
	public static String clean(String legacy) {
		return strip(legacy).stripTrailing();
	}

	// game numbers like "5,730"
	public static long number(String text) {
		return Long.parseLong(text.replace(",", ""));
	}
}
