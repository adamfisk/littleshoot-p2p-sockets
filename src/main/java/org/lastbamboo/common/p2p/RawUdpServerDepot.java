package org.lastbamboo.common.p2p;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;

import org.json.simple.JSONObject;
import org.littleshoot.util.SessionSocketListener;

/**
 * Interface for accessing sessions/sockets in the class for keeping track
 * of such things.
 */
public interface RawUdpServerDepot extends SessionSocketListener{

    /**
     * Accessor for the {@link Socket} with the specified ID.
     * 
     * @param id The ID of the socket we're looking for.
     * @return The {@link Socket}.
     */
    Socket getSocket(String id);

    /**
     * Get all IDs of current sessions.
     * 
     * @return All IDs of current sessions.
     */
    Collection<String> getIds();

    /**
     * Adds the specified socket to the depot.
     * 
     * @param id The ID for the socket.
     * @param sock The {@link Socket} to add.
     */
    void addSocket(String id, Socket sock);

    /**
     * Adds an error.
     * 
     * @param id The ID for the session.
     * @param msg The error message.
     */
    void addError(String id, String msg);

    JSONObject toJson();

    long write(String id, InputStream is, long contentLength) throws IOException;
    
    void write(String id, byte[] data) throws IOException;

    long read(String id, OutputStream outputStream, int length) 
        throws IOException;
}
