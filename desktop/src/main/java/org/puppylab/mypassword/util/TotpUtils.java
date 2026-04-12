package org.puppylab.mypassword.util;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.puppylab.mypassword.core.data.TotpData;

public class TotpUtils {

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Parse an {@code otpauth://totp/...} URI into a {@link TotpData}.
     */
    public static TotpData parseUri(String uri) {
        URI parsed = URI.create(uri);
        if (!"otpauth".equals(parsed.getScheme())) {
            throw new IllegalArgumentException("Not an otpauth URI: " + uri);
        }
        Map<String, String> params = new LinkedHashMap<>();
        String query = parsed.getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    params.put(pair.substring(0, eq).toLowerCase(),
                            java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        String secret = params.get("secret");
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Missing secret in otpauth URI");
        }
        TotpData data = new TotpData();
        data.secret = secret.toUpperCase().replace(" ", "");
        data.issuer = params.getOrDefault("issuer", "");
        data.algorithm = params.getOrDefault("algorithm", "SHA1").toUpperCase();
        try {
            data.digits = Integer.parseInt(params.getOrDefault("digits", "6"));
        } catch (NumberFormatException e) {
            data.digits = 6;
        }
        try {
            data.period = Integer.parseInt(params.getOrDefault("period", "30"));
        } catch (NumberFormatException e) {
            data.period = 30;
        }
        return data;
    }

    /**
     * Generate the current TOTP code for the given configuration.
     */
    public static String getTotp(TotpData totp) {
        long timeStep = System.currentTimeMillis() / 1000L / totp.period;
        byte[] key = base32Decode(totp.secret);
        String hmacAlgo = switch (totp.algorithm) {
            case "SHA256" -> "HmacSHA256";
            case "SHA512" -> "HmacSHA512";
            default -> "HmacSHA1";
        };
        byte[] hash;
        try {
            Mac mac = Mac.getInstance(hmacAlgo);
            mac.init(new SecretKeySpec(key, hmacAlgo));
            hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timeStep).array());
        } catch (Exception e) {
            throw new RuntimeException("TOTP generation failed", e);
        }
        int offset = hash[hash.length - 1] & 0x0F;
        int code = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = code % (int) Math.pow(10, totp.digits);
        return String.format("%0" + totp.digits + "d", otp);
    }

    private static byte[] base32Decode(String input) {
        String s = input.toUpperCase().replaceAll("[= ]", "");
        int outLen = s.length() * 5 / 8;
        byte[] out = new byte[outLen];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : s.toCharArray()) {
            int val = BASE32_CHARS.indexOf(c);
            if (val < 0) throw new IllegalArgumentException("Invalid base32 character: " + c);
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out[idx++] = (byte) (buffer >> bitsLeft);
            }
        }
        return out;
    }
}
