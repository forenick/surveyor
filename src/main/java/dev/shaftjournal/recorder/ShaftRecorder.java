package dev.shaftjournal.recorder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.shaftjournal.mixin.PlayerTabOverlayAccessor;
import dev.shaftjournal.util.DataFolder;
import dev.shaftjournal.util.HudText;
import dev.shaftjournal.util.TextUtil;

// dev tool. read only, never sends anything to the server
public final class ShaftRecorder {
	static final Logger LOGGER = LoggerFactory.getLogger("ShaftJournal Recorder");

	private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final double ENTITY_SCAN_RADIUS = 48;
	// keeps private convos out of recordings
	private static final List<String> PRIVATE_PREFIXES = List.of("From ", "To ", "Guild > ", "Officer > ");

	private RecordingWriter writer;
	private long lastBlockHit = Long.MIN_VALUE;
	private long startMillis;
	private int tick;

	private ClientLevel lastLevel;
	private List<String> lastScoreboard = List.of();
	private List<String> lastTabList = List.of();
	private String lastTabHeaderFooter = "";
	private String lastHeldItem = "";
	private final Map<Integer, String> knownStands = new HashMap<>();

	public boolean isRecording() {
		return writer != null;
	}

	public Path start(Minecraft client) throws IOException {
		stop();
		Path file = DataFolder.in(client.gameDirectory.toPath()).resolve("recordings")
				.resolve(LocalDateTime.now().format(FILE_NAME) + ".jsonl");
		writer = new RecordingWriter(file);
		startMillis = System.currentTimeMillis();
		resetSnapshots();
		JsonObject event = event("start");
		event.addProperty("time", LocalDateTime.now().toString());
		writer.write(event);
		return file;
	}

	public Path stop() {
		if (writer == null) {
			return null;
		}
		writer.write(event("stop"));
		Path file = writer.file();
		writer.close();
		writer = null;
		return file;
	}

	// fires every tick while held, only a new block pos gets written
	public void onBlockHit(BlockPos pos) {
		if (writer == null || pos.asLong() == lastBlockHit) return;
		lastBlockHit = pos.asLong();
		JsonObject event = event("block_hit");
		event.addProperty("x", pos.getX());
		event.addProperty("y", pos.getY());
		event.addProperty("z", pos.getZ());
		writer.write(event);
	}

	public void mark(String note) {
		if (writer == null) return;
		JsonObject event = event("mark");
		event.addProperty("note", note);
		writer.write(event);
	}

	public void onTitle(Component text, boolean subtitle) {
		if (writer == null) return;
		JsonObject event = event(subtitle ? "subtitle" : "title");
		event.addProperty("text", text.getString());
		event.addProperty("legacy", TextUtil.toLegacy(text));
		writer.write(event);
	}

	public void onGameMessage(Component message, boolean overlay) {
		if (writer == null) return;
		String plain = message.getString();
		if (!overlay && PRIVATE_PREFIXES.stream().anyMatch(plain::startsWith)) return;
		JsonObject event = event(overlay ? "actionbar" : "chat");
		event.addProperty("text", plain);
		event.addProperty("legacy", TextUtil.toLegacy(message));
		List<String> hovers = TextUtil.hoverTexts(message);
		if (!hovers.isEmpty()) {
			JsonArray array = new JsonArray();
			hovers.forEach(array::add);
			event.add("hover", array);
		}
		writer.write(event);
	}

	public void onTick(Minecraft client) {
		if (writer == null) return;
		tick++;
		if (client.level != lastLevel) {
			lastLevel = client.level;
			resetSnapshots();
			writer.write(event("world_change"));
		}
		if (client.level == null || client.player == null) return;

		if (tick % 10 == 0) {
			recordScoreboard(client.level.getScoreboard());
			recordTabList(client);
			recordHeldItem(client.player);
		}
		if (tick % 20 == 0) {
			recordArmorStands(client.level, client.player);
		}
		if (tick % 100 == 0) {
			JsonObject event = event("position");
			addPosition(event, client.player.position());
			writer.write(event);
		}
	}

	private void resetSnapshots() {
		lastScoreboard = List.of();
		lastTabList = List.of();
		lastTabHeaderFooter = "";
		lastHeldItem = "";
		knownStands.clear();
	}

	private void recordScoreboard(Scoreboard scoreboard) {
		List<String> lines = HudText.scoreboardLines(scoreboard);
		if (!lines.equals(lastScoreboard)) {
			lastScoreboard = lines;
			writer.write(linesEvent("scoreboard", lines));
		}
	}

