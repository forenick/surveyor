package dev.shaftjournal.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo for the HUD editor: each step is the whole layout as JSON, so any change
 * (moving, a setting, Reset) can be undone the same way. Plain Java.
 */
public final class HudHistory {
	/** Older steps are forgotten past this many. */
	static final int LIMIT = 100;

	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private String current;

	public HudHistory(String start) {
		current = start;
	}

	/** The state the history is at. */
	public String current() {
		return current;
	}

	/** Adds a step when the state differs from the current one; a new step clears redo. */
	public boolean record(String state) {
		if (state.equals(current)) return false;
		undo.push(current);
		if (undo.size() > LIMIT) undo.removeLast();
		redo.clear();
		current = state;
		return true;
	}

	/** @return the state to go back to, or null when there's nothing to undo */
	public String undo() {
		if (undo.isEmpty()) return null;
		redo.push(current);
		current = undo.pop();
		return current;
	}

	/** @return the state to go forward to, or null when there's nothing to redo */
	public String redo() {
		if (redo.isEmpty()) return null;
		undo.push(current);
		current = redo.pop();
		return current;
	}

	public boolean canUndo() {
		return !undo.isEmpty();
	}

	public boolean canRedo() {
		return !redo.isEmpty();
	}
}
