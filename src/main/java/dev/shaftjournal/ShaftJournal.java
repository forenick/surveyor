package dev.shaftjournal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import dev.shaftjournal.detection.LiveTracker;
import dev.shaftjournal.hud.LiveHud;
import dev.shaftjournal.journal.LiveJournal;
import dev.shaftjournal.recorder.ShaftRecorder;
import dev.shaftjournal.screen.JournalScreen;
import dev.shaftjournal.util.DataFolder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public class ShaftJournal implements ClientModInitializer {
	public static final String MOD_ID = "shaftjournal";
	/** {@code /shaftjournal} and the short {@code /sj}. Hypixel's {@code /st} is /storage, so not that. */
	private static final List<String> COMMAND_NAMES = List.of(MOD_ID, "sj");
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");

	/** Chat, action bar and titles are fed into these by {@code ClientPacketListenerMixin}. */
	public static final ShaftRecorder RECORDER = new ShaftRecorder();
	public static final LiveTracker TRACKER = new LiveTracker();
	public static final LiveJournal JOURNAL = new LiveJournal(TRACKER::inMiningArea);
	public static final LiveHud HUD = new LiveHud(TRACKER, JOURNAL);

	private final ShaftRecorder recorder = RECORDER;

	/** Opens the journal screen. Unbound by default; it only opens the mod's own screen. */
	private static final KeyMapping JOURNAL_KEY = new KeyMapping("key.shaftjournal.journal", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main")));

	/** Set by the command; opened on the next tick, after the chat screen has closed. */
	private boolean openJournal;

	@Override
	public void onInitializeClient() {
		moveOldDataFolder();
		ClientTickEvents.END_CLIENT_TICK.register(recorder::onTick);
		ClientTickEvents.END_CLIENT_TICK.register(TRACKER::onTick);
		KeyMappingHelper.registerKeyMapping(JOURNAL_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(this::openJournalIfAsked);
		ClientLifecycleEvents.CLIENT_STARTED.register(JOURNAL::start);
		ClientLifecycleEvents.CLIENT_STARTED.register(HUD::start);
		ClientTickEvents.END_CLIENT_TICK.register(HUD::onTick);
		// Drawn under the chat, so chat messages stay readable over a box.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HUD::render);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			recorder.stop();
			JOURNAL.close();
			HUD.close();
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
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			for (String name : COMMAND_NAMES) dispatcher.register(command(name));
		});
	}

	/** The command tree, the same under every name. On its own it opens the journal. */
	private LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
		return ClientCommands.literal(name)
				.executes(this::openJournal)
				.then(ClientCommands.literal("journal").executes(this::openJournal))
				.then(ClientCommands.literal("record")
						.then(ClientCommands.literal("start").executes(this::startRecording))
						.then(ClientCommands.literal("stop").executes(this::stopRecording)))
				.then(ClientCommands.literal("session")
						.then(ClientCommands.literal("new").executes(this::newSession)))
				.then(ClientCommands.literal("mark")
						.then(ClientCommands.argument("note", StringArgumentType.greedyString())
								.executes(this::mark)));
	}

	private int openJournal(CommandContext<FabricClientCommandSource> context) {
		openJournal = true;
		return 1;
	}

	/** Before the rename the mod kept its journal and recordings in {@code .minecraft/shafttools}. */
	private static void moveOldDataFolder() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		try {
			if (DataFolder.migrate(gameDir)) LOGGER.info("Moved the old shafttools folder to {}", DataFolder.in(gameDir));
		} catch (IOException e) {
			LOGGER.error("Could not move the old shafttools folder to {}", DataFolder.in(gameDir), e);
		}
	}

	private void openJournalIfAsked(Minecraft client) {
		while (JOURNAL_KEY.consumeClick()) openJournal = true;
		if (!openJournal) return;
		openJournal = false;
		client.gui.setScreen(new JournalScreen(JOURNAL.entries()));
	}

	private int startRecording(CommandContext<FabricClientCommandSource> context) {
		try {
			Path file = recorder.start(context.getSource().getClient());
			context.getSource().sendFeedback(Component.literal("§a[ShaftJournal] Recording to " + file.getFileName()));
		} catch (IOException e) {
			context.getSource().sendError(Component.literal("[ShaftJournal] Could not start recording: " + e.getMessage()));
		}
		return 1;
	}

	private int stopRecording(CommandContext<FabricClientCommandSource> context) {
		Path file = recorder.stop();
		context.getSource().sendFeedback(Component.literal(file == null
				? "§e[ShaftJournal] Not recording."
				: "§a[ShaftJournal] Saved " + file.toAbsolutePath()));
		return 1;
	}

	private int newSession(CommandContext<FabricClientCommandSource> context) {
		JOURNAL.newSession();
		context.getSource().sendFeedback(Component.literal("§a[ShaftJournal] Started a new session."));
		return 1;
	}

	private int mark(CommandContext<FabricClientCommandSource> context) {
		if (!recorder.isRecording()) {
			context.getSource().sendError(Component.literal("[ShaftJournal] Not recording."));
			return 0;
		}
		recorder.mark(StringArgumentType.getString(context, "note"));
		context.getSource().sendFeedback(Component.literal("§7[ShaftJournal] Marked."));
		return 1;
	}
}
