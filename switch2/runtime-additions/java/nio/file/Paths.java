package java.nio.file;

/** warsmash-nx: missing from Clearwing 3.1.2. Paths over PathImpl (the JDK Unix rules). */
public final class Paths {
	private Paths() {
	}

	public static Path get(final String first, final String... more) {
		if ((more == null) || (more.length == 0)) {
			return new PathImpl(first);
		}
		final StringBuilder sb = new StringBuilder(first);
		for (final String segment : more) {
			if (!segment.isEmpty()) {
				if (sb.length() > 0) {
					sb.append('/');
				}
				sb.append(segment);
			}
		}
		return new PathImpl(sb.toString());
	}
}
