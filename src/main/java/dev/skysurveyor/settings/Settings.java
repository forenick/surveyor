package dev.skysurveyor.settings;

// everything the settings page edits, all of it in config.json. new part = a record, a field here + a ConfigSection
public record Settings(MoneySettings money, JournalSettings journal, SummarySettings summary) {
	public static final Settings DEFAULTS = new Settings(MoneySettings.DEFAULTS, JournalSettings.DEFAULTS, SummarySettings.DEFAULTS);

	public Settings withMoney(MoneySettings value) {
		return new Settings(value, journal, summary);
	}

	public Settings withJournal(JournalSettings value) {
		return new Settings(money, value, summary);
	}

	public Settings withSummary(SummarySettings value) {
		return new Settings(money, journal, value);
	}
}
