package java.sql;

/**
 * warsmash-nx: missing from Clearwing 3.1.2. Gson's java.sql adapters construct it; only the
 * millisecond constructor and setTime, as the JDK's (no valueOf/toString date-only form).
 */
public class Date extends java.util.Date {
	public Date(long date) {
		super(date);
	}
}
