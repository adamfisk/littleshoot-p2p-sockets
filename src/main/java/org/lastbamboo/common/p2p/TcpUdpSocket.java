package org.lastbamboo.common.p2p;

import java.io.IOException;
import java.net.Socket;
import java.net.URI;

public interface TcpUdpSocket
    {

    Socket newSocket(URI sipUri) throws IOException;
    }
