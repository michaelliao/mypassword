package org.puppylab.mypassword.rpc.request;

import org.puppylab.mypassword.rpc.BaseRequest;

/**
 * Request body for {@code POST /passkeys/login}. The extension forwards the
 * {@code PublicKeyCredentialRequestOptions} it received from Chrome's
 * {@code onGetRequest} together with the login item the user picked.
 */
public class PasskeyLoginRequest extends BaseRequest {

    public long       itemId;
    public String     origin;
    public GetOptions options;

    public static class GetOptions {
        public String            challenge;        // base64url
        public String            rpId;             // e.g. "github.com"
        public long              timeout;
        public String            userVerification; // "required" | "preferred" | "discouraged"
        public AllowCredential[] allowCredentials; // may be empty for discoverable creds
    }

    public static class AllowCredential {
        public String   id;          // base64url credentialId
        public String   type;        // "public-key"
        public String[] transports;
    }
}
