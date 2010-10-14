package org.lastbamboo.common.p2p;

import java.net.URI;

import org.lastbamboo.common.offer.answer.Offerer;

public interface P2PSignalingClient extends Offerer {

    /**
     * Registers a given user ID with P2P proxies so that other people can
     * connect to her.
     *
     * @param userId The identifier of the user to register.
     */
    void register(long userId);
    
    /**
     * Registers a given user ID with P2P proxies so that other people can
     * connect to her.
     *
     * @param userId The identifier of the user to register.
     */
    void register(URI sipUri);

    /**
     * Registers a given user ID with P2P proxies so that other people can
     * connect to her.
     *
     * @param userId The identifier of the user to register.
     */
    void register(String id);
    
    /**
     * Logs in to the server.
     * 
     * @param user The user name.
     * @param password The password.
     */
    void login(String user, String password);
}
