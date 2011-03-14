package org.lastbamboo.common.p2p;

import java.net.Socket;

public interface CallState {

    long getTime();

    Socket getSocket();

    String getMessage();
}
