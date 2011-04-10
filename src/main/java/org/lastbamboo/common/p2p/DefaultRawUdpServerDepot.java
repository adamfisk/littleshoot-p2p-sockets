package org.lastbamboo.common.p2p;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.littleshoot.util.CommonUtils;
import org.littleshoot.util.SessionSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class accepts incoming, typically relayed raw UDP data connections.
 */
public class DefaultRawUdpServerDepot implements SessionSocketListener, 
    RawUdpServerDepot {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private final Map<String, CallState> sessions = 
        new ConcurrentHashMap<String, CallState>();
    
    public DefaultRawUdpServerDepot() {
        startPurging();
        startServerThreaded();
    }
    
    private void startServerThreaded() {
        final Runnable runner = new Runnable() {
            public void run() {
                //startServer();
            }
        };
        final Thread t = new Thread(runner, "Incoming-Raw-UDP-Server-Thread");
        t.setDaemon(true);
        t.start();
    }

    private void startPurging() {
        final Timer t = new Timer();
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                final long now = System.currentTimeMillis();
                synchronized (sessions) {
                    for (final Entry<String, CallState> e : sessions.entrySet()) {
                        final CallState ts = e.getValue();
                        if (now - ts.getTime() < 30 * 1000) {
                            // Ignore new sockets.
                            log.info("Not purging new socket");
                            return;
                        }
                        final Socket sock = ts.getSocket();
                        if (!sock.isConnected()) {
                            log.info("Removing unconnected socket!");
                            IOUtils.closeQuietly(sock);
                            sessions.remove(e.getKey());
                        }
                        
                    }
                }
            }
        };
        t.schedule(task, 30 * 1000, 30 * 1000);
    }

    public void onSocket(final String id, final Socket sock) throws IOException{
        addSocket(id, sock);
    }

    public void addSocket(final String id, final Socket sock) {
        log.info("Adding socket with ID: "+id+"to: "+hashCode());
        sessions.put(id, new TimestampedSocket(sock));
    }
    
    public Socket getSocket(final String id) {
        final CallState ts = sessions.get(id);
        if (ts == null) return null;
        return ts.getSocket();
    }

    public Collection<String> getIds() {
        synchronized (sessions) {
            return sessions.keySet();
        }
    }

    public void addError(final String id, final String msg) {
        log.info("Adding error");
        sessions.put(id, new CallError(msg));
    }
    
    public void write(final String id, final InputStream is) 
        throws IOException {
        final Socket sock = getSocket(id);
        if (sock != null) {
            final OutputStream os = sock.getOutputStream();
            CommonUtils.threadedCopy(is, os, "Call-Write-Thread");
        }
    }
    
    public void read(final String id, final OutputStream outputStream) 
        throws IOException {
        log.info("Reading data for sessions we have: {}", sessions.containsKey(id));
        final Socket sock = getSocket(id);
        if (sock == null) {
            log.warn("Call "+id+" not found from "+hashCode()+" in {}", sessions);
            return;
        }
        log.info("Got socket");
        final InputStream is = sock.getInputStream();
        CommonUtils.threadedCopy(is, outputStream, "Call-Read-Thread");
    }

    public JSONObject toJson() {
        final JSONObject json = new JSONObject();
        final JSONArray calls = new JSONArray();
        for (final Entry<String, CallState> entry : this.sessions.entrySet()) {
            final JSONObject jsonId = new JSONObject();
            final String id = entry.getKey();
            final CallState cs = entry.getValue();
            jsonId.put("id", id);
            jsonId.put("state", cs.getMessage());
            calls.add(jsonId);
        }
        
        json.put("calls", calls);
        return json;
    }
    
    private static final class CallError implements CallState {

        private final long time = System.currentTimeMillis();
        
        private final String msg;

        public CallError(final String msg) {
            this.msg = msg;
        }

        public long getTime() {
            return time;
        }

        public Socket getSocket() {
            return null;
        }

        public String getMessage() {
            return this.msg;
        }
    }
    
    private static final class TimestampedSocket implements CallState {

        private final long time = System.currentTimeMillis();
        private final Socket sock;

        public TimestampedSocket(final Socket sock) {
            this.sock = sock;
        }

        public long getTime() {
            return time;
        }

        public Socket getSocket() {
            return sock;
        }

        public String getMessage() {
            return "CONNECTED";
        }
    }

}
