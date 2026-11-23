package java.nio.file;

/** warsmash-nx: missing from Clearwing 3.1.2 (thrown by PathImpl). */
public class InvalidPathException extends IllegalArgumentException {
	private final String input;
	private final int index;

	public InvalidPathException(final String input, final String reason, final int index) {
		super(reason);
		if ((input == null) || (reason == null)) {
			throw new NullPointerException();
		}
		this.input = input;
		this.index = index;
	}

	public InvalidPathException(final String input, final String reason) {
		this(input, reason, -1);
	}

	public String getInput() {
		return this.input;
	}

	public String getReason() {
		return super.getMessage();
	}

	public int getIndex() {
		return this.index;
	}

	@Override
	public String getMessage() {
		return getReason() + (this.index > -1 ? " at index " + this.index : "") + ": " + this.input;
	}
}
