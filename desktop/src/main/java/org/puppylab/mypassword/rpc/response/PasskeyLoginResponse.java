package org.puppylab.mypassword.rpc.response;

import java.util.Map;

/**
 * WebAuthn {@code AuthenticationResponseJSON} returned to the extension. The
 * extension hands this straight to
 * {@code chrome.webAuthenticationProxy.completeGetRequest} which resolves the
 * page's {@code navigator.credentials.get()} promise.
 */
public class PasskeyLoginResponse {

    public String            id;                      // base64url credentialId
    public String            rawId;                   // same, base64url
    public String            type;                    // "public-key"
    public String            authenticatorAttachment; // "platform"
    public AssertionResponse response;

    public static class AssertionResponse {
        public String clientDataJSON;    // base64url
        public String authenticatorData; // base64url
        public String signature;         // base64url (ASN.1 DER ECDSA sig)
        public String userHandle;        // base64url; may be null
    }

    public Map<String, Object> clientExtensionResults = Map.of();
}
