package dev.skysurveyor.settings;

import java.util.ArrayList;
import java.util.List;

import dev.skysurveyor.settings.MoneySettings.Crystals;
import dev.skysurveyor.settings.MoneySettings.GemTier;
import dev.skysurveyor.settings.MoneySettings.OldShafts;
import dev.skysurveyor.settings.MoneySettings.SellPrice;
import dev.skysurveyor.settings.Setting.Choice;
import dev.skysurveyor.settings.Setting.Number;
import dev.skysurveyor.settings.Setting.Option;
import dev.skysurveyor.settings.Setting.Toggle;
import dev.skysurveyor.settings.SummarySettings.Position;
import dev.skysurveyor.settings.SummarySettings.Show;
import dev.skysurveyor.settings.SummarySettings.Style;

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
					new Section("Profile", List.of(
							new Toggle("Ironman", "NPC sell prices only, gems as Fine. Items with no NPC price show without coins, keys and "
									+ "campfires cost nothing. Selling and Gems settings don't apply.",
									s -> s.money().isIronman(), (s, v) -> s.withMoney(s.money().withIronman(v))))),
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
			new Category(JOURNAL, "Journal", "journal_icon", "The journal screen and the summary after each shaft.", List.of(
					new Section("Overview", List.of(
							new Toggle("Group by day", "Sessions sit under a row per day with that day's totals.",
									s -> s.journal().byDay(), (s, v) -> s.withJournal(s.journal().withByDay(v))))),
					new Section("Shaft summary", List.of(
							new Choice<>("Show summary", "What you see when you leave a shaft.", List.of(
									new Option<>(Show.TOAST, "Toast", "A short card that pops up for a few seconds."),
									new Option<>(Show.CHAT, "Chat", "The full summary in chat."),
									new Option<>(Show.BOTH, "Both", "The card and the chat summary."),
									new Option<>(Show.OFF, "Off", "Nothing, the journal still saves the shaft.")),
									s -> s.summary().show(), (s, v) -> s.withSummary(s.summary().withShow(v))),
							new Choice<>("Toast style", "How the card looks.", List.of(
									new Option<>(Style.VANILLA, "Vanilla toast", "Like Minecraft's own toasts."),
									new Option<>(Style.FRAME, "HUD frame", "The tooltip frame the HUD boxes use."),
									new Option<>(Style.DARK, "HUD dark", "A dark see-through box.")),
									s -> s.summary().style(), (s, v) -> s.withSummary(s.summary().withStyle(v))),
							new Choice<>("Toast position", "Where the card shows up.", List.of(
									new Option<>(Position.TOP_RIGHT, "Top right", "Where Minecraft's toasts go, slides in."),
									new Option<>(Position.MOVABLE, "Movable", "Anywhere you like, drag it in Edit HUD.")),
									s -> s.summary().position(), (s, v) -> s.withSummary(s.summary().withPosition(v))),
							new Number("Toast time", "How long the card stays. Minecraft's Notification Time setting stretches it.", "s",
									SummarySettings.MIN_SECONDS, SummarySettings.MAX_SECONDS,
									s -> s.summary().seconds(), (s, v) -> s.withSummary(s.summary().withSeconds(v))))))));

	private SettingsList() {
	}
}
