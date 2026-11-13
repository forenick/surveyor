package dev.skysurveyor.settings;

import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.money.MoneySettings;

// everything the settings page edits, all of it in config.json
public record Settings(MoneySettings money, boolean byDay) {
	public static final Settings DEFAULTS = new Settings(MoneySettings.DEFAULTS, LiveMoney.BY_DAY_DEFAULT);

	public Settings withMoney(MoneySettings value) {
		return new Settings(value, byDay);
	}

	public Settings withByDay(boolean value) {
		return new Settings(money, value);
	}
}