	private void recordTabList(Minecraft client) {
		if (client.getConnection() == null) return;
		List<String> lines = HudText.tabListLines(client);
		if (!lines.equals(lastTabList)) {
			lastTabList = lines;
			writer.write(linesEvent("tablist", lines));
		}

		PlayerTabOverlayAccessor accessor = (PlayerTabOverlayAccessor) client.gui.hud.getTabList();
		String header = legacyOrEmpty(accessor.shaftjournal$getHeader());
		String footer = legacyOrEmpty(accessor.shaftjournal$getFooter());
		if (!(header + "\n" + footer).equals(lastTabHeaderFooter)) {
			lastTabHeaderFooter = header + "\n" + footer;
			JsonObject event = event("tab_header_footer");
			event.addProperty("header", header);
			event.addProperty("footer", footer);
			writer.write(event);
		}
	}

	private void recordHeldItem(LocalPlayer player) {
		JsonObject item = describeItem(player.getInventory().getSelectedItem());
		String signature = item == null ? "" : item.toString();
		if (!signature.equals(lastHeldItem)) {
			lastHeldItem = signature;
			JsonObject event = event("held_item");
			event.add("item", item);
			writer.write(event);
		}
	}

	private void recordArmorStands(ClientLevel level, LocalPlayer player) {
		AABB area = player.getBoundingBox().inflate(ENTITY_SCAN_RADIUS);
		Set<Integer> seen = new HashSet<>();
		for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, area)) {
			JsonObject description = describeArmorStand(stand);
			// decoration only, skip to keep files small
			if (!description.has("name") && !description.has("equipment")) continue;
			seen.add(stand.getId());
			String signature = description.toString();
			if (!signature.equals(knownStands.put(stand.getId(), signature))) {
				JsonObject event = event("armor_stand");
				event.add("stand", description);
				writer.write(event);
			}
		}
		knownStands.keySet().removeIf(id -> {
			if (seen.contains(id)) return false;
			JsonObject event = event("armor_stand_gone");
			event.addProperty("id", id);
			writer.write(event);
			return true;
		});
	}

	private static JsonObject describeArmorStand(ArmorStand stand) {
		JsonObject description = new JsonObject();
		description.addProperty("id", stand.getId());
		if (stand.hasCustomName()) {
			description.addProperty("name", TextUtil.toLegacy(Objects.requireNonNull(stand.getCustomName())));
		}
		addPosition(description, stand.position());
		description.addProperty("invisible", stand.isInvisible());
		description.addProperty("small", stand.isSmall());
		description.addProperty("marker", stand.isMarker());
		JsonObject equipment = new JsonObject();
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			JsonObject item = describeItem(stand.getItemBySlot(slot));
			if (item != null) equipment.add(slot.getName(), item);
		}
		if (!equipment.isEmpty()) description.add("equipment", equipment);
		return description;
	}

	private static JsonObject describeItem(ItemStack stack) {
		if (stack.isEmpty()) return null;
		JsonObject item = new JsonObject();
		item.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		item.addProperty("count", stack.getCount());
		item.addProperty("name", TextUtil.toLegacy(stack.getHoverName()));
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData != null && !customData.isEmpty()) {
			item.addProperty("custom_data", customData.toString());
		}
		var lore = stack.get(DataComponents.LORE);
		if (lore != null && !lore.lines().isEmpty()) {
			JsonArray lines = new JsonArray();
			lore.lines().forEach(line -> lines.add(TextUtil.toLegacy(line)));
			item.add("lore", lines);
		}
		var profile = stack.get(DataComponents.PROFILE);
		if (profile != null) {
			item.addProperty("profile", profile.toString());
		}
		return item;
	}

	private JsonObject event(String type) {
		JsonObject event = new JsonObject();
		event.addProperty("t", System.currentTimeMillis() - startMillis);
		event.addProperty("type", type);
		return event;
	}

	private JsonObject linesEvent(String type, List<String> lines) {
		JsonObject event = event(type);
		JsonArray array = new JsonArray();
		lines.forEach(array::add);
		event.add("lines", array);
		return event;
	}

	private static void addPosition(JsonObject target, Vec3 position) {
		target.addProperty("x", Math.round(position.x * 10) / 10.0);
		target.addProperty("y", Math.round(position.y * 10) / 10.0);
		target.addProperty("z", Math.round(position.z * 10) / 10.0);
	}

	private static String legacyOrEmpty(Component component) {
		return component == null ? "" : TextUtil.toLegacy(component);
	}
}
