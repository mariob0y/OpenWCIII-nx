package java.nio.channels;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

/** warsmash-nx: missing from Clearwing 3.1.2; see SocketChannel (open() fails). */
public abstract class DatagramChannel extends AbstractSelectableChannel implements ByteChannel {
	protected DatagramChannel(final SelectorProvider provider) {
		super(provider);
	}

	public static DatagramChannel open() throws IOException {
		throw new IOException("networking is not available on this platform");
	}

	public static DatagramChannel open(final ProtocolFamily family) throws IOException {
		throw new IOException("networking is not available on this platform");
	}

	@Override
	public final int validOps() {
		return SelectionKey.OP_READ | SelectionKey.OP_WRITE;
	}

	public abstract DatagramChannel bind(SocketAddress local) throws IOException;

	public abstract DatagramChannel connect(SocketAddress remote) throws IOException;

	public abstract DatagramChannel disconnect() throws IOException;

	public abstract boolean isConnected();

	public abstract SocketAddress receive(ByteBuffer dst) throws IOException;

	public abstract int send(ByteBuffer src, SocketAddress target) throws IOException;

	public abstract SocketAddress getRemoteAddress() throws IOException;

	public abstract SocketAddress getLocalAddress() throws IOException;

	@Override
	public abstract int read(ByteBuffer dst) throws IOException;

	@Override
	public abstract int write(ByteBuffer src) throws IOException;
}
