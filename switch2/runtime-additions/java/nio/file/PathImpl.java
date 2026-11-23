package java.nio.file;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

/**
 * warsmash-nx: Clearwing 3.1.2 declares java.nio.file.Path but has no implementation
 * (File.toPath() returned null, Path.of threw). This is the JDK's Unix path, by its rules: '/'
 * separates names, repeated and trailing separators are dropped, absolute = starts with '/', the
 * empty path has one empty name. Console paths such as "sdmc:/switch/warsmash" are relative by
 * these rules ("sdmc:" is the first name), which is all resolve/relativize need.
 */
final class PathImpl implements Path {
	private final String path; // normalized: no repeated or trailing '/' (except "/")
	private String[] names; // lazily split

	PathImpl(final String input) {
		this.path = normalizeSeparators(input);
	}

	private static String normalizeSeparators(final String input) {
		if (input.indexOf('\u0000') >= 0) {
			throw new InvalidPathException(input, "Nul character not allowed");
		}
		final StringBuilder out = new StringBuilder(input.length());
		char previous = 0;
		for (int i = 0; i < input.length(); i++) {
			final char c = input.charAt(i);
			if ((c == '/') && (previous == '/')) {
				continue;
			}
			out.append(c);
			previous = c;
		}
		int length = out.length();
		if ((length > 1) && (out.charAt(length - 1) == '/')) {
			out.setLength(length - 1);
		}
		return out.toString();
	}

	private String[] names() {
		if (this.names == null) {
			if (this.path.isEmpty()) {
				this.names = new String[] { "" };
			}
			else if (this.path.equals("/")) {
				this.names = new String[0];
			}
			else {
				final ArrayList<String> list = new ArrayList<>();
				int start = this.path.charAt(0) == '/' ? 1 : 0;
				for (int i = start; i <= this.path.length(); i++) {
					if ((i == this.path.length()) || (this.path.charAt(i) == '/')) {
						list.add(this.path.substring(start, i));
						start = i + 1;
					}
				}
				this.names = list.toArray(new String[0]);
			}
		}
		return this.names;
	}

	private static Path join(final boolean absolute, final String[] names, final int from, final int to) {
		final StringBuilder out = new StringBuilder();
		if (absolute) {
			out.append('/');
		}
		for (int i = from; i < to; i++) {
			if (i > from) {
				out.append('/');
			}
			out.append(names[i]);
		}
		return new PathImpl(out.toString());
	}

	private static PathImpl check(final Path other) {
		if (other == null) {
			throw new NullPointerException();
		}
		if (!(other instanceof PathImpl)) {
			throw new ProviderMismatchException();
		}
		return (PathImpl) other;
	}

	@Override
	public FileSystem getFileSystem() {
		return null; // Clearwing has no FileSystem; the String overloads below do not need one
	}

	@Override
	public boolean isAbsolute() {
		return this.path.startsWith("/");
	}

	@Override
	public Path getRoot() {
		return isAbsolute() ? new PathImpl("/") : null;
	}

	@Override
	public Path getFileName() {
		if (this.path.isEmpty()) {
			return this;
		}
		final String[] n = names();
		return n.length == 0 ? null : new PathImpl(n[n.length - 1]);
	}

	@Override
	public Path getParent() {
		final String[] n = names();
		if (n.length == 0) {
			return null;
		}
		if (n.length == 1) {
			return isAbsolute() ? getRoot() : null;
		}
		return join(isAbsolute(), n, 0, n.length - 1);
	}

	@Override
	public int getNameCount() {
		return names().length;
	}

	@Override
	public Path getName(final int index) {
		final String[] n = names();
		if ((index < 0) || (index >= n.length)) {
			throw new IllegalArgumentException();
		}
		return new PathImpl(n[index]);
	}

	@Override
	public Path subpath(final int beginIndex, final int endIndex) {
		final String[] n = names();
		if ((beginIndex < 0) || (beginIndex >= n.length) || (endIndex > n.length) || (beginIndex >= endIndex)) {
			throw new IllegalArgumentException();
		}
		return join(false, n, beginIndex, endIndex);
	}

