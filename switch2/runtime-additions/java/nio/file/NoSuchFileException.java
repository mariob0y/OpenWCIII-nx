package java.nio.file;

/** warsmash-nx: missing from Clearwing 3.1.2. */
public class NoSuchFileException extends FileSystemException {
	public NoSuchFileException(final String file) {
		super(file);
	}

	public NoSuchFileException(final String file, final String other, final String reason) {
		super(file, other, reason);
	}
}
