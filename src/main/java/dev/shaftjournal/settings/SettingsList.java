package dev.shaftjournal.settings;

import java.util.ArrayList;
import java.util.List;

import dev.shaftjournal.money.MoneySettings.Crystals;
import dev.shaftjournal.money.MoneySettings.GemTier;
import dev.shaftjournal.money.MoneySettings.OldShafts;
import dev.shaftjournal.money.MoneySettings.SellPrice;
import dev.shaftjournal.settings.Setting.Choice;
import dev.shaftjournal.settings.Setting.Number;
import dev.shaftjournal.settings.Setting.Option;
import dev.shaftjournal.settings.Setting.Toggle;

// new setting = one entry here, the page builds its card, search + reset from it
public final class SettingsList {
	public record Section(String name, List<Setting> settings) {
	}

	// icon = textures/gui/<icon>.png
	public record Category(String id, String name, String icon, String description, List<Section> sections) {
		public List<Setting> settings() {
			return sections.stream().flatMap(s -> s.settings().stream()).toList();
		}

		public Category matching(String query) {
			List<Section> kept = new ArrayList<>();
			for (Section section : sections) {
				List<Setting> hits = section.settings().stream().filter(s -> s.matches(query)).toList();
				if (!hits.isEmpty()) kept.add(new Section(section.name(), hits));
			}
			return new Category(id, name, icon, description, kept);
		}

		public boolean isDefault(Settings settings) {
			return settings().stream().allMatch(s -> s.isDefault(settings));
		}

		public Settings reset(Settings settings) {
			for (Setting setting : settings()) settings = setting.reset(settings);
			return settings;
		}
	}

	public static final String MONEY = "money";
	public static final String JOURNAL = "journal";

	public static final List<Category> CATEGORIES = List.of(
			new Category(MONEY, "Money", "money_icon", "How a shaft turns into coins, everywhere the journal and HUD show coins.", List.of(
					new Section("Selling", List.of(
							new Choice<>("Sell price", "Price used for everything sold on the Bazaar.", List.of(
									new Option<>(SellPrice.INSTA_SELL, "Insta-sell", "The highest buy order, what you get right away."),
									new Option<>(SellPrice.SELL_OFFER, "Sell offer", "The lowest sell offer, more coins but you wait for a buyer.")),
									s -> s.money().sellPrice(), (s, v) -> s.withMoney(s.money().withSellPrice(v))),
							new Number("Bazaar tax", "Taken off everything sold on the Bazaar. Not off lowest BIN or NPC prices.", "%", 0, 100,
									s -> s.money().bazaarTax(), (s, v) -> s.withMoney(s.money().withBazaarTax(v))))),
					new Section("Gems", List.of(
							new Choice<>("Gem tier", "Rough, Flawed and Fine gems all count as this tier. Crafting up is free.", List.of(
									new Option<>(GemTier.FLAWLESS, "Flawless", "Every 80 Fine count as 1 Flawless."),
									new Option<>(GemTier.FINE, "Fine", "Gems count at the Fine price.")),
									s -> s.money().gemTier(), (s, v) -> s.withMoney(s.money().withGemTier(v))),
							new Choice<>("Gemstone Crystals", "Crystals from crystal shafts. They can't be sold.", List.of(
									new Option<>(Crystals.PERFECT_MINUS_FLAWLESS, "Perfect minus 5 Flawless",
											"A Perfect gem minus 5 Flawless of that gem."),
									new Option<>(Crystals.ZERO, "Zero", "Crystals add no coins.")),
									s -> s.money().crystals(), (s, v) -> s.withMoney(s.money().withCrystals(v))))),
					new Section("Old shafts", List.of(
							new Choice<>("Prices for old shafts",
									"Shafts since 0.14.0 saved their prices when they ended. Older shafts always use today's.", List.of(
											new Option<>(OldShafts.THAT_DAY, "That day's", "The prices saved when each shaft ended."),
											new Option<>(OldShafts.TODAY, "Today's", "Every shaft at the latest prices.")),
									s -> s.money().oldShafts(), (s, v) -> s.withMoney(s.money().withOldShafts(v))))))),
			new Category(JOURNAL, "Journal", "journal_icon", "The journal screen.", List.of(
					new Section("Overview", List.of(
							new Toggle("Group by day", "Sessions sit under a row per day with that day's totals.",
									Settings::byDay, Settings::withByDay))))));

	private SettingsList() {
	}
}
