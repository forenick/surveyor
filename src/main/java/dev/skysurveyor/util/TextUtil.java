package dev.skysurveyor.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class TextUtil {
	private static final List<ChatFormatting> LEGACY_COLORS = List.of(
			ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
			ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
			ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE);

	private TextUtil() {
	}

	// non legacy hex colors become "§#RRGGBB"
	public static String toLegacy(Component component) {
		StringBuilder out = new StringBuilder();
		component.visit((style, text) -> {
			if (!text.isEmpty()) {
				out.append(styleCodes(style)).append(text);
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	public static List<String> hoverTexts(Component component) {
		Set<HoverEvent> seen = new LinkedHashSet<>();
		component.visit((style, text) -> {
			if (style.getHoverEvent() instanceof HoverEvent.ShowText) {
				seen.add(style.getHoverEvent());
			}
			return Optional.empty();
		}, Style.EMPTY);
		return seen.stream()
				.map(hover -> toLegacy(((HoverEvent.ShowText) hover).value()))
				.toList();
	}

	private static String styleCodes(Style style) {
		StringBuilder codes = new StringBuilder();
		TextColor color = style.getColor();
		codes.append(color == null ? ChatFormatting.RESET : colorCode(color));
		if (style.isObfuscated()) codes.append(ChatFormatting.OBFUSCATED);
		if (style.isBold()) codes.append(ChatFormatting.BOLD);
		if (style.isStrikethrough()) codes.append(ChatFormatting.STRIKETHROUGH);
		if (style.isUnderlined()) codes.append(ChatFormatting.UNDERLINE);
		if (style.isItalic()) codes.append(ChatFormatting.ITALIC);
		return codes.toString();
	}

	private static String colorCode(TextColor color) {
		for (ChatFormatting formatting : LEGACY_COLORS) {
			if (color.equals(TextColor.fromLegacyFormat(formatting))) {
				return formatting.toString();
			}
		}
		return ChatFormatting.PREFIX_CODE + String.format("#%06X", color.getValue());
	}
}
