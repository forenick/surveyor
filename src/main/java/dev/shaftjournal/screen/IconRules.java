package dev.shaftjournal.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.shaftjournal.util.Resources;

/**
 * Picks a vanilla stand-in icon for a SkyBlock item name, used when no texture is available, from {@code shaftjournal/item_icons.txt}.
 * Plain Java so it can be tested; {@link ItemIcons} turns the result into an item stack.
 */
public final class IconRules {
	/** A comment: '#' after whitespace, to the end of the line. */
	private static final Pattern COMMENT = Pattern.compile("\\s#.*$");

	private static IconRules defaults;

	private record Rule(Pattern pattern, String item) {
		/** {@code *} matches any text; everything else matches literally. */
		static Rule of(String pattern, String item) {
			return new Rule(Pattern.compile(Pattern.quote(pattern).replace("*", "\\E.*\\Q")), item);
		}

		boolean matches(String name) {
			return pattern.matcher(name).matches();
		}
	}

	private final List<Rule> rules;
	private final String fallback;

	IconRules(List<String> lines) {
		List<Rule> parsed = new ArrayList<>();
		String defaultItem = "minecraft:paper";
		for (String line : lines) {
			line = COMMENT.matcher(line).replaceFirst("").strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			int eq = line.lastIndexOf('=');
			if (eq < 0) continue;
			String pattern = line.substring(0, eq).strip();
			String item = line.substring(eq + 1).strip();
			if (pattern.equals("default")) defaultItem = item;
			else parsed.add(Rule.of(pattern, item));
		}
		rules = List.copyOf(parsed);
		fallback = defaultItem;
	}

	/** The rules bundled with the mod, read once. */
	public static synchronized IconRules defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/shaftjournal/item_icons.txt");
			defaults = new IconRules(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	/** The Minecraft item ID to draw for this item name. */
	public String itemId(String name) {
		for (Rule rule : rules) {
			if (rule.matches(name)) return rule.item();
		}
		return fallback;
	}
}
