package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.hud.HudValue.Range;

public final class HudBoxText {
	private static final String TITLE_STYLE = "§e§l";
	private static final Pattern TAG = Pattern.compile("\\{([a-z_]+)((?::[a-z_]+)*)}");
	private static final Pattern ANY_TAG = Pattern.compile("\\{[^{}]*}");
	// &k left out on purpose
	private static final String STYLES = "lonm";

	private HudBoxText() {
	}

	public static List<String> lines(Box box, HudData data, boolean example) {
		List<String> lines = new ArrayList<>();
		if (box.showTitle) {
			String title = tags(box.title, data, example).strip();
			if (!title.isEmpty()) lines.add(TITLE_STYLE + title);
		}
		for (Line line : box.lines) {
			if (!line.shown) continue;
			String text = line(box, line, data, example);
			if (text != null) lines.add(text);
		}
		return lines;
	}

	// also hidden lines, for the editor
	public static String line(Box box, Line line, HudData data, boolean example) {
		HudValue hudValue = line.hudValue();
		if (hudValue == null) return null;
		String rule = HudColors.color(line, data, example);
		if (line.advanced && line.text != null) return free(box, line, rule, data, example);
		String label = "§" + (line.labelColor != null ? line.labelColor : box.labelColor) + (box.labelBold ? "§l" : "");
		String value = "§" + (rule != null ? rule : line.valueColor != null ? line.valueColor : box.valueColor)
				+ (box.valueBold ? "§l" : "");
		return label + line.prefix + value + text(hudValue, line.range, line.format, data, example);
	}

	static String tags(String text, HudData data, boolean example) {
		if (text.indexOf('{') < 0) return text;
		Matcher matcher = TAG.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			Tag tag = Tag.parse(matcher.group(1), matcher.group(2));
			String replacement = matcher.group();
			if (tag != null) {
				Object v = value(tag.value(), tag.range(), data, example);
				replacement = v == null ? "" : formatted(tag.value(), tag.range(), tag.format(), v);
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	// &r = box label colour, &v = box value colour, unknown tags stay as typed
	static String free(Box box, Line line, String rule, HudData data, boolean example) {
		String text = line.text;
		String color = box.labelColor;
		StringBuilder styles = new StringBuilder(box.labelBold ? "l" : "");
		StringBuilder out = new StringBuilder("§").append(color).append(codes(styles));
		Matcher tags = TAG.matcher(text);
		Range own = rule == null ? null : ruleRange(line);
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				// && and &{ = a plain & or {
				if (escaped(text.charAt(i + 1))) {
					out.append(text.charAt(i + 1));
					i += 2;
					continue;
				}
				char code = Character.toLowerCase(text.charAt(i + 1));
				if (HudLayout.COLOR_CODES.indexOf(code) >= 0 || code == 'r' || code == 'v') {
					// colour resets styles, like vanilla
					styles.setLength(0);
					if (code == 'r') {
						color = box.labelColor;
						if (box.labelBold) styles.append('l');
						out.append("§r");
					} else if (code == 'v') {
						color = box.valueColor;
						if (box.valueBold) styles.append('l');
					} else {
						color = String.valueOf(code);
					}
					out.append('§').append(color).append(codes(styles));
					i += 2;
					continue;
				}
				if (STYLES.indexOf(code) >= 0) {
					if (styles.indexOf(String.valueOf(code)) < 0) styles.append(code);
					out.append('§').append(code);
					i += 2;
					continue;
				}
			}
			if (c == '{' && tags.region(i, text.length()).lookingAt()) {
				Tag tag = Tag.parse(tags.group(1), tags.group(2));
				if (tag != null) {
					boolean colored = rule != null && tag.value() == line.hudValue() && tag.range() == own;
					if (colored) out.append('§').append(rule).append(codes(styles));
					out.append(tag.text(data, example));
					if (colored) out.append('§').append(color).append(codes(styles));
					i = tags.end();
					continue;
				}
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	public static List<String> unknownTags(String text) {
		List<String> unknown = new ArrayList<>();
		Matcher any = ANY_TAG.matcher(text);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length() && escaped(text.charAt(i + 1))) {
				i++;
			} else if (c == '{' && any.region(i, text.length()).lookingAt()) {
				Matcher tag = TAG.matcher(any.group());
				if (!tag.matches() || Tag.parse(tag.group(1), tag.group(2)) == null) unknown.add(any.group());
				i = any.end() - 1;
			}
		}
		return unknown;
	}

	private static boolean escaped(char next) {
		return next == '&' || next == '{';
	}

	public static String tag(HudValue value, Range range, int format) {
		return Tag.of(value, range, format);
	}

	// free text: range of the first tag w/ the lines own value
	public static Range ruleRange(Line line) {
		HudValue value = line.hudValue();
		if (!line.advanced || line.text == null || value == null) return line.range;
		Matcher tags = TAG.matcher(line.text);
		while (tags.find()) {
			Tag tag = Tag.parse(tags.group(1), tags.group(2));
			if (tag != null && tag.value() == value) return tag.range();
		}
		return line.range;
	}

	// own colours as codes, box colours stay live (&v). label escaped, its typed as is
	public static String basicText(Box box, Line line) {
		HudValue value = line.hudValue();
		String label = line.labelColor == null ? "" : "&" + line.labelColor + (box.labelBold ? "&l" : "");
		String valueCode = line.valueColor == null ? "&v" : "&" + line.valueColor + (box.valueBold ? "&l" : "");
		String prefix = line.prefix.replace("&", "&&").replace("{", "&{");
		return label + prefix + valueCode + Tag.of(value, line.range, line.format);
	}

	private static String codes(CharSequence styles) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < styles.length(); i++) out.append('§').append(styles.charAt(i));
		return out.toString();
	}

	static String text(HudValue value, Range range, int format, HudData data, boolean example) {
		return formatted(value, range, format, value(value, range, data, example));
	}

	private static Object value(HudValue value, Range range, HudData data, boolean example) {
		return example ? data.getOrExample(value, range) : data.get(value, range);
	}

	private static String formatted(HudValue value, Range range, int format, Object v) {
		String text = HudFormat.format(value.kind(), format, v);
		return v != null && range == Range.PER_HOUR ? text + "/h" : text;
	}

	record Tag(HudValue value, Range range, int format) {
		static Tag parse(String id, String options) {
			HudValue value = HudValue.byId(id);
			if (value == null) return null;
			Range range = value.defaultRange();
			int format = 0;
			for (String option : options.split(":")) {
				if (option.isEmpty()) continue;
				Range r = Range.byTagName(option);
				int f = HudFormat.names(value.kind()).indexOf(option);
				if (value.hasRange(r)) range = r;
				else if (f >= 0) format = f;
				else return null;
			}
			return new Tag(value, range, format);
		}

		static String of(HudValue value, Range range, int format) {
			StringBuilder tag = new StringBuilder("{").append(value.id());
			if (range != null && range != value.defaultRange()) tag.append(':').append(range.tagName());
			if (format > 0 && format < HudFormat.names(value.kind()).size()) {
				tag.append(':').append(HudFormat.names(value.kind()).get(format));
			}
			return tag.append('}').toString();
		}

		String text(HudData data, boolean example) {
			return HudBoxText.text(value, range, format, data, example);
		}
	}
}
