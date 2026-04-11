package org.puppylab.mypassword.rpc.response;

import java.util.Map;

public class PasskeyAddResponse {
    public String          id;                      // base64url credentialId
    public String          rawId;                   // same, base64url
    public String          type;                    // "public-key"
    public String          authenticatorAttachment; // "platform"
    public PasskeyResponse response;

    public static class PasskeyResponse {
        public String   clientDataJSON;     // base64url
        public String   authenticatorData;  // base64url (same bytes as inside attestationObject.authData)
        public String   publicKey;          // base64url of SPKI DER
        public int      publicKeyAlgorithm; // COSE alg, -7 for ES256
        public String   attestationObject;  // base64url (CBOR: {fmt:"none", authData, attStmt:{}})
        public String[] transports;         // ["internal"]
    }

    public Map<String, Object> clientExtensionResults = Map.of(); // can be empty
}
