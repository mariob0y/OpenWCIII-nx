package java.nio.file.attribute;

/** warsmash-nx: missing from Clearwing 3.1.2 (appears in Files/FileChannel signatures). */
public interface FileAttribute<T> {
	String name();

	T value();
}
