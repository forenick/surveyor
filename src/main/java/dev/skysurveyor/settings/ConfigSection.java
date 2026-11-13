package dev.skysurveyor.settings;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.google.gson.JsonElement;

// one object in config.json. read: null = default
record ConfigSection<T>(String key, Function<Settings, T> get, BiFunction<Settings, T, Settings> with, Function<JsonElement, T> read) {
	static final ConfigSection<MoneySettings> MONEY = gson("money", MoneySettings.class, Settings::money, Settings::withMoney,
			MoneySettings::repaired);
	static final ConfigSection<JournalSettings> JOURNAL = new ConfigSection<>("journal", Settings::journal, Settings::withJournal,
			JournalSettings::read);
	static final ConfigSection<SummarySettings> SUMMARY = gson("summary", SummarySettings.class, Settings::summary, Settings::withSummary,
			SummarySettings::repaired);
	// a new files key order + the load order, new ones go last
	static final List<ConfigSection<?>> ALL = List.of(MONEY, JOURNAL, SUMMARY);

	private static <T> ConfigSection<T> gson(String key, Class<T> type, Function<Settings, T> get, BiFunction<Settings, T, Settings> with,
			UnaryOperator<T> repaired) {
		return new ConfigSection<>(key, get, with, json -> {
			T loaded = ConfigFile.GSON.fromJson(json, type);
			return loaded == null ? null : repaired.apply(loaded);
		});
	}

	T of(Settings settings) {
		return get.apply(settings);
	}

	Settings in(Settings settings, T value) {
		return with.apply(settings, value);
	}
}
