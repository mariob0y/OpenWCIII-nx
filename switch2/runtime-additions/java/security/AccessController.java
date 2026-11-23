package java.security;

/**
 * warsmash-nx: missing from Clearwing 3.1.2. There is no security manager on this platform, so
 * every action runs directly (what the JDK does when none is installed). Gson uses it to read
 * enum constants reflectively.
 */
public final class AccessController {
	private AccessController() {
	}

	public static <T> T doPrivileged(PrivilegedAction<T> action) {
		return action.run();
	}

	public static <T> T doPrivileged(PrivilegedExceptionAction<T> action) throws PrivilegedActionException {
		try {
			return action.run();
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new PrivilegedActionException(e);
		}
	}
}
