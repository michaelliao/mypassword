package org.puppylab.mypassword.core.data;

public class PasskeyData {

    public String relyingPartyId;   // e.g. "github.com"
    public String relyingPartyName; // e.g. "GitHub"

    public String b64UserId;   // base64 of the user handle
    public String username;    // WebAuthn user.name
    public String displayName; // WebAuthn user.displayName

    public int alg; // -7 = ES256

    public String b64CredentialId; // base64url — key for lookup during sign-in
    public String b64PubKey;       // base64url of raw uncompressed P-256 point (0x04 ‖ X ‖ Y)
    public String b64PrivKey;      // base64url of PKCS#8-encoded EC private key
                                   // (the enclosing LoginItemData is already DEK-encrypted at rest)

    public long createdAt;

}
