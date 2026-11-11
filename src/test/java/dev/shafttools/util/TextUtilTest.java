package dev.shafttools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

class TextUtilTest {
	@Test
	void toLegacyWritesColorCodes() {
		Component message = Component.literal("[Sacks] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal("+9,861").withStyle(ChatFormatting.GREEN));
		assertEquals("§6[Sacks] §a+9,861", TextUtil.toLegacy(message));
	}

	@Test
	void hoverTextsAreCollectedOnceInOrder() {
		Component breakdown = Component.literal("Rough Jasper Gemstone +8,200");
		Style hover = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(breakdown));
		Component message = Component.literal("[Sacks] ")
				.append(Component.literal("+9,861").withStyle(hover))
				.append(Component.literal(" items").withStyle(hover))
				.append(Component.literal(" (Last 30s.)"));

		assertEquals(List.of("§rRough Jasper Gemstone +8,200"), TextUtil.hoverTexts(message));
	}

	@Test
	void messageWithoutHoverHasNoHoverTexts() {
		assertEquals(List.of(), TextUtil.hoverTexts(Component.literal("plain")));
	}
}
