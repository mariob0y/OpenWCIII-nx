package com.etheller.warsmash.util;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.nio.charset.StandardCharsets;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.table.DataTableSource;
import com.etheller.warsmash.util.table.TableDataSource;

/**
 * A structure that holds mapped data from INI and SLK files.
 *
 * In the case of SLK files, the first row is expected to hold the names of the
 * columns.
 */
public class MappedData implements Iterable<Map.Entry<String, MappedDataRow>> {
	private final Map<String, MappedDataRow> map = new HashMap<>();

	public MappedData() {
		this(null);
	}

	public MappedData(final String buffer) {
		if (buffer != null) {
			load(buffer);
		}
	}

	/**
	 * Load data from an SLK file or an INI file.
	 *
	 * Note that this may override previous properties!
	 */
	public void load(final String buffer) {
		if (buffer == null) {
			return;
		}
		final boolean slkLike = buffer.startsWith("ID;");
		final DataTable table = new DataTable(StringBundle.EMPTY);
		try (ByteArrayInputStream stream = new ByteArrayInputStream(buffer.getBytes(StandardCharsets.UTF_8))) {
			if (slkLike) {
				table.readSLK(stream);
			}
			else {
				table.readTXT(stream, true);
			}
		}
		catch (final Exception e) {
			throw new RuntimeException("Failed to parse mapped data buffer", e);
		}
		load(new DataTableSource(table), slkLike);
	}

	/**
	 * Load data from any table-backed data source.
	 *
	 * Note that this may override previous properties!
	 */
	public void load(final TableDataSource dataSource) {
		load(dataSource, false);
	}

	private void load(final TableDataSource dataSource, final boolean coerceSlkTypes) {
		if (dataSource == null) {
			return;
		}
		for (final String row : dataSource.rowKeys()) {
			if (row == null) {
				continue;
			}
			final String normalizedRow = row.toLowerCase();
			MappedDataRow mapped = this.map.get(normalizedRow);
			if (mapped == null) {
				mapped = new MappedDataRow();
				this.map.put(normalizedRow, mapped);
			}
			for (final String column : dataSource.columnKeys(row)) {
				if (column == null) {
					continue;
				}
				final Object rawValue = dataSource.getRaw(row, column);
				mapped.put(column, coerceSlkTypes ? coerceSlkValue(rawValue) : rawValue);
			}
		}
	}

	/**
	 * warsmash-nx: merges rows from another MappedData without mutating {@code other}'s rows.
	 */
	public void loadFrom(final MappedData other) {
		if (other == null) {
			return;
		}
		for (final Map.Entry<String, MappedDataRow> entry : other.map.entrySet()) {
			final String key = entry.getKey();
			final MappedDataRow existing = this.map.get(key);
			if (existing == null) {
				final MappedDataRow copy = new MappedDataRow();
				copy.putAll(entry.getValue());
				this.map.put(key, copy);
			}
			else {
				existing.putAll(entry.getValue());
			}
		}
	}

	private static Object coerceSlkValue(final Object rawValue) {
		if (!(rawValue instanceof String)) {
			return rawValue;
		}
		final String stringValue = (String) rawValue;
		if ("TRUE".equalsIgnoreCase(stringValue)) {
			return Boolean.TRUE;
		}
		if ("FALSE".equalsIgnoreCase(stringValue)) {
			return Boolean.FALSE;
		}
		if (!looksLikeFloat(stringValue)) {
			return stringValue;
		}
		try {
			return Float.parseFloat(stringValue);
		}
		catch (final NumberFormatException ignored) {
			return stringValue;
		}
	}

	/**
	 * warsmash-nx: fast syntax check before Float.parseFloat. In SLK tables most cells are strings
	 * ("_", paths, rawcodes, comma lists); throwing NumberFormatException on Clearwing captures a
	 * full stack trace per cell and was ~13.7% of total map load time.
	 */
	private static boolean looksLikeFloat(final String s) {
		final int len = s.length();
		if (len == 0) {
			return false;
		}
		int i = 0;
		while ((i < len) && (s.charAt(i) <= ' ')) {
			i++;
		}
		int end = len;
		while ((end > i) && (s.charAt(end - 1) <= ' ')) {
			end--;
		}
		if (i >= end) {
			return false;
		}
		char c = s.charAt(i);
		if ((c == '+') || (c == '-')) {
			i++;
			if (i >= end) {
				return false;
			}
		}
		boolean hasDigit = false;
		boolean hasDot = false;
		boolean hasExp = false;
		for (; i < end; i++) {
			c = s.charAt(i);
			if ((c >= '0') && (c <= '9')) {
				hasDigit = true;
			}
			else if ((c == '.') && !hasDot && !hasExp) {
				hasDot = true;
			}
			else if (((c == 'e') || (c == 'E')) && hasDigit && !hasExp) {
				hasExp = true;
				hasDigit = false; // exponent requires at least one digit
				if ((i + 1 < end) && ((s.charAt(i + 1) == '+') || (s.charAt(i + 1) == '-'))) {
					i++;
				}
			}
			else {
				return false;
			}
		}
		return hasDigit;
	}

	public MappedDataRow getRow(final String key) {
		return this.map.get(key.toLowerCase());
	}

	public Object getProperty(final String key, final String name) {
		return this.map.get(key.toLowerCase()).get(name);
	}

	public void setRow(final String key, final MappedDataRow values) {
		this.map.put(key.toLowerCase(), values);
	}

	@Override
	public Iterator<Entry<String, MappedDataRow>> iterator() {
		return this.map.entrySet().iterator();
	}
}
