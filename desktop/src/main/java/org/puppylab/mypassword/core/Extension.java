package org.puppylab.mypassword.core;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.puppylab.mypassword.core.entity.ExtensionConfig;
import org.puppylab.mypassword.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.Headers;

public class Extension {

    static final Logger logger = LoggerFactory.getLogger(Extension.class);

    private static ThreadLocal<Extension> current = new ThreadLocal<>();

    public final long   id;
    public final String name;
    public final String device;

    private Extension(long id, String name, String device) {
        this.id = id;
        this.name = name;
        this.device = device;
    }

    /**
     * Get current extension, or null if not bind.
     */
    public static Extension getCurrent() {
        return current.get();
    }

    public static void remove() {
        current.remove();
    }

    public static boolean trySetExtension(Headers headers) {
        // get header:
        // X-Extension-Id: 123456
        // X-Extension-Timestamp: 12345678900
        // X-Extension-Signature: a1b2c3d4e5f6
        try {
            String sid = headers.getFirst("X-Extension-Id");
            String sts = headers.getFirst("X-Extension-Timestamp");
            String sig = headers.getFirst("X-Extension-Signature");
            if (sid == null || sts == null || sig == null) {
                return false;
            }
            long id = Long.parseLong(sid);
            long ts = Long.parseLong(sts);
            if (Math.abs(ts - System.currentTimeMillis()) > 30_000L) {
                logger.warn("Extension: invalid timestamp: " + ts);
                return false;
            }
            ExtensionConfig ec = VaultManager.getCurrent().getExtension(id);
            if (ec == null || !ec.approve) {
                logger.warn("Extension: not paired.");
                return false;
            }
            // check signature:
            byte[] hash = HashUtils.hmacSha256(sts, ec.seed.getBytes(StandardCharsets.UTF_8));
            String hexHash = HexFormat.of().formatHex(hash);
            if (!sig.equals(hexHash)) {
                logger.warn("Extension: invalid signature.");
                return false;
            }
            logger.info("Extension: validated ok: {} @ {}", ec.name, ec.device);
            current.set(new Extension(ec.id, ec.name, ec.device));
            return true;
        } catch (Exception e) {
            logger.warn("Extension: validate failed by: " + e.getClass().getName() + ": " + e.getMessage());
        }
        return false;
    }
}
