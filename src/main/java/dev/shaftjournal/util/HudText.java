package dev.shaftjournal.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

// same order as on screen
public final class HudText {
	private HudText() {
	}

	public static List<String> scoreboardLines(Scoreboard scoreboard) {
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		List<String> lines = new ArrayList<>();
		if (objective != null) {
			lines.add(TextUtil.toLegacy(objective.getDisplayName()));
			scoreboard.listPlayerScores(objective).stream()
					.filter(entry -> !entry.isHidden())
					.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
							.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
					.forEach(entry -> {
						PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
						lines.add(TextUtil.toLegacy(PlayerTeam.formatNameForTeam(team, entry.ownerName())));
					});
		}
		return lines;
	}

	public static List<String> tabListLines(Minecraft client) {
		ClientPacketListener connection = client.getConnection();
		if (connection == null) return List.of();
		var tabOverlay = client.gui.hud.getTabList();
		return connection.getListedOnlinePlayers().stream()
				.sorted(Comparator.comparing((PlayerInfo info) -> info.getTeam() == null ? "" : info.getTeam().getName())
						.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER))
				.map(info -> TextUtil.toLegacy(tabOverlay.getNameForDisplay(info)))
				.toList();
	}
}
