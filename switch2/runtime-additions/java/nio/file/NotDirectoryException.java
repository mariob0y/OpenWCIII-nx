package java.nio.file;

/** warsmash-nx: missing from Clearwing 3.1.2. */
public class NotDirectoryException extends FileSystemException {
	public NotDirectoryException(final String file) {
		super(file);
	}
}
