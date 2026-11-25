package com.etheller.warsmash.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import com.etheller.warsmash.datasources.DataSource;

public class WorldEditStrings implements StringBundle {
	private ResourceBundle bundle;
	private ResourceBundle bundlegs;

	private static final java.util.Map<String, ResourceBundle> BUNDLE_CACHE = new java.util.HashMap<>();

	private static ResourceBundle loadBundle(final DataSource dataSource, final String path) {
		final boolean overridden = com.etheller.warsmash.datasources.CompoundDataSource
				.hasNonFolderOverride(dataSource, path);
		final String key = WarsmashConstants.GAME_VERSION + "|" + path.toLowerCase();
		if (!overridden) {
			synchronized (BUNDLE_CACHE) {
				final ResourceBundle cached = BUNDLE_CACHE.get(key);
				if (cached != null) {
					return cached;
				}
			}
		}
		try (InputStream fis = dataSource.getResourceAsStream(path);
				java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(fis, "utf-8"), 16384)) {
			final ResourceBundle loaded = new PropertyResourceBundle(reader);
			if (!overridden) {
				synchronized (BUNDLE_CACHE) {
					BUNDLE_CACHE.put(key, loaded);
				}
			}
			return loaded;
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public WorldEditStrings(final DataSource dataSource) {
		if (dataSource.has("UI\\WorldEditStrings.txt")) {
			this.bundle = loadBundle(dataSource, "UI\\WorldEditStrings.txt");
		}
		this.bundlegs = loadBundle(dataSource, "UI\\WorldEditGameStrings.txt");
	}

	@Override
	public String getString(String string) {
		try {
			while (string.toUpperCase().startsWith("WESTRING")) {
				string = internalGetString(string);
			}
			return string;
		}
		catch (final MissingResourceException exc) {
			try {
				return this.bundlegs.getString(string.toUpperCase());
			}
			catch (final MissingResourceException exc2) {
				return string;
			}
		}
	}

	private String internalGetString(final String key) {
		if (this.bundle == null) {
			return this.bundlegs.getString(key.toUpperCase());
		}
		try {
			String string = this.bundle.getString(key.toUpperCase());
			if ((string.charAt(0) == '"') && (string.length() >= 2) && (string.charAt(string.length() - 1) == '"')) {
				string = string.substring(1, string.length() - 1);
			}
			return string;
		}
		catch (final MissingResourceException exc) {
			return this.bundlegs.getString(key.toUpperCase());
		}
	}

	@Override
	public String getStringCaseSensitive(final String key) {
		if (this.bundle == null) {
			return this.bundlegs.getString(key);
		}
		try {
			return this.bundle.getString(key);
		}
		catch (final MissingResourceException exc) {
			return this.bundlegs.getString(key);
		}
	}
}
