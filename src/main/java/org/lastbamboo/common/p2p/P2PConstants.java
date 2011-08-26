package org.lastbamboo.common.p2p;

/**
 * P2P constants.
 */
public class P2PConstants {

    private P2PConstants() {}
    
    public static String MESSAGE_TYPE = "T";
    
    public static final String SDP = "S";

    public static final int INVITE       = 1;
    public static final int INVITE_OK    = 4;
    public static final int INVITE_ERROR = 16;
    public static final int CERT_REQUEST = 64;
    public static final int CERT_RESPONSE = 65;
    
    public static final String CERT = "CE";
    
    public static final String MAC = "MA";
    
    public static final String SECRET_KEY = "SK";

    public static final String CONTROL = "C";

    public static final String MAPPED_PORT = "MP";

    public static final String PUBLIC_IP = "PI";

    public static final String TRANSACTION_ID = "TID";
    
}
