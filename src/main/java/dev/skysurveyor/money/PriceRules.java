package dev.skysurveyor.money;

import java.util.Locale;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.Crystals;
import dev.skysurveyor.settings.MoneySettings.GemTier;
import dev.skysurveyor.settings.MoneySettings.SellPrice;

// rules in docs/money-design.md
public record PriceRules(ShaftValue.Prices prices, MoneySettings settings, ItemIds ids, TextPatterns patterns) {
	// portable campfire recipe (research/01)
	private static final int CAMPFIRE_MATCH_STICKS = 16;
	// perfect = 5 flawless + crystal (research/01)
	private static final int PERFECT_FLAWLESS = 5;

	// per item, by chat name
	public Double each(String item) {
		Matcher m = patterns.match("gem.item", item);
		if (m != null) {
			Double perFine = gemPerFine(m.group("gem"));
			return perFine == null ? null : perFine / GemYield.countPerFine(m.group("quality"));
		}
		if ((m = patterns.match("crystal.item", item)) != null) return crystal(m.group("gem"));
		return sell(ids.id(item));
	}

	public Double gemPerFine(String gem) {
		// flawless has no npc price
		if (settings.isIronman() || settings.gemTier() == GemTier.FINE) return sell(gemId("FINE", gem));
		Double flawless = sell(gemId("FLAWLESS", gem));
		return flawless == null ? null : flawless / GemYield.RECIPE;
	}

	public Double crystal(String gem) {
		// no npc price, listed w/o coins
		if (settings.isIronman()) return null;
		if (settings.crystals() == Crystals.ZERO) return 0.0;
		Double perfect = sell(gemId("PERFECT", gem));
		Double flawless = sell(gemId("FLAWLESS", gem));
		if (perfect == null || flawless == null) return null;
		return Math.max(0, perfect - PERFECT_FLAWLESS * flawless);
	}

	// bazaar w/ tax, else lowest bin, else npc (soulbound mob drops, empty order books). ironman = npc only
	public Double sell(String id) {
		if (id == null) return null;
		ItemPrice price = prices.get(id);
		if (price == null) return null;
		if (settings.isIronman()) return price.npcSell();
		Double side = settings.sellPrice() == SellPrice.INSTA_SELL ? price.buyOrder() : price.sellOffer();
		if (side != null) return side * settings.taxFactor();
		if (!price.onBazaar() && price.lowestBin() != null) return price.lowestBin();
		return price.npcSell();
	}

	// insta-buy. ironman crafts keys + campfires, nicks pick: no cost
	public Double buy(String id) {
		if (settings.isIronman()) return 0.0;
		if (id == null) return null;
		ItemPrice price = prices.get(id);
		if (price == null) return null;
		return price.onBazaar() ? price.sellOffer() : price.lowestBin();
	}

	// by key item name
	public Double key(String key) {
		return buy(ids.id(key));
	}

	public Double campfireCost() {
		Double umber = buy("REFINED_UMBER");
		Double sticks = buy("MATCH_STICKS");
		return umber == null || sticks == null ? null : umber + CAMPFIRE_MATCH_STICKS * sticks;
	}

	private static String gemId(String tier, String gem) {
		return tier + "_" + gem.toUpperCase(Locale.ROOT) + "_GEM";
	}
}
