package org.puppylab.mypassword.util;

import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

public class Base64Utils {

    static final Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    static final Decoder decoder = Base64.getUrlDecoder();

    /**
     * Do URL safe base64 encode.
     */
    public static String b64(byte[] data) {
        return encoder.encodeToString(data);
    }

    /**
     * Do URL safe base64 decode.
     */
    public static byte[] b64(String b64str) {
        return decoder.decode(b64str);
    }
}
