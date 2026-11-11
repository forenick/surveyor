package dev.shafttools.screen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Picks a vanilla stand-in icon for a SkyBlock item name, from {@code shafttools/item_icons.txt}.
 * Plain Java so it can be tested; {@link ItemIcons} turns the result into an item stack.
 */
public final class IconRules {
	/** The key for the merged Rough/Flawed/Fine gem slot. */
	public static final String GEMSTONES = "@gemstones";

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
			line = line.strip();
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

	public static IconRules load() {
		try (InputStream in = IconRules.class.getResourceAsStream("/shafttools/item_icons.txt")) {
			if (in == null) return new IconRules(List.of());
			return new IconRules(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The Minecraft item ID to draw for this item name. */
	public String itemId(String name) {
		for (Rule rule : rules) {
			if (rule.matches(name)) return rule.item();
		}
		return fallback;
	}
}