	@Override
	public boolean startsWith(final Path other) {
		final PathImpl that = check(other);
		if (that.isAbsolute() != isAbsolute()) {
			return false;
		}
		if (that.path.isEmpty()) {
			return this.path.isEmpty();
		}
		final String[] mine = names(), theirs = that.names();
		if (theirs.length > mine.length) {
			return false;
		}
		for (int i = 0; i < theirs.length; i++) {
			if (!mine[i].equals(theirs[i])) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean startsWith(final String other) {
		return startsWith(new PathImpl(other));
	}

	@Override
	public boolean endsWith(final Path other) {
		final PathImpl that = check(other);
		if (that.isAbsolute()) {
			return that.path.equals(this.path);
		}
		if (that.path.isEmpty()) {
			return this.path.isEmpty();
		}
		final String[] mine = names(), theirs = that.names();
		if (theirs.length > mine.length) {
			return false;
		}
		for (int i = 0; i < theirs.length; i++) {
			if (!mine[mine.length - theirs.length + i].equals(theirs[i])) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean endsWith(final String other) {
		return endsWith(new PathImpl(other));
	}

	@Override
	public Path normalize() {
		final String[] n = names();
		final ArrayList<String> out = new ArrayList<>();
		for (final String name : n) {
			if (name.equals(".")) {
				continue;
			}
			if (name.equals("..")) {
				if (!out.isEmpty() && !out.get(out.size() - 1).equals("..")) {
					out.remove(out.size() - 1);
					continue;
				}
				if (isAbsolute()) {
					continue; // "/.." is "/"
				}
			}
			out.add(name);
		}
		return join(isAbsolute(), out.toArray(new String[0]), 0, out.size());
	}

	@Override
	public Path resolve(final Path other) {
		final PathImpl that = check(other);
		if (that.isAbsolute()) {
			return that;
		}
		if (that.path.isEmpty()) {
			return this;
		}
		if (this.path.isEmpty()) {
			return that;
		}
		return new PathImpl(this.path.equals("/") ? "/" + that.path : this.path + "/" + that.path);
	}

	@Override
	public Path resolve(final String other) {
		return resolve(new PathImpl(other));
	}

	@Override
	public Path resolveSibling(final String other) {
		return resolveSibling(new PathImpl(other));
	}

	@Override
	public Path relativize(final Path other) {
		final PathImpl that = check(other);
		if (that.isAbsolute() != isAbsolute()) {
			throw new IllegalArgumentException("'other' is different type of Path");
		}
		if (this.path.equals(that.path)) {
			return new PathImpl("");
		}
		if (this.path.isEmpty()) {
			return that;
		}
		final String[] mine = names(), theirs = that.names();
		final int shorter = Math.min(mine.length, theirs.length);
		int common = 0;
		while ((common < shorter) && mine[common].equals(theirs[common])) {
			common++;
		}
		final StringBuilder out = new StringBuilder();
		for (int i = common; i < mine.length; i++) {
			if (out.length() > 0) {
				out.append('/');
			}
			out.append("..");
		}
		for (int i = common; i < theirs.length; i++) {
			if (out.length() > 0) {
				out.append('/');
			}
			out.append(theirs[i]);
		}
		return new PathImpl(out.toString());
	}

	@Override
	public URI toUri() {
		throw new UnsupportedOperationException("Path.toUri");
	}

	@Override
	public Path toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		return new PathImpl(new File(this.path.isEmpty() ? "." : this.path).getAbsolutePath());
	}

	@Override
	public Path toRealPath(final LinkOption... options) throws IOException {
		final Path real = toAbsolutePath().normalize();
		if (!new File(real.toString()).exists()) {
			throw new NoSuchFileException(this.path);
		}
		return real;
	}

	@Override
	public File toFile() {
		return new File(this.path);
	}

	@Override
	public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>[] events,
			final WatchEvent.Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException("Path.register");
	}

	@Override
	public int compareTo(final Path other) {
		return this.path.compareTo(check(other).path);
	}

	@Override
	public boolean equals(final Object other) {
		return (other instanceof PathImpl) && ((PathImpl) other).path.equals(this.path);
	}

	@Override
	public int hashCode() {
		return this.path.hashCode();
	}

	@Override
	public String toString() {
		return this.path;
	}
}
