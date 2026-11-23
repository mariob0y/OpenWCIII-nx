package java.nio.file;

/** warsmash-nx: missing from Clearwing 3.1.2. The overlay's Files.move never throws it. */
public class AtomicMoveNotSupportedException extends FileSystemException {
	private static final long serialVersionUID = 5402760225333135579L;

	public AtomicMoveNotSupportedException(final String source, final String target, final String reason) {
		super(source, target, reason);
	}
}
