package java.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * warsmash-nx: missing from Clearwing 3.1.2 (whose ResourceBundle.getString returns the key itself).
 * Like the JDK: loads with Properties.load and throws MissingResourceException for a missing key;
 * Warsmash's WorldEditStrings relies on that (with the key returned, its WESTRING loop never ends).
 */
public class PropertyResourceBundle extends ResourceBundle {
	private final Map<String, Object> lookup;

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public PropertyResourceBundle(final InputStream stream) throws IOException {
		final Properties properties = new Properties();
		properties.load(stream);
		this.lookup = new HashMap(properties);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public PropertyResourceBundle(final Reader reader) throws IOException {
		final Properties properties = new Properties();
		properties.load(reader);
		this.lookup = new HashMap(properties);
	}

	public Object handleGetObject(final String key) {
		if (key == null) {
			throw new NullPointerException();
		}
		return this.lookup.get(key);
	}

	@Override
	public String getString(final String key) {
		final Object value = handleGetObject(key);
		if (value == null) {
			throw new MissingResourceException("Can't find resource for bundle " + getClass().getName() + ", key "
					+ key, getClass().getName(), key);
		}
		return (String) value;
	}

	public boolean containsKey(final String key) {
		return handleGetObject(key) != null;
	}

	@Override
	public Enumeration<String> getKeys() {
		return Collections.enumeration(this.lookup.keySet());
	}

	public Set<String> keySet() {
		return this.lookup.keySet();
	}
}
