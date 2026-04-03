package org.puppylab.mypassword.core.data;

public class PasskeyData {

    public long id; // passkey id for db primary key in usage.

    public String relyingPartyId;   // e.g. "google.com"
    public String relyingPartyName; // e.g. "Google"
    public String b64UserId;        // base64-encoded user id
    public String username;         // user name
    public String displayName;      // user display name

    public int alg; // -7 = (ECDSA w/SHA-256)

    public String b64PubKey;
    public String b64PrivKey;

    public long createdAt;

}
