package org.lastbamboo.common.p2p;

/**
 * P2P constants.
 */
public class P2PConstants {

    private P2PConstants() {}
    
    public static String MESSAGE_TYPE = "T";
    
    public static final int INVITE = 0x0001;
    
    public static final int INVITE_OK = 0x0010;

    public static final String SDP = "S";

    public static final int INVITE_ERROR = 0x0100;
}
