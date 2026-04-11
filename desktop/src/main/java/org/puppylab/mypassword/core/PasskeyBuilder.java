package org.puppylab.mypassword.core;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;

import javax.crypto.SecretKey;

import org.puppylab.mypassword.core.data.PasskeyData;
import org.puppylab.mypassword.core.exception.EncryptException;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.rpc.VaultException;
import org.puppylab.mypassword.rpc.request.AddPasskeyRequest;
import org.puppylab.mypassword.util.Base64Utils;
import org.puppylab.mypassword.util.CborWriter;
import org.puppylab.mypassword.util.EncryptUtils;
import org.puppylab.mypassword.util.StringUtils;

/**
 * Builds a WebAuthn public-key credential (ES256, packed P-256) in response to
 * a passkey registration ceremony proxied from the browser extension.
 *
 * <p>
 * The flow is:
 * <ol>
 * <li>Validate the origin against the relying-party id.</li>
 * <li>Confirm the RP requested ES256 (COSE alg {@code -7}).</li>
 * <li>Generate a fresh EC P-256 keypair.</li>
 * <li>Encrypt the PKCS#8 private key with the vault DEK (AES-GCM).</li>
 * <li>Assemble {@code authenticatorData} (rpIdHash + flags + signCount + AAGUID
 * + credId + COSE public key).</li>
 * <li>Assemble {@code clientDataJSON} and {@code attestationObject}
 * ({@code fmt = "none"}).</li>
 * </ol>
 *
 * <p>
 * The result contains the {@link PasskeyData} to persist on the login item and
 * the bytes the extension must return to the browser.
 */
public class PasskeyBuilder {

    // COSE algorithm identifier for ECDSA with SHA-256 over P-256.
    public static final int COSE_ALG_ES256 = -7;

    // AAGUID for MyPassword software authenticator. All zeros is valid for
    // fmt=none and is what spec-pure "no attestation" providers use.
    private static final byte[] AAGUID = new byte[16];

    // authenticatorData flag bits.
    private static final int FLAG_UP = 0x01; // User Present
    private static final int FLAG_UV = 0x04; // User Verified
    private static final int FLAG_AT = 0x40; // Attested credential data included

    public static class Result {
        public PasskeyData data;
        public byte[]      credentialId;
        public byte[]      clientDataJson;
        public byte[]      authenticatorData;
        public byte[]      attestationObject;
        /** X.509 SubjectPublicKeyInfo (DER) encoding of the EC public key. */
        public byte[]      publicKeySpki;
        /** COSE algorithm identifier, e.g. -7 for ES256. */
        public int         publicKeyAlgorithm;
    }

