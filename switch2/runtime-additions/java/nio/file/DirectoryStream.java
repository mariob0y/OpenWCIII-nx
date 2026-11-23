package java.nio.file;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

/** warsmash-nx: missing from Clearwing 3.1.2. */
public interface DirectoryStream<T> extends Closeable, Iterable<T> {
	@FunctionalInterface
	public static interface Filter<T> {
		boolean accept(T entry) throws IOException;
	}

	@Override
	Iterator<T> iterator();
}
