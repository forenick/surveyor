package dev.skysurveyor.settings;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

// config.json for the whole mod. get() from any thread, the file only on the io thread its handed
public final class LiveSettings {
	private final List<BiConsumer<Settings, Settings>> listeners = new CopyOnWriteArrayList<>();
	private volatile ConfigFile config;
	private Executor io;

	// io = one thread, saves + reloads must run in the order they were asked
	public void start(Path folder, Executor io) {
		this.io = io;
		config = new ConfigFile(folder);
	}

	public Settings get() {
		return config == null ? Settings.DEFAULTS : config.get();
	}

	// settings page. built on memory now, each changed section saved on the io thread
	public Settings change(Function<Settings, Settings> change) {
		Settings before = get();
		Settings after = change.apply(before);
		if (config == null || after.equals(before)) return after;
		for (ConfigSection<?> section : ConfigSection.ALL) apply(section, before, after);
		for (BiConsumer<Settings, Settings> listener : listeners) listener.accept(before, after);
		return after;
	}

	private <T> void apply(ConfigSection<T> section, Settings before, Settings after) {
		T old = section.of(before);
		T value = section.of(after);
		if (value.equals(old)) return;
		config.set(section, value);
		io.execute(() -> config.save(section, old, value));
	}

	// (before, after) right after change(), on its thread. not for hand edits, those come in w/ reloadIfChanged
	public void onChange(BiConsumer<Settings, Settings> listener) {
		listeners.add(listener);
	}

	// null = saved fine
	public String saveError() {
		return config == null ? null : config.saveError();
	}

	// io thread
	public void reloadIfChanged() {
		if (config != null) config.reloadIfChanged();
	}
}
