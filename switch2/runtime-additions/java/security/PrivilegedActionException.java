package java.security;

/** warsmash-nx: missing from Clearwing 3.1.2. As the JDK: wraps the checked exception as the cause. */
public class PrivilegedActionException extends Exception {
	public PrivilegedActionException(Exception exception) {
		super(null, exception);
	}

	public Exception getException() {
		return (Exception) getCause();
	}

	@Override
	public String toString() {
		Throwable cause = getCause();
		return (cause != null) ? (getClass().getName() + ": " + cause.toString()) : getClass().getName();
	}
}
