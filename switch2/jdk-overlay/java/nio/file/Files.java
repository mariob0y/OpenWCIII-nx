package java.nio.file;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * warsmash-nx: missing from Clearwing 3.1.2. The part of java.nio.file.Files that Warsmash uses,
 * over java.io.File, FileInputStream/FileOutputStream and the overlay's FileChannel, with the JDK's
 * exceptions (NoSuchFileException, FileAlreadyExistsException). No walk(): there is no
 * java.util.stream (Warsmash's one walk is replaced on nx-port).
 */
public final class Files {
	private Files() {
	}

	private static File file(final Path path) {
		return new File(path.toString());
	}

	public static boolean exists(final Path path, final LinkOption... options) {
		return file(path).exists();
	}

	public static boolean notExists(final Path path, final LinkOption... options) {
		return !file(path).exists();
	}

	public static boolean isDirectory(final Path path, final LinkOption... options) {
		final File f = file(path);
		return f.exists() && f.isDirectory();
	}

	public static boolean isRegularFile(final Path path, final LinkOption... options) {
		final File f = file(path);
		return f.exists() && !f.isDirectory();
	}

	public static boolean isReadable(final Path path) {
		return file(path).exists();
	}

	public static long size(final Path path) throws IOException {
		final File f = file(path);
		if (!f.exists()) {
			throw new NoSuchFileException(path.toString());
		}
		return f.length();
	}

	private static void requireFile(final Path path) throws IOException {
		final File f = file(path);
		if (!f.exists()) {
			throw new NoSuchFileException(path.toString());
		}
		if (f.isDirectory()) {
			throw new FileSystemException(path.toString(), null, "Is a directory");
		}
	}

	public static InputStream newInputStream(final Path path, final OpenOption... options) throws IOException {
		for (final OpenOption option : options) {
			if ((option == StandardOpenOption.APPEND) || (option == StandardOpenOption.WRITE)) {
				throw new UnsupportedOperationException("'" + option + "' not allowed");
			}
		}
		requireFile(path);
		return new FileInputStream(file(path));
	}

	public static OutputStream newOutputStream(final Path path, final OpenOption... options) throws IOException {
		final Set<OpenOption> set = new HashSet<>(Arrays.asList(options));
		if (set.isEmpty()) {
			set.add(StandardOpenOption.CREATE);
			set.add(StandardOpenOption.TRUNCATE_EXISTING);
		}
		if (set.contains(StandardOpenOption.READ)) {
			throw new IllegalArgumentException("READ not allowed");
		}
		final File f = file(path);
		if (set.contains(StandardOpenOption.CREATE_NEW) && f.exists()) {
			throw new FileAlreadyExistsException(path.toString());
		}
		if (!f.exists() && !set.contains(StandardOpenOption.CREATE) && !set.contains(StandardOpenOption.CREATE_NEW)) {
			throw new NoSuchFileException(path.toString());
		}
		return new FileOutputStream(f, set.contains(StandardOpenOption.APPEND));
	}

	public static SeekableByteChannel newByteChannel(final Path path, final OpenOption... options)
			throws IOException {
		return FileChannel.open(path, options);
	}

	public static SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options,
			final FileAttribute<?>... attrs) throws IOException {
		return FileChannel.open(path, options, attrs);
	}

	public static byte[] readAllBytes(final Path path) throws IOException {
		requireFile(path);
		try (InputStream in = new FileInputStream(file(path))) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(32, file(path).length()));
			final byte[] buffer = new byte[65536];
			int n;
			while ((n = in.read(buffer)) > 0) {
				out.write(buffer, 0, n);
			}
			return out.toByteArray();
		}
	}

	public static List<String> readAllLines(final Path path) throws IOException {
		return readAllLines(path, Charset.forName("UTF-8"));
	}

	public static List<String> readAllLines(final Path path, final Charset cs) throws IOException {
		requireFile(path);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file(path)), cs))) {
			final List<String> result = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				result.add(line);
			}
			return result;
		}
	}

	public static Path write(final Path path, final byte[] bytes, final OpenOption... options) throws IOException {
		try (OutputStream out = newOutputStream(path, options)) {
			out.write(bytes);
		}
		return path;
	}

	public static long copy(final InputStream in, final Path target, final CopyOption... options)
			throws IOException {
		boolean replace = false;
		for (final CopyOption option : options) {
			if (option == StandardCopyOption.REPLACE_EXISTING) {
				replace = true;
			}
			else if (option != null) {
				throw new UnsupportedOperationException("Unsupported copy option");
			}
		}
		final File f = file(target);
		if (f.exists()) {
			if (!replace) {
				throw new FileAlreadyExistsException(target.toString());
			}
			if (!f.delete()) {
				throw new FileSystemException(target.toString(), null, "could not replace");
			}
		}
		long total = 0;
		try (OutputStream out = new FileOutputStream(f)) {
			final byte[] buffer = new byte[65536];
			int n;
			while ((n = in.read(buffer)) > 0) {
				out.write(buffer, 0, n);
				total += n;
			}
		}
		return total;
	}

	public static long copy(final Path source, final Path target, final CopyOption... options) throws IOException {
		requireFile(source);
		try (InputStream in = new FileInputStream(file(source))) {
			return copy(in, target, options);
		}
	}

	public static String readString(final Path path) throws IOException {
		return readString(path, java.nio.charset.StandardCharsets.UTF_8);
	}

	public static String readString(final Path path, final Charset cs) throws IOException {
		return new String(readAllBytes(path), cs);
	}

	public static Path writeString(final Path path, final CharSequence text, final OpenOption... options)
			throws IOException {
		return writeString(path, text, java.nio.charset.StandardCharsets.UTF_8, options);
	}

	public static Path writeString(final Path path, final CharSequence text, final Charset cs,
			final OpenOption... options) throws IOException {
		return write(path, text.toString().getBytes(cs), options);
	}

	public static BufferedReader newBufferedReader(final Path path) throws IOException {
		return newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8);
	}

	public static BufferedReader newBufferedReader(final Path path, final Charset cs) throws IOException {
		return new BufferedReader(new InputStreamReader(newInputStream(path), cs));
	}

	private static int tempCounter;

	/** A new empty file in {@code dir}: prefix + a unique number + suffix (".tmp" when null). */
	public static Path createTempFile(final Path dir, final String prefix, final String suffix,
			final FileAttribute<?>... attrs) throws IOException {
		final File directory = file(dir);
		for (int attempt = 0; attempt < 1000; attempt++) {
			final long id = (System.nanoTime() & 0x7FFFFFFFL) + (tempCounter++);
			final File f = new File(directory, (prefix == null ? "" : prefix) + id + (suffix == null ? ".tmp" : suffix));
			if (f.createNewFile()) {
				return Paths.get(f.getPath());
			}
		}
		throw new FileSystemException(dir.toString(), null, "could not create a temporary file");
	}

	/**
	 * Renames source to target, or copies and deletes when the rename fails (always, under Clearwing 3.1.2).
	 * ATOMIC_MOVE is accepted but not atomic. REPLACE_EXISTING deletes the target first.
	 */
	public static Path move(final Path source, final Path target, final CopyOption... options) throws IOException {
		boolean replace = false;
		for (final CopyOption option : options) {
			if (option == StandardCopyOption.REPLACE_EXISTING) {
				replace = true;
			}
		}
		final File from = file(source);
		final File to = file(target);
		if (!from.exists()) {
			throw new NoSuchFileException(source.toString());
		}
		if (to.exists()) {
			if (!replace) {
				throw new FileAlreadyExistsException(target.toString());
			}
			if (!to.delete()) {
				throw new FileSystemException(target.toString(), null, "could not replace");
			}
		}
		// Clearwing's File.renameTo always returns false: copy, then delete the source
		if (!from.renameTo(to)) {
			moveByCopy(from, to);
		}
		return target;
	}

	private static void moveByCopy(final File from, final File to) throws IOException {
		if (from.isDirectory()) {
			if (!to.mkdir() && !to.isDirectory()) {
				throw new FileSystemException(to.getPath(), null, "could not create directory");
			}
			final File[] children = from.listFiles();
			if (children != null) {
				for (final File child : children) {
					moveByCopy(child, new File(to, child.getName()));
				}
			}
		}
		else {
			try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
				final byte[] buffer = new byte[65536];
				int n;
				while ((n = in.read(buffer)) > 0) {
					out.write(buffer, 0, n);
				}
			}
		}
		if (!from.delete()) {
			throw new FileSystemException(from.getPath(), null, "moved, but could not delete the source");
		}
	}

	public static void delete(final Path path) throws IOException {
		final File f = file(path);
		if (!f.exists()) {
			throw new NoSuchFileException(path.toString());
		}
		if (!f.delete()) {
			throw new FileSystemException(path.toString(), null, "could not delete");
		}
	}

	public static boolean deleteIfExists(final Path path) throws IOException {
		final File f = file(path);
		if (!f.exists()) {
			return false;
		}
		delete(path);
		return true;
	}

	public static Path createDirectories(final Path dir, final FileAttribute<?>... attrs) throws IOException {
		final File f = file(dir);
		if (f.exists()) {
			if (!f.isDirectory()) {
				throw new FileAlreadyExistsException(dir.toString());
			}
			return dir;
		}
		// Clearwing's File.mkdirs creates the deepest directory first, which fails on a new tree: create
		// each missing level from the top
		final List<File> missing = new ArrayList<>();
		for (File d = f.getAbsoluteFile(); (d != null) && !d.exists(); d = d.getParentFile()) {
			missing.add(0, d);
		}
		for (final File d : missing) {
			if (!d.mkdir() && !d.isDirectory()) {
				throw new FileSystemException(d.getPath(), null, "could not create directory");
			}
		}
		return dir;
	}

	public static DirectoryStream<Path> newDirectoryStream(final Path dir) throws IOException {
		return newDirectoryStream(dir, new DirectoryStream.Filter<Path>() {
			@Override
			public boolean accept(final Path entry) {
				return true;
			}
		});
	}

	/** Glob: '*', '?', '{a,b}' and literal characters (enough for "*.idx"-style patterns). */
	public static DirectoryStream<Path> newDirectoryStream(final Path dir, final String glob) throws IOException {
		if ("*".equals(glob)) {
			return newDirectoryStream(dir);
		}
		final String regex = globToRegex(glob);
		return newDirectoryStream(dir, new DirectoryStream.Filter<Path>() {
			@Override
			public boolean accept(final Path entry) {
				final Path name = entry.getFileName();
				return (name != null) && name.toString().matches(regex);
			}
		});
	}

	public static DirectoryStream<Path> newDirectoryStream(final Path dir,
			final DirectoryStream.Filter<? super Path> filter) throws IOException {
		final File f = file(dir);
		if (!f.exists()) {
			throw new NoSuchFileException(dir.toString());
		}
		if (!f.isDirectory()) {
			throw new NotDirectoryException(dir.toString());
		}
		final String[] names = f.list();
		final List<Path> entries = new ArrayList<>();
		if (names != null) {
			for (final String name : names) {
				final Path entry = dir.resolve(name);
				if (filter.accept(entry)) {
					entries.add(entry);
				}
			}
		}
		return new DirectoryStream<Path>() {
			private boolean iterated;

			@Override
			public Iterator<Path> iterator() {
				if (this.iterated) {
					throw new IllegalStateException("Iterator already obtained");
				}
				this.iterated = true;
				return Collections.unmodifiableList(entries).iterator();
			}

			@Override
			public void close() {
			}
		};
	}

	private static String globToRegex(final String glob) {
		final StringBuilder regex = new StringBuilder();
		boolean inGroup = false;
		for (int i = 0; i < glob.length(); i++) {
			final char c = glob.charAt(i);
			switch (c) {
			case '*':
				regex.append("[^/]*");
				break;
			case '?':
				regex.append("[^/]");
				break;
			case '{':
				regex.append("(?:");
				inGroup = true;
				break;
			case '}':
				regex.append(')');
				inGroup = false;
				break;
			case ',':
				regex.append(inGroup ? "|" : ",");
				break;
			default:
				if ("\\.[]()^$+|".indexOf(c) >= 0) {
					regex.append('\\');
				}
				regex.append(c);
			}
		}
		return regex.toString();
	}
}
