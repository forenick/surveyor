package dev.shaftjournal.hud;

import java.util.ArrayDeque;
import java.util.Deque;

// undo/redo for the HUD editor. every step is the whole layout as JSON, so any change
// (moving, a setting, Reset) can be undone the same way. plain java
public final class HudHistory {
	// older steps get forgotten past this many
	static final int LIMIT = 100;

	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private String current;

	public HudHistory(String start) {
		current = start;
	}

	// the state the history is at now
	public String current() {
		return current;
	}

	// adds a step if the state is different from the current one. a new step clears redo
	public boolean record(String state) {
		if (state.equals(current)) return false;
		undo.push(current);
		if (undo.size() > LIMIT) undo.removeLast();
		redo.clear();
		current = state;
		return true;
	}

	// the state to go back to, or null if there's nothing to undo
	public String undo() {
		if (undo.isEmpty()) return null;
		redo.push(current);
		current = undo.pop();
		return current;
	}

	// the state to go forward to, or null if there's nothing to redo
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
