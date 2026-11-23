package java.nio.channels;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Replaces Clearwing's stub (only map(), which returned null). A channel over the FILE* of a
 * RandomAccessFile, sharing its handle and position like the JDK's. Every call is synchronized on
 * the RandomAccessFile, so stream and channel calls do not interleave mid-operation.
 *
 * warsmash-nx (Warsmash, 2026-11-20): FileChannel.open(Path, ...) with the JDK's option rules
 * (READ default; WRITE needs CREATE/CREATE_NEW for a missing file; TRUNCATE_EXISTING; APPEND),
 * force, truncate. map() is still unsupported (only the CASC reader maps, not used on the console).
 */
public class FileChannel implements SeekableByteChannel {
	/*JNI
	#include <cstdio>
	#include <unistd.h>
	*/

	private final Object lock;
	private final long[] fd;
	private final boolean writable;

	private FileChannel(final Object lock, final long[] fd, final boolean writable) {
		this.lock = lock;
		this.fd = fd;
		this.writable = writable;
	}

	/** For java.io.RandomAccessFile only: a channel on its handle (one-element array, 0 = closed). */
	public static FileChannel shareHandle(final Object lock, final long[] fd, final boolean writable) {
		return new FileChannel(lock, fd, writable);
	}

	public static FileChannel open(final Path path, final OpenOption... options) throws IOException {
		return open(path, new HashSet<OpenOption>(Arrays.asList(options)));
	}

	public static FileChannel open(final Path path, final Set<? extends OpenOption> options,
			final FileAttribute<?>... attrs) throws IOException {
		final boolean write = options.contains(StandardOpenOption.WRITE)
				|| options.contains(StandardOpenOption.APPEND);
		if (options.contains(StandardOpenOption.READ) && options.contains(StandardOpenOption.APPEND)) {
			throw new IllegalArgumentException("READ + APPEND not allowed");
		}
		if (options.contains(StandardOpenOption.APPEND) && options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
			throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING not allowed");
		}
		final File file = new File(path.toString());
		final boolean exists = file.exists();
		if (write && options.contains(StandardOpenOption.CREATE_NEW) && exists) {
			throw new FileAlreadyExistsException(path.toString());
		}
		if (!exists && (!write || (!options.contains(StandardOpenOption.CREATE)
				&& !options.contains(StandardOpenOption.CREATE_NEW)))) {
			throw new NoSuchFileException(path.toString());
		}
		final RandomAccessFile raf = new RandomAccessFile(file, write ? "rw" : "r");
		final FileChannel channel = raf.getChannel();
		if (write && options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
			channel.truncate(0);
		}
		if (options.contains(StandardOpenOption.APPEND)) {
			channel.position(channel.size());
		}
		return channel;
	}

	private static native int truncate0(long file, long size); /*
		FILE* f = (FILE*) file;
		if (fflush(f) != 0)
			return -2;
		return ftruncate(fileno(f), (off_t) size) == 0 ? 0 : -2;
	*/

	private static native int flush0(long file); /*
		FILE* f = (FILE*) file;
		if (fflush(f) != 0)
			return -2;
		return fsync(fileno(f)) == 0 ? 0 : -2;
	*/

	private static native int readAt0(long file, ByteBuffer buffer, int offset, int len, long position); /*
		FILE* f = (FILE*) file;
		if (position >= 0 && fseeko(f, (off_t) position, SEEK_SET) != 0)
			return -2;
		size_t n = fread((char*) buffer + offset, 1, (size_t) len, f);
		if (n == 0)
			return ferror(f) ? -2 : -1;
		return (jint) n;
	*/

	private static native int writeAt0(long file, ByteBuffer buffer, int offset, int len, long position); /*
		FILE* f = (FILE*) file;
		if (position >= 0 && fseeko(f, (off_t) position, SEEK_SET) != 0)
			return -2;
		size_t n = fwrite((const char*) buffer + offset, 1, (size_t) len, f);
		return n < (size_t) len ? -2 : (jint) n;
	*/

	private static native long tell0(long file); /*
		return (jlong) ftello((FILE*) file);
	*/

	private static native int seek0(long file, long position); /*
		return fseeko((FILE*) file, (off_t) position, SEEK_SET) == 0 ? 0 : -2;
	*/

