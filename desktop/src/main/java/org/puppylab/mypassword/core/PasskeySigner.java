package org.puppylab.mypassword.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

import org.puppylab.mypassword.core.data.LoginItemData;
import org.puppylab.mypassword.core.data.PasskeyData;
import org.puppylab.mypassword.core.exception.EncryptException;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.VaultException;
import org.puppylab.mypassword.rpc.request.PasskeyLoginRequest;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.StringUtils;

/**
 * Signs a WebAuthn assertion using a stored passkey's private key. Mirrors
 * {@link PasskeyBuilder} but for the {@code navigator.credentials.get()} side
 * of the ceremony.
 *
 * <p>
 * The flow:
 * <ol>
 *   <li>Validate the request origin against the RP id.</li>
 *   <li>If the RP passed a non-empty {@code allowCredentials} list, verify
 *       that the stored passkey's credential id is in it — otherwise WebAuthn
 *       requires us to fail.</li>
 *   <li>AES-GCM decrypt the PKCS#8 private key bytes with the vault DEK.</li>
 *   <li>Build a {@code webauthn.get} {@code clientDataJSON}.</li>
 *   <li>Build an {@code authenticatorData} trailer: {@code rpIdHash(32) ‖
 *       flags(UP|UV) ‖ signCount(0)} — no attested-credential block.</li>
 *   <li>Sign {@code authenticatorData ‖ SHA-256(clientDataJSON)} with ECDSA
 *       P-256/SHA-256. The resulting signature is an ASN.1 DER sequence, which
 *       is what WebAuthn expects for ES256.</li>
 * </ol>
 */
public class PasskeySigner {

    // authenticatorData flags for an assertion (no attested credential block).
    private static final int FLAG_UP = 0x01; // User Present
    private static final int FLAG_UV = 0x04; // User Verified

    public static class Result {
        public byte[] credentialId;
        public byte[] clientDataJson;
        public byte[] authenticatorData;
        public byte[] signatureDer;
        public byte[] userHandle; // raw (decoded) user handle bytes; may be null
    }

    /**
     * Produce a WebAuthn assertion for {@code login}'s stored passkey.
     *
     * @param login the login item the user picked (must have a non-null passkey)
     * @param req   the request proxied from the browser
     */
    public static Result sign(LoginItemData login, PasskeyLoginRequest req) {
        if (login == null || login.data == null || login.data.passkey == null) {
            throw new VaultException(ErrorCode.DATA_NOT_FOUND, "Login item has no passkey.");
        }
        if (req.options == null) {
            throw new VaultException(ErrorCode.BAD_REQUEST, "Missing passkey options.");
        }
        String rpId = req.options.rpId;
        StringUtils.checkNotEmpty("rpId", rpId);
        StringUtils.checkNotEmpty("origin", req.origin);
        PasskeyBuilder.validateOrigin(req.origin, rpId);

        PasskeyData pk = login.data.passkey;
        if (!rpId.equals(pk.relyingPartyId)) {
            throw new VaultException(ErrorCode.BAD_REQUEST,
                    "RP id mismatch: stored='" + pk.relyingPartyId + "' requested='" + rpId + "'");
        }

        byte[] credentialId = Base64Utils.b64(pk.b64CredentialId);
        // allowCredentials: if non-empty, our credential id must appear in it.
        var allow = req.options.allowCredentials;
        if (allow != null && allow.length > 0) {
            boolean found = false;
            for (var c : allow) {
                if (c == null || c.id == null) {
                    continue;
                }
                byte[] allowed = Base64Utils.b64(c.id);
                if (Arrays.equals(allowed, credentialId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new VaultException(ErrorCode.DATA_NOT_FOUND,
                        "Stored passkey not listed in allowCredentials.");
            }
        }

        try {
            // The enclosing LoginItemData is DEK-encrypted at rest, so b64PrivKey
            // stores the PKCS#8 bytes directly (no inner AES-GCM wrap).
            byte[] pkcs8 = Base64Utils.b64(pk.b64PrivKey);

            KeyFactory kf = KeyFactory.getInstance("EC");
            PrivateKey privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));

            // clientDataJSON + authenticatorData + signature.
            byte[] clientDataJson = PasskeyBuilder.buildClientDataJson(
                    "webauthn.get", req.options.challenge, req.origin);
            byte[] authData = buildAssertionAuthenticatorData(rpId);

            // ES256 signs authData ‖ SHA-256(clientDataJSON).
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] clientDataHash = sha256.digest(clientDataJson);
            byte[] toSign = new byte[authData.length + clientDataHash.length];
            System.arraycopy(authData, 0, toSign, 0, authData.length);
            System.arraycopy(clientDataHash, 0, toSign, authData.length, clientDataHash.length);

            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initSign(privateKey);
            sig.update(toSign);
            byte[] signatureDer = sig.sign();

            Result r = new Result();
            r.credentialId = credentialId;
            r.clientDataJson = clientDataJson;
            r.authenticatorData = authData;
            r.signatureDer = signatureDer;
            // userHandle is the raw bytes that b64UserId decodes to. WebAuthn
            // requires returning it for discoverable-credential flows; harmless
            // to include always.
            if (pk.b64UserId != null && !pk.b64UserId.isEmpty()) {
                r.userHandle = Base64Utils.b64(pk.b64UserId);
            }
            return r;
        } catch (GeneralSecurityException e) {
            throw new EncryptException(e);
        }
    }

    /**
     * authenticatorData for an assertion:
     *
     * <pre>
     *   rpIdHash  (32)
     *   flags      (1)   UP | UV
     *   signCount  (4)   always 0
     * </pre>
     *
     * No AAGUID / credential id / public key — those belong only to the
     * registration (AT-flag) form.
     */
    private static byte[] buildAssertionAuthenticatorData(String rpId) {
        try {
            byte[] rpIdHash = MessageDigest.getInstance("SHA-256")
                    .digest(rpId.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(rpIdHash, 0, 32);
            out.write(FLAG_UP | FLAG_UV);
            // signCount = 0
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(0);
            return out.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new EncryptException(e);
        }
    }
}
