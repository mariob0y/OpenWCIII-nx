package java.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

/** warsmash-nx: missing from Clearwing 3.1.2; see SocketChannel (open() fails). */
public abstract class ServerSocketChannel extends AbstractSelectableChannel {
	protected ServerSocketChannel(final SelectorProvider provider) {
		super(provider);
	}

	public static ServerSocketChannel open() throws IOException {
		throw new IOException("networking is not available on this platform");
	}

	@Override
	public final int validOps() {
		return SelectionKey.OP_ACCEPT;
	}

	public final ServerSocketChannel bind(final SocketAddress local) throws IOException {
		return bind(local, 0);
	}

	public abstract ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException;

	public abstract SocketChannel accept() throws IOException;

	public abstract SocketAddress getLocalAddress() throws IOException;
}
