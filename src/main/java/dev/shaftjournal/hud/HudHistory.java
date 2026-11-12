package dev.shaftjournal.hud;

import java.util.ArrayDeque;
import java.util.Deque;

public final class HudHistory {
	static final int LIMIT = 100;

	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();
	private String current;

	public HudHistory(String start) {
		current = start;
	}

	public String current() {
		return current;
	}

	public boolean record(String state) {
		if (state.equals(current)) return false;
		undo.push(current);
		if (undo.size() > LIMIT) undo.removeLast();
		redo.clear();
		current = state;
		return true;
	}

	public String undo() {
		if (undo.isEmpty()) return null;
		redo.push(current);
		current = undo.pop();
		return current;
	}

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
