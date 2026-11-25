package com.etheller.warsmash.parsers.fdf.datamodel;

import java.util.HashMap;
import java.util.Map;

public class FrameTemplateEnvironment {
	private final FrameTemplateEnvironment parent;
	private final Map<String, String> idToDecoratedString = new HashMap<>();
	private final Map<String, FrameDefinition> idToFrame = new HashMap<>();

	public FrameTemplateEnvironment() {
		this(null);
	}

	public FrameTemplateEnvironment(final FrameTemplateEnvironment parent) {
		this.parent = parent;
	}

	public void addDecoratedString(final String id, final String value) {
		this.idToDecoratedString.put(id, value);
	}

	public String getDecoratedString(final String id) {
		final String decoratedString = this.idToDecoratedString.get(id);
		if (decoratedString != null) {
			return decoratedString;
		}
		if (this.parent != null) {
			return this.parent.getDecoratedString(id);
		}
		return id;
	}

	public void put(final String id, final FrameDefinition frame) {
		this.idToFrame.put(id, frame);
	}

	public FrameDefinition getFrame(final String id) {
		final FrameDefinition frame = this.idToFrame.get(id);
		if ((frame == null) && (this.parent != null)) {
			return this.parent.getFrame(id);
		}
		return frame;
	}

	public void putAll(final FrameTemplateEnvironment other) {
		this.idToDecoratedString.putAll(other.idToDecoratedString);
		this.idToFrame.putAll(other.idToFrame);
	}
}
