package java.nio.file;

import java.io.IOException;

/** warsmash-nx: missing from Clearwing 3.1.2; the JDK message format. */
public class FileSystemException extends IOException {
	private final String file;
	private final String other;

	public FileSystemException(final String file) {
		super((String) null);
		this.file = file;
		this.other = null;
	}

	public FileSystemException(final String file, final String other, final String reason) {
		super(reason);
		this.file = file;
		this.other = other;
	}

	public String getFile() {
		return this.file;
	}

	public String getOtherFile() {
		return this.other;
	}

	public String getReason() {
		return super.getMessage();
	}

	@Override
	public String getMessage() {
		if ((this.file == null) && (this.other == null)) {
			return getReason();
		}
		final StringBuilder sb = new StringBuilder();
		if (this.file != null) {
			sb.append(this.file);
		}
		if (this.other != null) {
			sb.append(" -> ").append(this.other);
		}
		if (getReason() != null) {
			sb.append(": ").append(getReason());
		}
		return sb.toString();
	}
}
