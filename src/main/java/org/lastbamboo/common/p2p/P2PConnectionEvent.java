package org.lastbamboo.common.p2p;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Event for when there's a change in the connectivity status of a P2P 
 * connection.
 */
public class P2PConnectionEvent {

    private final String jid;
    private final boolean incoming;
    private final InetSocketAddress remoteSocketAddress;
    private final boolean connected;
    private final Socket sock;

    public P2PConnectionEvent(final String jid, 
        final Socket sock, final boolean incoming, 
        final boolean connected) {
        this.jid = jid;
        this.sock = sock;
        this.remoteSocketAddress = 
            (InetSocketAddress) sock.getRemoteSocketAddress();;
        this.incoming = incoming;
        this.connected = connected;
    }

    public String getJid() {
        return jid;
    }

    public boolean isIncoming() {
        return incoming;
    }

    public InetSocketAddress getRemoteSocketAddress() {
        return remoteSocketAddress;
    }

    public boolean isConnected() {
        return connected;
    }

    public Socket getSocket() {
        return sock;
    }

    @Override
    public String toString() {
        return "P2PConnectionEvent [jid=" + jid + ", incoming=" + incoming
                + ", remoteSocketAddress=" + remoteSocketAddress
                + ", connected=" + connected + "]";
    }

}
