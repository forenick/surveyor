package dev.skysurveyor.screen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.util.Resources;

public final class SkyblockItems {
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

	public static synchronized SkyblockItems defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/skyblock_items.txt");
			defaults = new SkyblockItems(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	public Info get(String name) {
		return byName.get(name);
	}
}
