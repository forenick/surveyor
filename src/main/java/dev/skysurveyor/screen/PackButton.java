package dev.skysurveyor.screen;

import java.util.function.Consumer;

import io.wispforest.owo.ui.component.ButtonComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

// vanilla sprites so packs apply, owo has own copy
public class PackButton extends ButtonComponent {
	private static final WidgetSprites SPRITES = new WidgetSprites(Identifier.withDefaultNamespace("widget/button"),
			Identifier.withDefaultNamespace("widget/button_disabled"), Identifier.withDefaultNamespace("widget/button_highlighted"));

	// focus only lit after keyboard input, else it sticks after a click
	public static final ButtonComponent.Renderer RENDERER = (graphics, b, delta) -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
			SPRITES.get(b.active, b.isHovered() || b.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()), b.getX(),
			b.getY(), b.getWidth(), b.getHeight());

	public PackButton(Component text, Consumer<ButtonComponent> onPress) {
		super(text, onPress);
		renderer(RENDERER);
	}

	public static ButtonComponent button(Component text, Consumer<ButtonComponent> onPress) {
		return new PackButton(text, onPress);
	}
}
