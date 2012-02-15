package org.lastbamboo.common.p2p;

/**
 * Listener for p2p connection events.
 */
public interface P2PConnectionListener {

    /**
     * Called when there's been a connectivity change, with details specified
     * in the provided event object.
     * 
     * @param event The event.
     */
    void onConnectivityEvent(P2PConnectionEvent event);
}
