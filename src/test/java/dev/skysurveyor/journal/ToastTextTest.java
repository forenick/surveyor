package dev.skysurveyor.journal;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToastTextTest {
	private static JournalEntry entry(String exit, List<Corpse> corpses) {
		return EntryBuilder.entry().shaftId("JASP_1").durationMillis(252_000).exit(exit).searchMillis(60_000L).corpses(corpses)
				.build();
	}

	@Test
	void shortCard() {
		JournalEntry entry = entry("BYE", List.of(new Corpse("Lapis", true), new Corpse("Lapis", true), new Corpse("Umber", true)));
		ShaftValue value = new ShaftValue(List.of(new Line(Part.CORPSES, "Goblin Egg", 1, 1_240_000.0)), List.of());
		ToastText toast = ToastText.of(entry, new Journal.Session(0, 5, 5, 450_000), value, false);
		assertEquals("§eJASP_1  §f4m 12s", toast.title());
		assertEquals(List.of(new ToastText.Row("Corpses", "§9Lapis 2/2§7, §fUmber 1/1"), new ToastText.Row("Coins", "§6+1.2M"),
				new ToastText.Row("Session", "§f5 shafts§7, avg spawn §f1m 30s")), toast.rows());
	}

	@Test
	void frozeNoCorpsesNoPrices() {
		ToastText toast = ToastText.of(entry("DEATH", List.of()), new Journal.Session(0, 1, 0, 0), null, false);
		assertEquals("§eJASP_1  §f4m 12s §c(froze)", toast.title());
		assertEquals(List.of(new ToastText.Row("Coins", "§8no prices yet"), new ToastText.Row("Session", "§f1 shaft")), toast.rows());
	}

	@Test
	void lossIsRedAndNothingWorthCoinsIsLeftOut() {
		ShaftValue loss = new ShaftValue(List.of(new Line(Part.KEYS, "Umber Key", 1, -121_000.0)), List.of());
		assertEquals(new ToastText.Row("Coins", "§c-121.0k"), ToastText.of(entry("WARP", List.of()), null, loss, false).rows().get(0));
		assertEquals(List.of(), ToastText.of(entry("BYE", List.of()), null, new ShaftValue(List.of(), List.of()), false).rows());
		ShaftValue unpriced = new ShaftValue(List.of(new Line(Part.CRYSTALS, "Jasper Crystal", 1, null)), List.of());
		assertEquals(new ToastText.Row("Coins", "§8no NPC price"), ToastText.of(entry("BYE", List.of()), null, unpriced, true).rows().get(0));
	}
}
