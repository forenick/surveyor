package dev.shaftjournal.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.shaftjournal.hud.HudLayout.Box;
import dev.shaftjournal.hud.HudLayout.Line;
import dev.shaftjournal.hud.HudValue.Range;

/** Turns a box and the current values into §-coded text lines, title first. Plain Java. */
public final class HudBoxText {
	private static final String TITLE_STYLE = "§e§l";
	/** {value_id}, or {value_id:option:option} where the options pick a range and a number format. */
	private static final Pattern TAG = Pattern.compile("\\{([a-z_]+)((?::[a-z_]+)*)}");
	private static final String COLORS = "0123456789abcdef";
	/** Bold, italic, underline and strikethrough; obfuscated (&k) is left out on purpose. */
	private static final String STYLES = "lonm";

	private HudBoxText() {
	}

	/**
	 * @param example true in the editor: missing values show their example instead of "-"
	 * @return the lines to draw, empty when the box has nothing to show
	 */
	public static List<String> lines(Box box, HudData data, boolean example) {
		List<String> lines = new ArrayList<>();
		if (box.showTitle) {
			String title = tags(box.title, data, example).strip();
			if (!title.isEmpty()) lines.add(TITLE_STYLE + title);
		}
		String label = "§" + box.labelColor + (box.labelBold ? "§l" : "");
		for (Line line : box.lines) {
			HudValue hudValue = line.hudValue();
			if (!line.shown || hudValue == null) continue;
			String rule = HudColors.color(line, data, example);
			if (line.advanced && line.text != null) {
				lines.add(free(box, line, rule, data, example));
				continue;
			}
			String value = "§" + (rule != null ? rule : box.valueColor) + (box.valueBold ? "§l" : "");
			lines.add(label + line.prefix + value + text(hudValue, line.range, line.format, data, example));
		}
		return lines;
	}

	/** Replaces every tag in a title with its value, or nothing while it's missing. */
	static String tags(String text, HudData data, boolean example) {
		if (text.indexOf('{') < 0) return text;
		Matcher matcher = TAG.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			Tag tag = Tag.parse(matcher.group(1), matcher.group(2));
			String replacement = matcher.group();
			if (tag != null) {
				Object v = example ? data.getOrExample(tag.value(), tag.range()) : data.get(tag.value(), tag.range());
				replacement = v == null ? "" : tag.text(data, example);
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	/**
	 * A free-text line: &amp; codes become § codes, tags become values. Plain text starts in the box's label
	 * colour, and &amp;r goes back to it. When a colour rule fires, the line's own value takes its colour, and
	 * the text after it goes on in the colour it had. A tag with an unknown value or option stays as typed.
	 *
	 * @param rule the colour code a rule gives the line's value, or null
	 */
	static String free(Box box, Line line, String rule, HudData data, boolean example) {
		String text = line.text;
		StringBuilder out = new StringBuilder("§").append(box.labelColor);
		String color = box.labelColor;
		StringBuilder styles = new StringBuilder();
		Matcher tags = TAG.matcher(text);
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				char code = Character.toLowerCase(text.charAt(i + 1));
				if (COLORS.indexOf(code) >= 0 || code == 'r') {
					// A colour ends the styles before it, as in Minecraft.
					color = code == 'r' ? box.labelColor : String.valueOf(code);
					styles.setLength(0);
					out.append(code == 'r' ? "§r" : "").append('§').append(color);
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
					boolean own = rule != null && tag.value() == line.hudValue() && tag.range() == line.range;
					if (own) out.append('§').append(rule).append(codes(styles));
					out.append(tag.text(data, example));
					if (own) out.append('§').append(color).append(codes(styles));
					i = tags.end();
					continue;
				}
			}
			out.append(c);
			i++;
		}
		return out.toString();
	}

	/** The free text a line starts from when switched to it: the same look as its basic version. */
	public static String basicText(Box box, Line line) {
		HudValue value = line.hudValue();
		return "&" + box.labelColor + (box.labelBold ? "&l" : "") + line.prefix
				+ "&" + box.valueColor + (box.valueBold ? "&l" : "") + Tag.of(value, line.range, line.format);
	}

	private static String codes(CharSequence styles) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < styles.length(); i++) out.append('§').append(styles.charAt(i));
		return out.toString();
	}

	/** The value as a line shows it; a rate gets "/h", but not the dash for a missing one. */
	static String text(HudValue value, Range range, int format, HudData data, boolean example) {
		Object v = example ? data.getOrExample(value, range) : data.get(value, range);
		String text = HudFormat.format(value.kind(), format, v);
		return v != null && range == Range.PER_HOUR ? text + "/h" : text;
	}

	/** A parsed tag. */
	record Tag(HudValue value, Range range, int format) {
		/** Null when the value or an option is unknown, so the tag is shown as typed. */
		static Tag parse(String id, String options) {
			HudValue value = HudValue.byId(id);
			if (value == null) return null;
			Range range = value.defaultRange();
			int format = 0;
			for (String option : options.split(":")) {
				if (option.isEmpty()) continue;
				Range r = range(option);
				int f = HudFormat.names(value.kind()).indexOf(option);
				if (value.hasRange(r)) range = r;
				else if (f >= 0) format = f;
				else return null;
			}
			return new Tag(value, range, format);
		}

		/** The tag for this value, leaving out the options that are the defaults. */
		static String of(HudValue value, Range range, int format) {
			StringBuilder tag = new StringBuilder("{").append(value.id());
			if (range != null && range != value.defaultRange()) tag.append(':').append(range.name().toLowerCase(Locale.ROOT));
			if (format > 0 && format < HudFormat.names(value.kind()).size()) {
				tag.append(':').append(HudFormat.names(value.kind()).get(format));
			}
			return tag.append('}').toString();
		}

		private static Range range(String option) {
			for (Range r : Range.values()) {
				if (r.name().toLowerCase(Locale.ROOT).equals(option)) return r;
			}
			return null;
		}

		String text(HudData data, boolean example) {
			return HudBoxText.text(value, range, format, data, example);
		}
	}
}
