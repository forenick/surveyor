package dev.shaftjournal.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.shaftjournal.util.Resources;

/**
 * SkyBlock IDs and Hypixel resource-pack models by item name, from
 * {@code shaftjournal/skyblock_items.txt}. Plain Java; {@link ItemIcons} turns them into stacks.
 */
public final class SkyblockItems {
	/**
	 * @param id    the SkyBlock ID, e.g. {@code FINE_JADE_GEM}
	 * @param model the model in Hypixel's resource pack, or null when the item has none
	 * @param glint whether the real item glints
	 */
	public record Info(String id, String model, boolean glint) {
	}

	private static SkyblockItems defaults;

	private final Map<String, Info> byName = new HashMap<>();

	SkyblockItems(List<String> lines) {
		for (String line : lines) {
			line = line.strip();
			if (line.isEmpty() || line.startsWith("#")) continue;
			int eq = line.lastIndexOf('=');
			if (eq < 0) continue;
			String[] fields = line.substring(eq + 1).strip().split("\\s+");
			if (fields.length < 2) continue;
			String model = fields[1].equals("-") ? null : fields[1];
			boolean glint = fields.length > 2 && fields[2].equals("glint");
			byName.put(line.substring(0, eq).strip(), new Info(fields[0], model, glint));
		}
	}

	/** The items bundled with the mod, read once. */
	public static synchronized SkyblockItems defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/shaftjournal/skyblock_items.txt");
			defaults = new SkyblockItems(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	/** The item with this display name, or null when it isn't in the file. */
	public Info get(String name) {
		return byName.get(name);
	}
}
