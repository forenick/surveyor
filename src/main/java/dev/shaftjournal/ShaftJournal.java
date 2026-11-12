package dev.shaftjournal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.detection.LiveTracker;
import dev.shaftjournal.hud.LiveHud;
import dev.shaftjournal.journal.LiveJournal;
import dev.shaftjournal.money.LiveMoney;
import dev.shaftjournal.recorder.ShaftRecorder;
import dev.shaftjournal.screen.JournalScreen;
import dev.shaftjournal.util.DataFolder;

/**
 This is my first real Java project i'll make public. The code is probably ass in some places.
 Author is @forenick

 Yes, i did use AI to help me out in some places.

 Fun Fact: Lobsters pee out of their faces. From right under their eyes. :o

 **/

public class ShaftJournal implements ClientModInitializer {
	public static final String MOD_ID = "shaftjournal";
	// not /st, thats hypixels /storage
	private static final List<String> COMMAND_NAMES = List.of(MOD_ID, "sj");
	private static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal");

	public static final ShaftRecorder RECORDER = new ShaftRecorder();
	public static final LiveTracker TRACKER = new LiveTracker();
	public static final LiveMoney MONEY = new LiveMoney();
	public static final LiveJournal JOURNAL = new LiveJournal(TRACKER::inMiningArea, MONEY);
	public static final LiveHud HUD = new LiveHud(TRACKER, JOURNAL);

	private final ShaftRecorder recorder = RECORDER;

	// unbound, only opens our own screen
	private static final KeyMapping JOURNAL_KEY = new KeyMapping("key.shaftjournal.journal", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main")));

	// opens next tick once the chat screen is closed
	private boolean openJournal;

	@Override
	public void onInitializeClient() {
		moveOldDataFolder();
		ClientTickEvents.END_CLIENT_TICK.register(recorder::onTick);
		ClientTickEvents.END_CLIENT_TICK.register(TRACKER::onTick);
		KeyMappingHelper.registerKeyMapping(JOURNAL_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(this::openJournalIfAsked);
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> MONEY.start(DataFolder.in(client.gameDirectory.toPath()), modVersion()));
		ClientLifecycleEvents.CLIENT_STARTED.register(JOURNAL::start);
		ClientLifecycleEvents.CLIENT_STARTED.register(HUD::start);
		ClientTickEvents.END_CLIENT_TICK.register(HUD::onTick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HUD::render);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			recorder.stop();
			JOURNAL.close();
			HUD.close();
			MONEY.close();
		});
		TRACKER.addListener(JOURNAL::onEvent);
		TRACKER.addListener(MONEY::onEvent);
		// own hits only, PASS = never changes the hit
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide()) {
				JOURNAL.onBlockHit();
				recorder.onBlockHit(pos);
			}
			return InteractionResult.PASS;
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			for (String name : COMMAND_NAMES) dispatcher.register(command(name));
		});
	}

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
		MONEY.refresh();
		client.gui.setScreen(new JournalScreen(JOURNAL.entries()));
	}

	private static String modVersion() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("dev");
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
