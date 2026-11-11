package dev.shafttools;

import java.io.IOException;
import java.nio.file.Path;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.shafttools.detection.LiveTracker;
import dev.shafttools.journal.LiveJournal;
import dev.shafttools.recorder.ShaftRecorder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;

public class ShaftTools implements ClientModInitializer {
	public static final String MOD_ID = "shafttools";

	/** Chat, action bar and titles are fed into these by {@code ClientPacketListenerMixin}. */
	public static final ShaftRecorder RECORDER = new ShaftRecorder();
	public static final LiveTracker TRACKER = new LiveTracker();
	public static final LiveJournal JOURNAL = new LiveJournal(TRACKER::inMiningArea);

	private final ShaftRecorder recorder = RECORDER;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(recorder::onTick);
		ClientTickEvents.END_CLIENT_TICK.register(TRACKER::onTick);
		ClientLifecycleEvents.CLIENT_STARTED.register(JOURNAL::start);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			recorder.stop();
			JOURNAL.close();
		});
		TRACKER.addListener(JOURNAL::onEvent);
		// Only watches the player's own block hits; PASS leaves the hit itself untouched.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide()) {
				JOURNAL.onBlockHit();
				recorder.onBlockHit(pos);
			}
			return InteractionResult.PASS;
		});

		// Client-side commands are handled locally and never sent to the server.
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(
				ClientCommands.literal(MOD_ID)
						.then(ClientCommands.literal("record")
								.then(ClientCommands.literal("start").executes(this::startRecording))
								.then(ClientCommands.literal("stop").executes(this::stopRecording)))
						.then(ClientCommands.literal("session")
								.then(ClientCommands.literal("new").executes(this::newSession)))
						.then(ClientCommands.literal("mark")
								.then(ClientCommands.argument("note", StringArgumentType.greedyString())
										.executes(this::mark)))));
	}

	private int startRecording(CommandContext<FabricClientCommandSource> context) {
		try {
			Path file = recorder.start(context.getSource().getClient());
			context.getSource().sendFeedback(Component.literal("§a[ShaftTools] Recording to " + file.getFileName()));
		} catch (IOException e) {
			context.getSource().sendError(Component.literal("[ShaftTools] Could not start recording: " + e.getMessage()));
		}
		return 1;
	}

	private int stopRecording(CommandContext<FabricClientCommandSource> context) {
		Path file = recorder.stop();
		context.getSource().sendFeedback(Component.literal(file == null
				? "§e[ShaftTools] Not recording."
				: "§a[ShaftTools] Saved " + file.toAbsolutePath()));
		return 1;
	}

	private int newSession(CommandContext<FabricClientCommandSource> context) {
		JOURNAL.newSession();
		context.getSource().sendFeedback(Component.literal("§a[ShaftTools] Started a new session."));
		return 1;
	}

	private int mark(CommandContext<FabricClientCommandSource> context) {
		if (!recorder.isRecording()) {
			context.getSource().sendError(Component.literal("[ShaftTools] Not recording."));
			return 0;
		}
		recorder.mark(StringArgumentType.getString(context, "note"));
		context.getSource().sendFeedback(Component.literal("§7[ShaftTools] Marked."));
		return 1;
	}
}
