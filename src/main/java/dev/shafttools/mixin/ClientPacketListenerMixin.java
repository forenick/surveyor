package dev.shafttools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shafttools.ShaftTools;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

/**
 * Observes chat, action bar and title packets the moment they reach the client thread, before
 * any other mod can hide or rewrite them. Read-only: never cancels or alters a packet.
 *
 * <p>Injecting right after {@code ensureRunningOnSameThread} (rather than at HEAD) makes sure
 * each packet is seen once, on the client thread.
 */
@Mixin(value = ClientPacketListener.class, priority = 500)
public abstract class ClientPacketListenerMixin {
	private static final String ENSURE_THREAD =
			"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Inject(method = "handleSystemChat", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void shafttools$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		ShaftTools.RECORDER.onGameMessage(packet.content(), packet.overlay());
		ShaftTools.TRACKER.onGameMessage(packet.content(), packet.overlay());
	}

	@Inject(method = "setTitleText", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void shafttools$onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
		ShaftTools.RECORDER.onTitle(packet.text(), false);
	}

	@Inject(method = "setSubtitleText", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void shafttools$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
		ShaftTools.RECORDER.onTitle(packet.text(), true);
	}
}
