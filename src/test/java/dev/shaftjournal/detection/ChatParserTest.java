package dev.shaftjournal.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.shaftjournal.detection.ShaftEvent.CampfirePlaced;
import dev.shaftjournal.detection.ShaftEvent.CampfireUsed;
import dev.shaftjournal.detection.ShaftEvent.CaveIn;
import dev.shaftjournal.detection.ShaftEvent.CorpseLooted;
import dev.shaftjournal.detection.ShaftEvent.ExitBonus;
import dev.shaftjournal.detection.ShaftEvent.ExtraCorpses;
import dev.shaftjournal.detection.ShaftEvent.FrozeToDeath;
import dev.shaftjournal.detection.ShaftEvent.MayhemBuff;
import dev.shaftjournal.detection.ShaftEvent.PortalSpawned;
import dev.shaftjournal.detection.ShaftEvent.Pristine;
import dev.shaftjournal.detection.ShaftEvent.SacksChanged;
import dev.shaftjournal.detection.ShaftEvent.ScrapFound;
import dev.shaftjournal.detection.ShaftEvent.Supercraft;
import dev.shaftjournal.detection.ShaftEvent.Warping;

// fixtures are real lines from the recordings in Recordings/
class ChatParserTest {
	private static final String BAR = "§a§l" + "▬".repeat(64);

	private final ChatParser parser = new ChatParser(TextPatterns.defaults());

	private ShaftEvent parse(String legacy) {
		return parser.parse(legacy, List.of());
	}

	@Test
	void portalSpawnMessages() {
		assertEquals(new PortalSpawned(true), parse("§5§lWOW! §aYou found a §bGlacite Mineshaft §aportal!"));
		assertEquals(new PortalSpawned(false), parse("§b§lMINESHAFT! §7A Mineshaft portal spawned nearby!"));
	}

	@Test
	void leavingMessages() {
		assertEquals(new ExitBonus(1520),
				parse("§b§lBYE! §aYou got a §b+1,520 §bGlacite Powder §abonus for leaving a Mineshaft before you died!"));
		assertEquals(new Warping(), parse("§7Warping..."));
		assertEquals(new Warping(), parse("§7Warping you to your SkyBlock island..."));
		assertEquals(new FrozeToDeath(), parse("§c ☠ §7You froze to death§7."));
		assertNull(parse("§c ☠ §7Someone§7 was killed by Ghost§7."));
	}

	@Test
	void mayhemBuffs() {
		assertEquals(new MayhemBuff("Mining Fortune"),
				parse("§b§lMAYHEM! §7You received a §6 Mining Fortune §7buff from your §aMineshaft Mayhem §7perk!"));
		assertEquals(new MayhemBuff("Cold Resistance"),
				parse("§b§lMAYHEM! §7You received a §b Cold Resistance §7buff from your §aMineshaft Mayhem §7perk!"));
		assertEquals(new MayhemBuff("Suspicious Scrap chance"),
				parse("§b§lMAYHEM! §7Your §9Suspicious Scrap §7chance was buffed by your §aMineshaft Mayhem §7perk!"));
		assertEquals(new MayhemBuff("Pickaxe Ability cooldown"),
				parse("§b§lMAYHEM! §7Your Pickaxe Ability cooldown was reduced §7from your §aMineshaft Mayhem §7perk!"));
	}

	@Test
	void shaftEvents() {
		assertEquals(new ExtraCorpses(1), parse("§a§lMORE! §7You have spawned §a1 §7additional §bFrozen Corpse§7!"));
		assertEquals(new CaveIn(), parse(
				"§7§oThe mineshaft entrance has caved in... it doesn't look like anyone else will be able to get in here."));
		assertEquals(new ScrapFound(), parse("§6§lEXCAVATOR! §fYou found a §9Suspicious Scrap§f!"));
	}

	@Test
	void campfireMessages() {
		assertEquals(new CampfirePlaced(), parse("§aYou placed a Portable Campfire!"));
		assertEquals(new CampfireUsed(50),
				parse("§aYou've warmed your feet by your campfire. Your §b❄ Cold §awas reduced by §b50§a!"));
		assertNull(parse("§cYou've already used this campfire!"));
	}

	@Test
	void pristineAndSupercraft() {
		assertEquals(new Pristine(new ItemCount("Flawed Jade Gemstone", 31)),
				parse("§d§lPRISTINE! §fYou found §a Flawed Jade Gemstone §8x31§f!"));
		assertEquals(new Supercraft(new ItemCount("Flawed Jasper Gemstone", 1944)),
				parse("§eYou Supercrafted §a Flawed Jasper Gemstone §8x1,944§e!"));
		assertNull(parse("§eMaxed the Supercraft to §b2,432x crafts§e!"));
	}

