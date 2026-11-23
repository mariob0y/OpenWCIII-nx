package java.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * warsmash-nx: missing from Clearwing 3.1.2. Networking is not part of the console port (single
 * player); Warsmash's lobby code compiles against this and open() fails with an IOException.
 */
public abstract class SocketChannel extends AbstractSelectableChannel implements ByteChannel {
	protected SocketChannel(final SelectorProvider provider) {
		super(provider);
	}

	public static SocketChannel open() throws IOException {
		throw new IOException("networking is not available on this platform");
	}

	public static SocketChannel open(final SocketAddress remote) throws IOException {
		throw new IOException("networking is not available on this platform");
	}

	@Override
	public final int validOps() {
		return SelectionKey.OP_READ | SelectionKey.OP_WRITE | SelectionKey.OP_CONNECT;
	}

	public abstract SocketChannel bind(SocketAddress local) throws IOException;

	public abstract boolean isConnected();

	public abstract boolean isConnectionPending();

	public abstract boolean connect(SocketAddress remote) throws IOException;

	public abstract boolean finishConnect() throws IOException;

	public abstract SocketAddress getRemoteAddress() throws IOException;

	public abstract SocketAddress getLocalAddress() throws IOException;

	public abstract SocketChannel shutdownInput() throws IOException;

	public abstract SocketChannel shutdownOutput() throws IOException;

	@Override
	public abstract int read(ByteBuffer dst) throws IOException;

	@Override
	public abstract int write(ByteBuffer src) throws IOException;
}
