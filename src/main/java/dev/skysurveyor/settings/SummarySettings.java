package dev.skysurveyor.settings;

import com.google.gson.annotations.SerializedName;

// "summary" in config.json, names never rename
public record SummarySettings(Show show, Style style, Position position, Double seconds) {
	public static final double MIN_SECONDS = 1;
	public static final double MAX_SECONDS = 60;
	// nicks picks 2026-11-13, 5 s = vanillas own toast time
	public static final SummarySettings DEFAULTS = new SummarySettings(Show.TOAST, Style.VANILLA, Position.TOP_RIGHT, 5.0);

	public enum Show {
		@SerializedName("toast")
		TOAST,
		@SerializedName("chat")
		CHAT,
		@SerializedName("both")
		BOTH,
		@SerializedName("off")
		OFF;

		public boolean toast() {
			return this == TOAST || this == BOTH;
		}

		public boolean chat() {
			return this == CHAT || this == BOTH;
		}
	}

	public enum Style {
		@SerializedName("vanilla")
		VANILLA,
		@SerializedName("frame")
		FRAME,
		@SerializedName("dark")
		DARK
	}

	public enum Position {
		@SerializedName("top_right")
		TOP_RIGHT,
		@SerializedName("movable")
		MOVABLE
	}

	// hand edited file, anything missing or weird = default
	public SummarySettings repaired() {
		double time = seconds == null || !(seconds >= MIN_SECONDS && seconds <= MAX_SECONDS) ? DEFAULTS.seconds : seconds;
		return new SummarySettings(show == null ? DEFAULTS.show : show, style == null ? DEFAULTS.style : style,
				position == null ? DEFAULTS.position : position, time);
	}

	public boolean movableToast() {
		return show.toast() && position == Position.MOVABLE;
	}

	public SummarySettings withShow(Show value) {
		return new SummarySettings(value, style, position, seconds);
	}

	public SummarySettings withStyle(Style value) {
		return new SummarySettings(show, value, position, seconds);
	}

	public SummarySettings withPosition(Position value) {
		return new SummarySettings(show, style, value, seconds);
	}

	public SummarySettings withSeconds(double value) {
		return new SummarySettings(show, style, position, value);
	}
}
