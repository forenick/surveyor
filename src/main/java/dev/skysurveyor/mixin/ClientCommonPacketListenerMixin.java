package dev.skysurveyor.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.skysurveyor.Surveyor;

// read only, only times the packet. vanilla still sends its own pong, we send nothing
// after ensureRunningOnSameThread = once, on client thread
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
	private static final String ENSURE_THREAD =
			"Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Inject(method = "handlePing", at = @At(value = "INVOKE", target = ENSURE_THREAD, shift = At.Shift.AFTER))
	private void skysurveyor$onPing(ClientboundPingPacket packet, CallbackInfo ci) {
		Surveyor.HUD.onServerPing(packet.getId());
	}
}