	@Test
	void sacksHoverLines() {
		String added = "§aAdded items:\n"
				+ "§a  +10 §eEnchanted Glacite§8 (Enchanted Mining Sack)§a\n"
				+ "§a  +1 §eGlaciated Brains§8 (Witch's Sack)§a\n"
				+ "§a  +40 §e Flawed Jade Gemstone§8 (Gemstones Sack)§a\n"
				+ "§a  +100§e other items.§a\n"
				+ "\n"
				+ "§8This message can be disabled in the settings.";
		assertEquals(new SacksChanged(17, List.of(
						new ItemCount("Enchanted Glacite", 10),
						new ItemCount("Glaciated Brains", 1),
						new ItemCount("Flawed Jade Gemstone", 40)), List.of(), 100, 0),
				parser.parse("§6[Sacks] §a+898§e items§e.§8 (Last 17s.)", List.of(added)));
	}

	@Test
	void sacksRemovedItemsAndSackLists() {
		String added = "§aAdded items:\n"
				+ "§a  +1 §eRefined Mineral§8 (Mining Sack, Events Sack)§a\n"
				+ "§a  +2,432 §e Flawed Jade Gemstone§8 (Gemstones Sack)§a\n"
				+ "\n§8This message can be disabled in the settings.";
		String removed = "§cRemoved items:\n"
				+ "§c  -41,760 §e Flawed Jade Gemstone§8 (Gemstones Sack)§c\n"
				+ "§c  -194,560 §e Rough Jade Gemstone§8 (Gemstones Sack)§c\n"
				+ "\n§8This message can be disabled in the settings.";
		assertEquals(new SacksChanged(8,
						List.of(new ItemCount("Refined Mineral", 1), new ItemCount("Flawed Jade Gemstone", 2432)),
						List.of(new ItemCount("Flawed Jade Gemstone", 41760), new ItemCount("Rough Jade Gemstone", 194560)),
						0, 0),
				parser.parse("§6[Sacks] §a+2,954§e items§e, §c-236,320§e items§e.§8 (Last 8s.)", List.of(added, removed)));
	}

	@Test
	void corpseLootWithBonusDrop() {
		List<ShaftEvent> events = parseAll(
				BAR,
				"§r  §9§lLAPIS §b§lCORPSE LOOT! ",
				"§r  §7+1 bonus drop! ",
				"",
				"§r  §a§lREWARDS",
				"§r    §9Red Goblin Egg",
				"§r    §aGreen Goblin Egg",
				"§r    §9Suspicious Scrap",
				"§r    §9Enchanted Umber §8x4",
				"§r    §a Flawed Onyx Gemstone §8x20",
				"§r    §9Yellow Goblin Egg",
				"§r    §bGlacite Powder §8x5,730",
				BAR);
		assertEquals(List.of(new CorpseLooted(new CorpseLoot("Lapis", true, List.of(
				new ItemCount("Red Goblin Egg", 1),
				new ItemCount("Green Goblin Egg", 1),
				new ItemCount("Suspicious Scrap", 1),
				new ItemCount("Enchanted Umber", 4),
				new ItemCount("Flawed Onyx Gemstone", 20),
				new ItemCount("Yellow Goblin Egg", 1)), 5730))), events);
	}

	@Test
	void corpseLootWithoutBonusDrop() {
		List<ShaftEvent> events = parseAll(
				BAR,
				"§r  §9§lLAPIS §b§lCORPSE LOOT! ",
				"§r  §a§lREWARDS",
				"§r    §9Suspicious Scrap",
				"§r    §a Flawed Aquamarine Gemstone §8x40",
				"§r    §bGlacite Powder §8x4,111",
				BAR);
		assertEquals(List.of(new CorpseLooted(new CorpseLoot("Lapis", false, List.of(
				new ItemCount("Suspicious Scrap", 1),
				new ItemCount("Flawed Aquamarine Gemstone", 40)), 4111))), events);
	}

	@Test
	void stashReminderIsNotLoot() {
		assertEquals(List.of(), parseAll(
				"§r ",
				"§r§f             §7You have §37,732,257 §7materials stashed away!",
				"§r§f             §8(This totals 20 types of materials stashed!)",
				"§r§f                §3§l>>> §3§lCLICK HERE§b to pick them up! §3§l<<<",
				"§r  "));
	}

	private List<ShaftEvent> parseAll(String... lines) {
		List<ShaftEvent> events = new ArrayList<>();
		for (String line : lines) {
			ShaftEvent event = parse(line);
			if (event != null) events.add(event);
		}
		return events;
	}
}
