package org.puppylab.mypassword.core.data;

public class TotpData {

    public String secret;    // base32-encoded
    public String issuer;
    public String algorithm; // SHA1, SHA256, SHA512
    public int    digits;    // 6 or 8
    public int    period;    // seconds, usually 30

}
