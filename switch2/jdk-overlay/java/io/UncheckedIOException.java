package java.io;

import java.util.Objects;

/** warsmash-nx: missing from Clearwing 3.1.2 (Java 8 API). */
public class UncheckedIOException extends RuntimeException {
	private static final long serialVersionUID = -8134305061645241065L;

	public UncheckedIOException(final String message, final IOException cause) {
		super(message, Objects.requireNonNull(cause));
	}

	public UncheckedIOException(final IOException cause) {
		super(Objects.requireNonNull(cause));
	}

	@Override
	public IOException getCause() {
		return (IOException) super.getCause();
	}
}
