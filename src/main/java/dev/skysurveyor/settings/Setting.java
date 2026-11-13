package dev.skysurveyor.settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public sealed interface Setting permits Setting.Choice, Setting.Number, Setting.Toggle {
	String name();

	String description();

	boolean isDefault(Settings settings);

	Settings reset(Settings settings);

	String defaultText();

	default List<String> searchText() {
		return List.of(name(), description());
	}

	// case + spaces dont matter, empty matches all
	default boolean matches(String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return true;
		for (String text : searchText()) {
			if (text.toLowerCase(Locale.ROOT).contains(q)) return true;
		}
		return false;
	}

	record Option<T>(T value, String label, String tip) {
	}

	record Choice<T>(String name, String description, List<Option<T>> options, Function<Settings, T> get,
			BiFunction<Settings, T, Settings> set) implements Setting {
		public boolean isDefault(Settings settings) {
			return get.apply(settings).equals(get.apply(Settings.DEFAULTS));
		}

		public Settings reset(Settings settings) {
			return set.apply(settings, get.apply(Settings.DEFAULTS));
		}

		public boolean isDefault(Option<T> option) {
			return option.value().equals(get.apply(Settings.DEFAULTS));
		}

		public String defaultText() {
			T value = get.apply(Settings.DEFAULTS);
			return options.stream().filter(o -> o.value().equals(value)).map(Option::label).findFirst().orElse(String.valueOf(value));
		}

		public List<String> searchText() {
			List<String> text = new ArrayList<>(List.of(name, description));
			options.forEach(o -> text.add(o.label()));
			return text;
		}
	}

	record Number(String name, String description, String unit, double min, double max, ToDoubleFunction<Settings> get,
			BiFunction<Settings, Double, Settings> set) implements Setting {
		public boolean isDefault(Settings settings) {
			return get.applyAsDouble(settings) == get.applyAsDouble(Settings.DEFAULTS);
		}

		public Settings reset(Settings settings) {
			return set.apply(settings, get.applyAsDouble(Settings.DEFAULTS));
		}

		public String defaultText() {
			return format(get.applyAsDouble(Settings.DEFAULTS)) + unit;
		}

		// null = not a number in range. comma ok for eu keyboards
		public Double parse(String text) {
			String t = text.strip().replace(',', '.');
			// plain digits only, parseDouble takes 1d, 1e1, 0x1p1
			if (!t.matches("\\d+(\\.\\d*)?|\\.\\d+")) return null;
			try {
				double value = Double.parseDouble(t);
				return Double.isFinite(value) && value >= min && value <= max ? value : null;
			} catch (NumberFormatException e) {
				return null;
			}
		}

		public static String format(double value) {
			return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
		}
	}

	record Toggle(String name, String description, Predicate<Settings> get, BiFunction<Settings, Boolean, Settings> set)
			implements Setting {
		public boolean isDefault(Settings settings) {
			return get.test(settings) == get.test(Settings.DEFAULTS);
		}

		public Settings reset(Settings settings) {
			return set.apply(settings, get.test(Settings.DEFAULTS));
		}

		public String defaultText() {
			return get.test(Settings.DEFAULTS) ? "On" : "Off";
		}
	}
}
