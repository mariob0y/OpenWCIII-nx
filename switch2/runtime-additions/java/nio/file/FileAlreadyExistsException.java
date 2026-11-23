package java.nio.file;

/** warsmash-nx: missing from Clearwing 3.1.2. */
public class FileAlreadyExistsException extends FileSystemException {
	public FileAlreadyExistsException(final String file) {
		super(file);
	}

	public FileAlreadyExistsException(final String file, final String other, final String reason) {
		super(file, other, reason);
	}
}
