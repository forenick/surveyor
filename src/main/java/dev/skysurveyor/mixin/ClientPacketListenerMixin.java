package dev.skysurveyor.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.skysurveyor.Surveyor;

// read only, never cancels or edits a packet
// after ensureRunningOnSameThread (not HEAD) = once, on client thread, before other mods hide it
@Mixin(value = ClientPacketListener.class, priority = 500)
public abstract class ClientPacketListenerMixin {
	private static final String ENSURE_THREAD =
			"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Inject(method = "handleSystemChat", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void skysurveyor$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		Surveyor.RECORDER.onGameMessage(packet.content(), packet.overlay());
		Surveyor.TRACKER.onGameMessage(packet.content(), packet.overlay());
	}

	@Inject(method = "setTitleText", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void skysurveyor$onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
		Surveyor.RECORDER.onTitle(packet.text(), false);
	}

	@Inject(method = "setSubtitleText", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void skysurveyor$onSubtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
		Surveyor.RECORDER.onTitle(packet.text(), true);
	}

	// every server switch, even same dimension (that keeps the old ClientLevel)
	@Inject(method = "handleLogin", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void skysurveyor$onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
		Surveyor.HUD.onServerChange();
	}

	@Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void skysurveyor$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
		Surveyor.HUD.onServerChange();
	}

	@Inject(method = "handleBlockChangedAck", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void skysurveyor$onBlockAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
		Surveyor.HUD.onBlockAck(packet.sequence());
	}
}