    /**
     * Build a new passkey for the given request using the provided vault DEK to
     * wrap the private key.
     */
    public static Result build(SecretKey dek, AddPasskeyRequest req) {
        if (req.options == null || req.options.rp == null || req.options.user == null) {
            throw new VaultException(ErrorCode.BAD_REQUEST, "Missing passkey options.");
        }
        String rpId = req.options.rp.id;
        StringUtils.checkNotEmpty("rpId", rpId);
        StringUtils.checkNotEmpty("origin", req.origin);
        validateOrigin(req.origin, rpId);
        requireEs256(req);

        try {
            // Generate EC P-256 keypair.
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            var kp = kpg.generateKeyPair();

            ECPublicKey ecPub = (ECPublicKey) kp.getPublic();
            ECPoint w = ecPub.getW();
            byte[] x = toFixed32(w.getAffineX());
            byte[] y = toFixed32(w.getAffineY());
            byte[] rawPub = new byte[65];
            rawPub[0] = 0x04;
            System.arraycopy(x, 0, rawPub, 1, 32);
            System.arraycopy(y, 0, rawPub, 33, 32);

            // Wrap the PKCS#8 private key bytes under the DEK.
            byte[] pkcs8 = kp.getPrivate().getEncoded();
            byte[] iv = EncryptUtils.generateIV();
            byte[] ciphertext = EncryptUtils.encrypt(pkcs8, dek, iv);
            byte[] ivPlusCt = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, ivPlusCt, 0, iv.length);
            System.arraycopy(ciphertext, 0, ivPlusCt, iv.length, ciphertext.length);

            // Fresh 16-byte credential ID.
            byte[] credentialId = EncryptUtils.generateSecureRandomBytes(16);

            // COSE public key and authenticator data.
            byte[] cosePubKey = buildCoseEc2PublicKey(x, y);
            byte[] authData = buildAuthenticatorData(rpId, credentialId, cosePubKey);

            // clientDataJSON. Field order must not matter for fmt=none but we
            // follow the order the spec's algorithm produces for JSON-compatible
            // serialization.
            byte[] clientDataJson = buildClientDataJson(req.options.challenge, req.origin);

            // attestationObject CBOR: { fmt: "none", attStmt: {}, authData: <bytes> }.
            CborWriter att = new CborWriter();
            att.writeMapHeader(3);
            att.writeText("fmt").writeText("none");
            att.writeText("attStmt").writeMapHeader(0);
            att.writeText("authData").writeBytes(authData);
            byte[] attestationObject = att.toByteArray();

            // Pack PasskeyData for persistence.
            PasskeyData pd = new PasskeyData();
            pd.relyingPartyId = rpId;
            pd.relyingPartyName = req.options.rp.name;
            pd.b64UserId = req.options.user.id; // already base64url from the browser
            pd.username = req.options.user.name;
            pd.displayName = req.options.user.displayName;
            pd.alg = COSE_ALG_ES256;
            pd.b64CredentialId = Base64Utils.b64(credentialId);
            pd.b64PubKey = Base64Utils.b64(rawPub);
            pd.b64EncryptedPrivKey = Base64Utils.b64(ivPlusCt);
            pd.createdAt = System.currentTimeMillis();

            Result r = new Result();
            r.data = pd;
            r.credentialId = credentialId;
            r.clientDataJson = clientDataJson;
            r.authenticatorData = authData;
            r.attestationObject = attestationObject;
            // Java's EC PublicKey.getEncoded() returns X.509 SubjectPublicKeyInfo
            // (DER), which is exactly the "publicKey" format Chrome expects in
            // RegistrationResponseJSON.
            r.publicKeySpki = ecPub.getEncoded();
            r.publicKeyAlgorithm = COSE_ALG_ES256;
            return r;
        } catch (GeneralSecurityException e) {
            throw new EncryptException(e);
        }
    }

    /** The origin's host must equal rpId or be a sub-domain of rpId. */
    private static void validateOrigin(String origin, String rpId) {
        String host;
        try {
            host = URI.create(origin).getHost();
        } catch (IllegalArgumentException e) {
            throw new VaultException(ErrorCode.BAD_REQUEST, "Invalid origin: " + origin);
        }
        if (host == null) {
            throw new VaultException(ErrorCode.BAD_REQUEST, "Invalid origin: " + origin);
        }
        if (!host.equals(rpId) && !host.endsWith("." + rpId)) {
            throw new VaultException(ErrorCode.BAD_REQUEST,
                    "Origin host '" + host + "' does not match rp.id '" + rpId + "'.");
        }
    }

    /** The RP must allow ES256; we do not currently support any other alg. */
    private static void requireEs256(AddPasskeyRequest req) {
        var params = req.options.pubKeyCredParams;
        if (params == null || params.length == 0) {
            throw new VaultException(ErrorCode.BAD_REQUEST, "No pubKeyCredParams.");
        }
        for (var p : params) {
            if (p.alg == COSE_ALG_ES256) {
                return;
            }
        }
        throw new VaultException(ErrorCode.BAD_REQUEST, "RP does not accept ES256.");
    }

    /**
     * Convert a BigInteger to a fixed 32-byte big-endian representation (left
     * zero-padded, high sign byte stripped).
     */
    private static byte[] toFixed32(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length == 32) {
            return b;
        }
        byte[] out = new byte[32];
        if (b.length > 32) {
            // toByteArray may prefix a 0x00 sign byte when the top bit is set.
            System.arraycopy(b, b.length - 32, out, 0, 32);
        } else {
            System.arraycopy(b, 0, out, 32 - b.length, b.length);
        }
        return out;
    }

    /**
     * COSE_Key for EC2 / P-256:
     *
     * <pre>
     *   { 1: 2, 3: -7, -1: 1, -2: x, -3: y }
     *     kty    alg    crv   x    y
     * </pre>
     */
    private static byte[] buildCoseEc2PublicKey(byte[] x, byte[] y) {
        CborWriter w = new CborWriter();
        w.writeMapHeader(5);
        w.writeInt(1).writeInt(2); // kty = EC2
        w.writeInt(3).writeInt(-7); // alg = ES256
        w.writeInt(-1).writeInt(1); // crv = P-256
        w.writeInt(-2).writeBytes(x); // x
        w.writeInt(-3).writeBytes(y); // y
        return w.toByteArray();
    }

    /**
     * authenticatorData layout:
     *
     * <pre>
     *   rpIdHash (32)
     *   flags     (1)   UP | UV | AT
     *   signCount (4)   always 0
     *   AAGUID   (16)
     *   credIdLen (2)   big-endian
     *   credId    (N)
     *   credPubKey (COSE_Key)
     * </pre>
     */
    private static byte[] buildAuthenticatorData(String rpId, byte[] credentialId, byte[] cosePubKey) {
        try {
            byte[] rpIdHash = MessageDigest.getInstance("SHA-256").digest(rpId.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(rpIdHash, 0, 32);
            out.write(FLAG_UP | FLAG_UV | FLAG_AT);
            // signCount = 0
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(AAGUID, 0, 16);
            int len = credentialId.length;
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
            out.write(credentialId, 0, credentialId.length);
            out.write(cosePubKey, 0, cosePubKey.length);
            return out.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new EncryptException(e);
        }
    }

    /**
     * clientDataJSON for a {@code webauthn.create} ceremony.
     *
     * <p>
     * The challenge from the RP is already base64url-encoded in the request; we
     * echo it back verbatim. {@code origin} is taken from the tab that initiated
     * the ceremony.
     */
    private static byte[] buildClientDataJson(String challengeB64, String origin) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"type\":\"webauthn.create\"");
        sb.append(",\"challenge\":\"").append(escape(challengeB64 == null ? "" : challengeB64)).append('\"');
        sb.append(",\"origin\":\"").append(escape(origin)).append('\"');
        sb.append(",\"crossOrigin\":false");
        sb.append('}');
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
            case '\\':
                sb.append("\\\\");
                break;
            case '"':
                sb.append("\\\"");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\t':
                sb.append("\\t");
                break;
            default:
                if (c < 0x20) {
                    sb.append(String.format("\\u%04x", (int) c));
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
