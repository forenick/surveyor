package dev.shaftjournal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.shaftjournal.ShaftJournal;

// read only, times vanillas own block break/use packet for ping. we send nothing
// client thread. sequence = the only int local, the one the packet got
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "startPrediction", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V",
			shift = At.Shift.AFTER))
	private void shaftjournal$onPredict(ClientLevel level, PredictiveAction action, CallbackInfo ci, @Local int sequence) {
		ShaftJournal.HUD.onBlockSent(sequence);
	}
}
