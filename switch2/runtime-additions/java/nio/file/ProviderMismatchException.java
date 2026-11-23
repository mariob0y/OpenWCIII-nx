package java.nio.file;

/** warsmash-nx: missing from Clearwing 3.1.2. */
public class ProviderMismatchException extends IllegalArgumentException {
	public ProviderMismatchException() {
	}

	public ProviderMismatchException(final String msg) {
		super(msg);
	}
}
