package java.io;

import java.nio.channels.FileChannel;

/**
 * Replaces Clearwing's stub (its constructor only throws). Our classes win over the runtime's
 * because the transpiler reads them after its own. Backed by a C FILE*, i.e. newlib, so on the
 * Switch every open goes through the guard's wrapped fsFsOpenFile.
 *
 * Covers what Warsmash's archive readers need: random reads, seek, length, and a FileChannel
 * that shares the same handle (as the JDK's does). Not thread-safe across the two beyond each
 * call being synchronized on this object.
 */
public class RandomAccessFile implements Closeable {
	/*JNI
	#include <cstdio>
	#include <cerrno>
	*/

	/** The FILE*, in a one-element array shared with the channel so closing either closes both. */
	private final long[] fd = new long[1];
	private final boolean writable;
	private FileChannel channel;

	public RandomAccessFile(final File file, final String mode) throws FileNotFoundException {
		this(file.getPath(), mode);
	}

	public RandomAccessFile(final String name, final String mode) throws FileNotFoundException {
		if ("r".equals(mode)) {
			this.writable = false;
		}
		else if ("rw".equals(mode) || "rws".equals(mode) || "rwd".equals(mode)) {
			this.writable = true;
		}
		else {
			throw new IllegalArgumentException("Illegal mode \"" + mode + "\"");
		}
		this.fd[0] = open0(name, this.writable);
		if (this.fd[0] == 0) {
			throw new FileNotFoundException(name);
		}
	}

	/** "r" opens read-only; "rw" opens for update and creates the file if missing, never truncating. */
	private static native long open0(String name, boolean writable); /*
		FILE* f = fopen(name, writable ? "r+b" : "rb");
		if (!f && writable)
			f = fopen(name, "w+b");
		return (jlong) f;
	*/

	private static native int read0(long file, byte[] bytes, int off, int len); /*
		FILE* f = (FILE*) file;
		size_t n = fread(bytes + off, 1, (size_t) len, f);
		if (n == 0)
			return ferror(f) ? -2 : -1;
		return (jint) n;
	*/

	private static native int write0(long file, byte[] bytes, int off, int len); /*
		FILE* f = (FILE*) file;
		size_t n = fwrite(bytes + off, 1, (size_t) len, f);
		return n < (size_t) len ? -2 : (jint) n;
	*/

	private static native int seek0(long file, long position); /*
		return fseeko((FILE*) file, (off_t) position, SEEK_SET) == 0 ? 0 : -2;
	*/

	private static native long tell0(long file); /*
		return (jlong) ftello((FILE*) file);
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

	private static native int close0(long file); /*
		return fclose((FILE*) file) == 0 ? 0 : -2;
	*/

	private long handle() throws IOException {
		if (this.fd[0] == 0) {
			throw new IOException("Stream Closed");
		}
		return this.fd[0];
	}

	public final synchronized FileChannel getChannel() {
		if (this.channel == null) {
			this.channel = FileChannel.shareHandle(this, this.fd, this.writable);
		}
		return this.channel;
	}

	public synchronized int read() throws IOException {
		final byte[] one = new byte[1];
		return read(one, 0, 1) == 1 ? one[0] & 0xFF : -1;
	}

	public synchronized int read(final byte[] b, final int off, final int len) throws IOException {
		if ((off < 0) || (len < 0) || (len > (b.length - off))) {
			throw new IndexOutOfBoundsException();
		}
		if (len == 0) {
			return 0;
		}
		final int n = read0(handle(), b, off, len);
		if (n == -2) {
			throw new IOException("read failed");
		}
		return n;
	}

	public int read(final byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	public final void readFully(final byte[] b) throws IOException {
		readFully(b, 0, b.length);
	}

	public final synchronized void readFully(final byte[] b, final int off, final int len) throws IOException {
		int n = 0;
		while (n < len) {
			final int count = read(b, off + n, len - n);
			if (count < 0) {
				throw new EOFException();
			}
			n += count;
		}
	}

	public final int readInt() throws IOException {
		final byte[] b = new byte[4];
		readFully(b);
		return ((b[0] & 0xFF) << 24) | ((b[1] & 0xFF) << 16) | ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
	}

	public final short readShort() throws IOException {
		final byte[] b = new byte[2];
		readFully(b);
		return (short) (((b[0] & 0xFF) << 8) | (b[1] & 0xFF));
	}

	public final long readLong() throws IOException {
		return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
	}

	public final byte readByte() throws IOException {
		final int v = read();
		if (v < 0) {
			throw new EOFException();
		}
		return (byte) v;
	}

	public int skipBytes(final int n) throws IOException {
		if (n <= 0) {
			return 0;
		}
		final long pos = getFilePointer();
		final long target = Math.min(length(), pos + n);
		seek(target);
		return (int) (target - pos);
	}

	public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
		if (!this.writable) {
			throw new IOException("File opened read-only");
		}
		if ((off < 0) || (len < 0) || (len > (b.length - off))) {
			throw new IndexOutOfBoundsException();
		}
		if ((len > 0) && (write0(handle(), b, off, len) < 0)) {
			throw new IOException("write failed");
		}
	}

	public void write(final byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	public void write(final int b) throws IOException {
		write(new byte[] { (byte) b }, 0, 1);
	}

	public synchronized void seek(final long pos) throws IOException {
		if (pos < 0) {
			throw new IOException("Negative seek offset");
		}
		if (seek0(handle(), pos) != 0) {
			throw new IOException("seek failed");
		}
	}

	public synchronized long getFilePointer() throws IOException {
		return tell0(handle());
	}

	public synchronized long length() throws IOException {
		final long length = length0(handle());
		if (length < 0) {
			throw new IOException("length failed");
		}
		return length;
	}

	@Override
	public synchronized void close() throws IOException {
		if (this.fd[0] == 0) {
			return;
		}
		final long f = this.fd[0];
		this.fd[0] = 0;
		if (close0(f) != 0) {
			throw new IOException("close failed");
		}
	}
}
