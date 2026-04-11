package org.puppylab.mypassword.rpc.request;

import org.puppylab.mypassword.rpc.BaseRequest;

public class AddPasskeyRequest extends BaseRequest {

    public long           itemId;
    public String         origin;
    public PasskeyOptions options;

    public static class PasskeyOptions {
        public String                        attestation;
        public String                        challenge;
        public long                          timeout;
        public PasskeyAuthenticatorSelection authenticatorSelection;
        public PasskeyPubKeyCredParam[]      pubKeyCredParams;
        public PasskeyExcludeCredential[]    excludeCredentials;
        public PasskeyRp                     rp;
        public PasskeyUser                   user;
    }

    public static class PasskeyPubKeyCredParam {
        public int    alg;
        public String type;
    }

    public static class PasskeyExcludeCredential {
        public String   id;
        public String   type;
        public String[] transports;
    }

    public static class PasskeyRp {
        public String id;
        public String name;
    }

    public static class PasskeyUser {
        public String id;
        public String displayName;
        public String name;
    }

    public static class PasskeyAuthenticatorSelection {
        public String residentKey;             // "required" | "preferred" | "discouraged"
        public String userVerification;        // "required" | "preferred" | "discouraged"
        public String authenticatorAttachment; // "platform" | "cross-platform"
    }
}
