package dev.shaftjournal.settings;

import dev.shaftjournal.money.MoneySettings;

// everything the settings page edits, all of it in config.json
public record Settings(MoneySettings money, boolean byDay) {
	// by day = nicks pick (2026-11-12)
	public static final Settings DEFAULTS = new Settings(MoneySettings.DEFAULTS, true);

	public Settings withMoney(MoneySettings value) {
		return new Settings(value, byDay);
	}

	public Settings withByDay(boolean value) {
		return new Settings(money, value);
	}
}
