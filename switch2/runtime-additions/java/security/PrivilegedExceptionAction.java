package java.security;

/** warsmash-nx: missing from Clearwing 3.1.2. */
public interface PrivilegedExceptionAction<T> {
	T run() throws Exception;
}
