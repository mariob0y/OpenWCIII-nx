package java.util;

/**
 * warsmash-nx: missing from Clearwing 3.1.2. Gson's Currency adapter needs getInstance and
 * getCurrencyCode. No ISO 4217 table here: any three upper-case letters are accepted (the JDK
 * accepts only known codes), one shared instance per code, as the JDK.
 */
public final class Currency {
	private static final HashMap<String, Currency> INSTANCES = new HashMap<>();
	private final String currencyCode;

	private Currency(String currencyCode) {
		this.currencyCode = currencyCode;
	}

	public static Currency getInstance(String currencyCode) {
		if (currencyCode.length() != 3)
			throw new IllegalArgumentException();
		for (int i = 0; i < 3; i++) {
			char c = currencyCode.charAt(i);
			if (c < 'A' || c > 'Z')
				throw new IllegalArgumentException();
		}
		synchronized (INSTANCES) {
			Currency currency = INSTANCES.get(currencyCode);
			if (currency == null) {
				currency = new Currency(currencyCode);
				INSTANCES.put(currencyCode, currency);
			}
			return currency;
		}
	}

	public String getCurrencyCode() {
		return currencyCode;
	}

	@Override
	public String toString() {
		return currencyCode;
	}
}
