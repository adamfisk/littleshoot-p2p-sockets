package org.lastbamboo.common.p2p;

import java.io.IOException;
import java.net.URI;

import org.lastbamboo.common.offer.answer.NoAnswerException;

/**
 * General factory interface for sockets.
 */
public interface SocketFactory<T> {

    /**
     * Creates a new socket.
     * 
     * @param uri The URI to generate a socket from.
     * @return The socket.
     * @throws IOException If there's an error connecting.
     * @throws NoAnswerException If there's no response from the answerer. 
     */
    T newSocket(URI uri) throws IOException, NoAnswerException;
    
    /**
     * Creates a new "unreliable" socket that, while it uses the Socket 
     * interface, does not send packets reliably underneath.
     * 
     * @param uri The URI to generate a socket from.
     * @return The socket.
     * @throws IOException If there's an error connecting.
     * @throws NoAnswerException If there's no response from the answerer. 
     */
    T newUnreliableSocket(URI uri) throws IOException, NoAnswerException;
    

    /**
     * Creates a new socket.
     * 
     * @param uri The URI to generate a socket from.
     * @return The socket.
     * @throws IOException If there's an error connecting.
     * @throws NoAnswerException If there's no response from the answerer. 
     */
    T newRawSocket(URI uri) throws IOException, NoAnswerException;
    
    /**
     * Creates a new "unreliable" socket that, while it uses the Socket 
     * interface, does not send packets reliably underneath.
     * 
     * @param uri The URI to generate a socket from.
     * @return The socket.
     * @throws IOException If there's an error connecting.
     * @throws NoAnswerException If there's no response from the answerer. 
     */
    T newRawUnreliableSocket(URI uri) throws IOException, NoAnswerException;
}
