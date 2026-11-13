package dev.skysurveyor.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.money.MoneySettings;
import dev.skysurveyor.settings.Setting.Choice;
import dev.skysurveyor.settings.SettingsList.Category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsListTest {
	private static Category money() {
		return SettingsList.CATEGORIES.stream().filter(c -> c.id().equals(SettingsList.MONEY)).findFirst().orElseThrow();
	}

	private static Setting named(String name) {
		return SettingsList.CATEGORIES.stream().flatMap(c -> c.settings().stream()).filter(s -> s.name().equals(name)).findFirst()
				.orElseThrow();
	}

	@Test
	void defaultsAreDefault() {
		for (Category c : SettingsList.CATEGORIES) {
			assertTrue(c.isDefault(Settings.DEFAULTS), c.name());
			for (Setting s : c.settings()) assertTrue(s.isDefault(Settings.DEFAULTS), s.name());
		}
	}

	// new category w/o its png = page crashes on open
	@Test
	void everyCategoryHasAnIcon() {
		for (Category c : SettingsList.CATEGORIES) {
			assertNotNull(SettingsListTest.class.getResource("/assets/skysurveyor/textures/gui/" + c.icon() + ".png"), c.name());
		}
	}

	@Test
	void namesAndIdsAreUnique() {
		Set<String> names = new HashSet<>();
		for (Category c : SettingsList.CATEGORIES) {
			for (Setting s : c.settings()) assertTrue(names.add(s.name()), s.name());
		}
		assertEquals(SettingsList.CATEGORIES.size(), SettingsList.CATEGORIES.stream().map(Category::id).distinct().count());
	}

	// every option of every choice round trips through set + get
	@Test
	void choicesSetWhatTheyShow() {
		for (Category c : SettingsList.CATEGORIES) {
			for (Setting s : c.settings()) {
				if (!(s instanceof Choice<?> choice)) continue;
				roundTrip(choice);
			}
		}
	}

	private static <T> void roundTrip(Choice<T> choice) {
		for (Setting.Option<T> option : choice.options()) {
			Settings set = choice.set().apply(Settings.DEFAULTS, option.value());
			assertEquals(option.value(), choice.get().apply(set), choice.name());
			assertEquals(choice.isDefault(option), choice.isDefault(set), choice.name());
		}
	}

	@Test
	void everyMoneyFieldHasASetting() {
		MoneySettings changed = new MoneySettings(MoneySettings.SellPrice.SELL_OFFER, MoneySettings.GemTier.FINE, 2.0,
				MoneySettings.OldShafts.TODAY, MoneySettings.Crystals.ZERO);
		Settings all = Settings.DEFAULTS.withMoney(changed);
		assertFalse(money().isDefault(all));
		assertEquals(Settings.DEFAULTS, money().reset(all));
	}

	@Test
	void resetCategoryLeavesOthers() {
		Settings changed = Settings.DEFAULTS.withByDay(false).withMoney(MoneySettings.DEFAULTS.withGemTier(MoneySettings.GemTier.FINE));
		Settings reset = money().reset(changed);
		assertEquals(MoneySettings.DEFAULTS, reset.money());
		assertFalse(reset.byDay());
	}

	@Test
	void taxParses() {
		Setting.Number tax = (Setting.Number) named("Bazaar tax");
		assertEquals(1.25, tax.parse(" 1,25 "));
		assertEquals(0.0, tax.parse("0"));
		assertEquals(100.0, tax.parse("100"));
		assertNull(tax.parse("100.5"));
		assertNull(tax.parse("-1"));
		assertNull(tax.parse(""));
		assertNull(tax.parse("abc"));
		assertNull(tax.parse("NaN"));
		assertNull(tax.parse("Infinity"));
		assertNull(tax.parse("5f"));
		assertNull(tax.parse("2d"));
		assertNull(tax.parse("1e1"));
		assertNull(tax.parse("0x1p1"));
		assertNull(tax.parse("1.2.3"));
		assertEquals(2.0, tax.parse("2."));
		assertEquals(0.5, tax.parse(".5"));
		assertEquals("1.125%", tax.defaultText());
		assertEquals("2", Setting.Number.format(2.0));
	}

	@Test
	void searchFindsNamesDescriptionsAndChoices() {
		assertEquals(List.of("Bazaar tax"), names(money().matching("TAX")));
		assertEquals(List.of("Gemstone Crystals"), names(money().matching("perfect")));
		assertEquals(List.of("Gem tier", "Gemstone Crystals"), names(money().matching("gem")));
		assertTrue(money().matching("nothing like this").sections().isEmpty());
		assertEquals(money().settings().size(), money().matching("  ").settings().size());
	}

	private static List<String> names(Category category) {
		return category.settings().stream().map(Setting::name).toList();
	}

	@Test
	void defaultTexts() {
		assertEquals("Insta-sell", named("Sell price").defaultText());
		assertEquals("Flawless", named("Gem tier").defaultText());
		assertEquals("On", named("Group by day").defaultText());
	}
}