	private static native long length0(long file); /*
		FILE* f = (FILE*) file;
		off_t here = ftello(f);
		if (here < 0 || fseeko(f, 0, SEEK_END) != 0)
			return -2;
		off_t end = ftello(f);
		fseeko(f, here, SEEK_SET);
		return (jlong) end;
	*/

	private long handle() throws IOException {
		if (this.fd[0] == 0) {
			throw new ClosedChannelException();
		}
		return this.fd[0];
	}

	/** position < 0 means "at the current position, and advance it". */
	private int transfer(final ByteBuffer dst, final long position, final boolean write) throws IOException {
		synchronized (this.lock) {
			final long file = handle();
			final int pos = dst.position();
			final int len = dst.limit() - pos;
			if (len <= 0) {
				return 0;
			}
			final long here = position >= 0 ? tell0(file) : -1;
			final int n = write ? writeAt0(file, dst, pos, len, position) : readAt0(file, dst, pos, len, position);
			if (here >= 0) {
				seek0(file, here); // a positional transfer leaves the channel position alone
			}
			if (n == -2) {
				throw new IOException(write ? "write failed" : "read failed");
			}
			if (n > 0) {
				dst.position(pos + n);
			}
			return n;
		}
	}

	@Override
	public int read(final ByteBuffer dst) throws IOException {
		return transfer(dst, -1, false);
	}

	public int read(final ByteBuffer dst, final long position) throws IOException {
		if (position < 0) {
			throw new IllegalArgumentException("Negative position");
		}
		return transfer(dst, position, false);
	}

	@Override
	public int write(final ByteBuffer src) throws IOException {
		if (!this.writable) {
			throw new NonWritableChannelException();
		}
		return transfer(src, -1, true);
	}

	public int write(final ByteBuffer src, final long position) throws IOException {
		if (!this.writable) {
			throw new NonWritableChannelException();
		}
		if (position < 0) {
			throw new IllegalArgumentException("Negative position");
		}
		return transfer(src, position, true);
	}

	@Override
	public long position() throws IOException {
		synchronized (this.lock) {
			return tell0(handle());
		}
	}

	@Override
	public FileChannel position(final long newPosition) throws IOException {
		if (newPosition < 0) {
			throw new IllegalArgumentException("Negative position");
		}
		synchronized (this.lock) {
			if (seek0(handle(), newPosition) != 0) {
				throw new IOException("seek failed");
			}
		}
		return this;
	}

	@Override
	public long size() throws IOException {
		synchronized (this.lock) {
			final long size = length0(handle());
			if (size < 0) {
				throw new IOException("size failed");
			}
			return size;
		}
	}

	@Override
	public FileChannel truncate(final long size) throws IOException {
		if (size < 0) {
			throw new IllegalArgumentException("Negative size");
		}
		if (!this.writable) {
			throw new NonWritableChannelException();
		}
		synchronized (this.lock) {
			final long file = handle();
			final long here = tell0(file);
			if ((size < length0(file)) && (truncate0(file, size) != 0)) {
				throw new IOException("truncate failed");
			}
			if (here > size) {
				seek0(file, size);
			}
		}
		return this;
	}

	/** Writes buffered data to the card (fflush + fsync). */
	public void force(final boolean metaData) throws IOException {
		synchronized (this.lock) {
			if (flush0(handle()) != 0) {
				throw new IOException("force failed");
			}
		}
	}

	@Override
	public boolean isOpen() {
		return this.fd[0] != 0;
	}

	/** Closing the channel closes the file, as in the JDK. */
	@Override
	public void close() throws IOException {
		if (this.lock instanceof java.io.Closeable) {
			((java.io.Closeable) this.lock).close();
		}
	}

	public MappedByteBuffer map(final FileChannel.MapMode mode, final long position, final long size)
			throws IOException {
		throw new UnsupportedOperationException("FileChannel.map");
	}

	public static class MapMode {

		public static final MapMode READ_ONLY = new MapMode("READ_ONLY");

		public static final MapMode READ_WRITE = new MapMode("READ_WRITE");

		public static final MapMode PRIVATE = new MapMode("PRIVATE");

		private final String name;

		private MapMode(final String name) {
			this.name = name;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}
}
