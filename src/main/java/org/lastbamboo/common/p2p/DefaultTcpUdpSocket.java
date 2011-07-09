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
import org.littleshoot.mina.common.ByteBuffer;
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

    private final Logger m_log = LoggerFactory.getLogger(getClass());
    private final long m_startTime = System.currentTimeMillis();
    
    private final Object m_socketLock = new Object();
    
    /**
     * Lock for waiting for just the answer. We then create the socket using
     * the data from the answer.
     */
    private final Object m_answerLock = new Object();
    
    /**
     * Flag for whether or not we've received an answer.
     */
    private volatile boolean m_gotAnswer;
    
    private final AtomicReference<Socket> socketRef = 
        new AtomicReference<Socket>();
    private volatile boolean m_finishedWaitingForSocket = false;
    
    private final Offerer m_offerer;
    private final OfferAnswer m_offerAnswer;
    private final int m_relayWaitTime;
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
        final IceMediaStreamDesc desc)
        throws IOException {
        this(offerer, offerAnswerFactory, relayWaitTime, 30 * 1000, desc);
    }
    
    public DefaultTcpUdpSocket(final Offerer offerer,
        final OfferAnswerFactory offerAnswerFactory, final int relayWaitTime,
        final long offerTimeoutTime, final IceMediaStreamDesc desc) 
        throws IOException {
        this.m_offerer = offerer;
        this.m_relayWaitTime = relayWaitTime;
        this.offerTimeoutTime = offerTimeoutTime;
        this.desc = desc;
        try {
            this.m_offerAnswer = offerAnswerFactory.createOfferer(this, desc);
        } catch (final OfferAnswerConnectException e) {
            throw new IOExceptionWithCause("Could not create offerer", e);
        }
    }

    public Socket newSocket(final URI uri) throws IOException, 
        NoAnswerException {
        final byte[] offer = this.m_offerAnswer.generateOffer();
        processingThreadPool.submit(new Runnable() {
            public void run() {
                try {
                    m_offerer.offer(uri, offer, DefaultTcpUdpSocket.this, 
                        DefaultTcpUdpSocket.this);
                } catch (final IOException e) {
                    m_log.warn("Error sending offer", e);
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
        m_log.info("Waiting for socket -- sent offer.");
        synchronized (this.m_answerLock) {
            if (!this.m_gotAnswer) {
                m_log.info("Waiting for answer");
                try {
                    this.m_answerLock.wait(this.offerTimeoutTime);
                } catch (final InterruptedException e) {
                    m_log.error("Interrupted?", e);
                }
            }
        }
        
        if (!this.m_gotAnswer) {
            // This can happen particularly when we're using XMPP and
            // Google Talk to negotiate connections. Some just get dropped.
            final String msg = 
                "Did not get an answer from "+sipUri+" after waiting "+
                this.offerTimeoutTime + "- Could have detected failure earlier too."; 
            m_log.info(msg);
            throw new NoAnswerException(msg);
        }

        m_log.info("Got answer...");
        
        synchronized (this.m_socketLock) {
            m_log.info("Got socket lock...");
            
            // We use this flag in case we're notified of the socket before
            // we start waiting. We'd wait forever in that case without this
            // check.
            if (!m_finishedWaitingForSocket) {
                m_log.trace("Waiting for socket...");
                try {
                    // We add one to make sure we don't sleep forever.
                    m_socketLock.wait((this.m_relayWaitTime * 1000) + 1);
                } catch (final InterruptedException e) {
                    // Should never happen -- we don't use interrupts here.
                    m_log.error("Unexpectedly interrupted", e);
                }
            }

            if (this.socketRef.get() == null && this.desc.isUseRelay()) {
                // If the socket is still null, we could not create a direct
                // connection. Instead we'll have to relay the data.
                m_log.info("Could not create direct connection - using relay!");
                this.m_offerAnswer.useRelay();
                m_log.trace("Waiting for socket...");
                // We sometimes have to wait for awhile for resolution,
                // especially if we're accessing a file from around the world!!
                try {
                    m_socketLock.wait(35 * 1000);
                } catch (final InterruptedException e) {
                    // Should never happen -- we don't use interrupts here.
                    m_log.error("Unexpectedly interrupted", e);
                }
            }

        }
        
        // If the socket is still null, that means even the relay failed
        // for some reason. This should never happen, but it's of course
        // possible.
        if (this.socketRef.get() == null) {
            m_log.warn("Socket is null...");

            // This notifies IceAgentImpl that it should close all its
            // candidates.
            this.m_offerAnswer.close();
            throw new IOException("Could not connect to remote host: "
                    + sipUri);
        } else {
            m_log.trace("Returning socket!!");
            return this.socketRef.get();
        }
    }
    
    /**
     * Simply notifies the socket lock that it should stop waiting.  This
     * will happen both when we've successfully created a socket and when
     * there's been an error creating the socket.
     */
    private void notifySocketLock() {
        m_log.info("Notifying socket lock");
        synchronized (this.m_socketLock) {
            m_log.info("Got socket lock...notifying...");
            m_finishedWaitingForSocket = true;
            this.m_socketLock.notify();
        }
    }

    public void onTransactionSucceeded(final OfferAnswerMessage response) {
        m_log.trace("Received INVITE OK");

        m_log.debug("Successful transaction after {} milliseconds...",
                getElapsedTime());

        // Determine the ICE candidates for socket creation from the
        // response body.
        final ByteBuffer answer = response.getBody();

        synchronized (this.m_answerLock) {
            m_gotAnswer = true;
            this.m_answerLock.notifyAll();
        }

        // This is responsible for notifying listeners on errors.
        processingThreadPool.submit(new Runnable() {
            public void run() {
                m_offerAnswer.processAnswer(answer);
            }
        });
        //this.m_offerAnswer.processAnswer(answer);
    }

    public void onTransactionFailed(final OfferAnswerMessage response) {
        m_log.warn("Failed transaction after " + getElapsedTime()
                + " milliseconds...");

        // We know the status of the remote host, so make sure the socket
        // fails as quickly as possible.
        notifySocketLock();
        
        // Also notify the answer lock which could be waiting separately.
        synchronized (this.m_answerLock) {
            this.m_answerLock.notify();
        }

        this.m_offerAnswer.close();
    }

    public void onTcpSocket(final Socket sock) {
        m_log.info("Got a TCP socket!");
        if (processedSocket(sock)) {
            this.m_offerAnswer.closeUdp();
        } else {
            this.m_offerAnswer.closeTcp();
        }
    }

    public void onUdpSocket(final Socket sock) {
        if (processedSocket(sock)) {
            this.m_offerAnswer.closeTcp();
        } else {
            this.m_offerAnswer.closeUdp();
        }
    }

    private boolean processedSocket(final Socket sock) {
        m_log.info("Processing socket");
        synchronized (socketRef) {
            if (socketRef.get() != null) {
                m_log.info("Ignoring socket");
                IOUtils.closeQuietly(sock);
                return false;
            }
            socketRef.set(sock);
        }

        m_log.info("Notifying socket lock!!");
        notifySocketLock();
        return true;
    }

    public void onOfferAnswerFailed(final OfferAnswer offerAnswer) {
        notifySocketLock();
        this.m_offerAnswer.close();
    }
    
    /**
     * Returns the elapsed time from the start time.  This method assumes that
     * the start time was previously set.
     * 
     * @return The elapsed time from the start time.
     */
    private long getElapsedTime() {
        final long now = System.currentTimeMillis();
        final long elapsedTime = now - this.m_startTime;

        return elapsedTime;
    }

    public byte[] getWriteKey() {
        return this.writeKey;
    }

    public byte[] getReadKey() {
        return this.readKey;
    }

    public void setReadKey(final byte[] key) {
        this.readKey = key;
    }
}
