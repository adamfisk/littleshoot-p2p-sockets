package org.lastbamboo.common.p2p;

import java.net.InetSocketAddress;

/**
 * Event for when there's a change in the connectivity status of a P2P 
 * connection.
 */
public class P2PConnectionEvent {

    private final String jid;
    private final boolean incoming;
    private final InetSocketAddress remoteSocketAddress;
    private final boolean connected;

    public P2PConnectionEvent(final String jid, 
        final InetSocketAddress remoteSocketAddress, final boolean incoming, 
        final boolean connected) {
        this.jid = jid;
        this.remoteSocketAddress = remoteSocketAddress;
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

}
