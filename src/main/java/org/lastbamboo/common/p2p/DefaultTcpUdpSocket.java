package org.lastbamboo.common.p2p;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.commons.io.IOUtils;
import org.lastbamboo.common.offer.answer.IceMediaStreamDesc;
import org.lastbamboo.common.offer.answer.NoAnswerException;
import org.lastbamboo.common.offer.answer.OfferAnswer;
import org.lastbamboo.common.offer.answer.OfferAnswerConnectException;
import org.lastbamboo.common.offer.answer.OfferAnswerFactory;
import org.lastbamboo.common.offer.answer.OfferAnswerListener;
import org.lastbamboo.common.offer.answer.OfferAnswerMessage;
import org.lastbamboo.common.offer.answer.OfferAnswerTransactionListener;
import org.lastbamboo.common.offer.answer.Offerer;
import org.littleshoot.util.CommonUtils;
import org.littleshoot.util.KeyStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for creating sockets that can be created using either a TCP or a 
 * reliable UDP connection, depending on which successfully connects first.
 */
public class DefaultTcpUdpSocket implements TcpUdpSocket, 
    OfferAnswerTransactionListener, OfferAnswerListener, KeyStorage {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final long startTime = System.currentTimeMillis();
    
    private final Object socketLock = new Object();
    
    /**
     * Lock for waiting for just the answer. We then create the socket using
     * the data from the answer.
     */
    private final Object answerLock = new Object();
    
    /**
     * Flag for whether or not we've received an answer.
     */
    private volatile boolean gotAnswer;
    
    private final AtomicReference<Socket> socketRef = 
        new AtomicReference<Socket>();
    private volatile boolean finishedWaitingForSocket = false;
    
    private final Offerer offerer;
    private final OfferAnswer offerAnswer;
    private final int relayWaitTime;
    private final long offerTimeoutTime;
    private final byte[] writeKey = CommonUtils.generateKey();
    private byte[] readKey = null;
    
    /**
     * Thread pool for offloading tasks that can't hold up the processing 
     * threads, particularly the handled of offers and answers that can
     * block due to the sockets they open.
     */
    private static final ExecutorService processingThreadPool = 
        Executors.newCachedThreadPool();
    private final IceMediaStreamDesc desc;
    
    /**
     * Creates a new reliable TCP or UDP socket.
     * 
     * @param offerer The client connection to the P2P signaling server.
     * @param offerAnswerFactory The class for creating new offers and answers.
     * @param relayWaitTime The number of seconds to wait before using the 
     * relay.
     * @throws IOException If there's an error connecting.
     */
    public DefaultTcpUdpSocket(final Offerer offerer,
        final OfferAnswerFactory offerAnswerFactory, final int relayWaitTime,
        final IceMediaStreamDesc desc) throws IOException {
        this(offerer, offerAnswerFactory, relayWaitTime, 30 * 1000, desc);
    }
    
    public DefaultTcpUdpSocket(final Offerer offerer,
        final OfferAnswerFactory offerAnswerFactory, final int relayWaitTime,
        final long offerTimeoutTime, final IceMediaStreamDesc desc) 
        throws IOException {
        this.offerer = offerer;
        this.relayWaitTime = relayWaitTime;
        this.offerTimeoutTime = offerTimeoutTime;
        this.desc = desc;
        try {
            this.offerAnswer = offerAnswerFactory.createOfferer(this, desc);
        } catch (final OfferAnswerConnectException e) {
            throw new IOExceptionWithCause("Could not create offerer", e);
        }
    }

    @Override
    public Socket newSocket(final URI uri) throws IOException, 
        NoAnswerException {
        final byte[] offer = this.offerAnswer.generateOffer();
        processingThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    offerer.offer(uri, offer, DefaultTcpUdpSocket.this, 
                        DefaultTcpUdpSocket.this);
                } catch (final IOException e) {
                    log.warn("Error sending offer", e);
                    notifySocketLock();
                }                
            }
        });
        return waitForSocket(uri);
    }

    /**
     * Waits for the successful creation of a socket or a socket creation
     * error.
     * 
     * @param sipUri The URI we're connecting to.
     * @return The new socket.
     * @throws IOException If there's any problem creating the socket.
     * @throws NoAnswerException If there's no answer.
     */
    private Socket waitForSocket(final URI sipUri) throws IOException, 
        NoAnswerException {
        log.info("Waiting for socket -- sent offer.");
        synchronized (this.answerLock) {
            if (!this.gotAnswer) {
                log.info("Waiting for answer");
                try {
                    this.answerLock.wait(this.offerTimeoutTime);
                } catch (final InterruptedException e) {
                    log.error("Interrupted?", e);
                }
            }
        }
        
        if (!this.gotAnswer) {
            // This can happen particularly when we're using XMPP and
            // Google Talk to negotiate connections. Some just get dropped.
            final String msg = 
                "Did not get an answer from "+sipUri+" after waiting "+
                this.offerTimeoutTime + "- Could have detected failure earlier too."; 
            log.info(msg);
            throw new NoAnswerException(msg);
        }

        log.info("Got answer...");
        
        synchronized (this.socketLock) {
            log.info("Got socket lock...");
            
            // We use this flag in case we're notified of the socket before
            // we start waiting. We'd wait forever in that case without this
            // check.
            if (!finishedWaitingForSocket) {
                log.trace("Waiting for socket...");
                try {
                    // We add one to make sure we don't sleep forever.
                    socketLock.wait((this.relayWaitTime * 1000) + 1);
                } catch (final InterruptedException e) {
                    // Should never happen -- we don't use interrupts here.
                    log.error("Unexpectedly interrupted", e);
                }
            }

            if (this.socketRef.get() == null && this.desc.isUseRelay()) {
                // If the socket is still null, we could not create a direct
                // connection. Instead we'll have to relay the data.
                log.info("Could not create direct connection - using relay!");
                this.offerAnswer.useRelay();
                log.trace("Waiting for socket...");
                // We sometimes have to wait for awhile for resolution,
                // especially if we're accessing a file from around the world!!
                try {
                    socketLock.wait(35 * 1000);
                } catch (final InterruptedException e) {
                    // Should never happen -- we don't use interrupts here.
                    log.error("Unexpectedly interrupted", e);
                }
            }
        }
        
        // If the socket is still null, that means even the relay failed
        // for some reason. This should never happen, but it's of course
        // possible.
        if (this.socketRef.get() == null) {
            log.warn("Socket is null...");

            // This notifies IceAgentImpl that it should close all its
            // candidates.
            this.offerAnswer.close();
            throw new IOException("Could not connect to remote host: "
                    + sipUri);
        } else {
            log.trace("Returning socket!!");
            return this.socketRef.get();
        }
    }
    
    /**
     * Simply notifies the socket lock that it should stop waiting.  This
     * will happen both when we've successfully created a socket and when
     * there's been an error creating the socket.
     */
    private void notifySocketLock() {
        log.info("Notifying socket lock");
        synchronized (this.socketLock) {
            log.info("Got socket lock...notifying...");
            finishedWaitingForSocket = true;
            this.socketLock.notify();
        }
    }

    @Override
    public void onTransactionSucceeded(final OfferAnswerMessage response) {
        log.info("Received INVITE OK");

        log.debug("Successful transaction after {} milliseconds...",
                getElapsedTime());

        synchronized (this.answerLock) {
            gotAnswer = true;
            this.answerLock.notifyAll();
        }

        // This is responsible for notifying listeners on errors.
        processingThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                offerAnswer.processAnswer(response.getBody());
            }
        });
        //this.m_offerAnswer.processAnswer(answer);
    }

    @Override
    public void onTransactionFailed(final OfferAnswerMessage response) {
        log.warn("Failed transaction after " + getElapsedTime()
                + " milliseconds...");

        // This indicates specifically that we haven't received an answer,
        // so notifying the answer lock will generate an exception.
        synchronized (this.answerLock) {
            this.answerLock.notify();
        }
        
        this.offerAnswer.close();
    }

    @Override
    public void onTcpSocket(final Socket sock) {
        log.info("Got a TCP socket!");
        if (processedSocket(sock)) {
            this.offerAnswer.closeUdp();
        } else {
            this.offerAnswer.closeTcp();
        }
    }

    @Override
    public void onUdpSocket(final Socket sock) {
        if (processedSocket(sock)) {
            this.offerAnswer.closeTcp();
        } else {
            this.offerAnswer.closeUdp();
        }
    }

    private boolean processedSocket(final Socket sock) {
        log.info("Processing socket");
        synchronized (socketRef) {
            if (socketRef.get() != null) {
                log.info("Ignoring socket");
                IOUtils.closeQuietly(sock);
                return false;
            }
            socketRef.set(sock);
        }

        log.info("Notifying socket lock!!");
        notifySocketLock();
        return true;
    }

    @Override
    public void onOfferAnswerFailed(final OfferAnswer offerAnswer) {
        notifySocketLock();
        this.offerAnswer.close();
    }
    
    /**
     * Returns the elapsed time from the start time.  This method assumes that
     * the start time was previously set.
     * 
     * @return The elapsed time from the start time.
     */
    private long getElapsedTime() {
        final long now = System.currentTimeMillis();
        final long elapsedTime = now - this.startTime;

        return elapsedTime;
    }

    @Override
    public byte[] getWriteKey() {
        return this.writeKey;
    }

    @Override
    public byte[] getReadKey() {
        return this.readKey;
    }

    @Override
    public void setReadKey(final byte[] key) {
        this.readKey = key;
    }
}
