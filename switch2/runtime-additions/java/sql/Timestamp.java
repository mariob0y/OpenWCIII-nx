package java.sql;

/**
 * warsmash-nx: missing from Clearwing 3.1.2 (see java.sql.Date). As the JDK, the sub-second part
 * is kept as nanos and getTime() still returns the full milliseconds.
 */
public class Timestamp extends java.util.Date {
	private int nanos;

	public Timestamp(long time) {
		super((time / 1000) * 1000);
		nanos = (int) ((time % 1000) * 1000000);
		if (nanos < 0) {
			nanos = 1000000000 + nanos;
			super.setTime(((time / 1000) - 1) * 1000);
		}
	}

	@Override
	public long getTime() {
		return super.getTime() + (nanos / 1000000);
	}

	public int getNanos() {
		return nanos;
	}
}
