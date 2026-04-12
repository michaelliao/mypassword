package org.puppylab.mypassword.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;

/**
 * Process-wide cache for toolbar/UI icons loaded from {@code /icons/<name>.png}
 * (with HiDPI fallback to {@code /icons/<name>@2x.png} at 200% zoom). All
 * cached images are disposed when the {@link Display} is disposed, so callers
 * must <strong>never</strong> dispose the result of {@link #get(String)}.
 *
 * <p>This class is UI-thread only: all access must happen on the SWT display
 * thread. Images are shared across every widget that calls
 * {@code setImage(Icons.get(...))}, which is safe because SWT {@code Button}
 * does not take ownership of its image.
 */
public final class Icons {

    private static final Map<String, Image> CACHE = new HashMap<>();
    private static boolean disposeHooked = false;

    private Icons() {}

    /**
     * Return a shared {@link Image} for {@code /icons/<name>.png}. The image is
     * owned by the cache — do not dispose it.
     */
    public static Image get(String name) {
        Image img = CACHE.get(name);
        if (img != null && !img.isDisposed()) {
            return img;
        }
        Display display = Display.getCurrent();
        if (display == null) {
            throw new IllegalStateException("Icons.get must be called on the SWT UI thread");
        }
        img = new Image(display, (int zoom) -> {
            String path = zoom >= 200
                    ? "/icons/" + name + "@2x.png"
                    : "/icons/" + name + ".png";
            try (InputStream is = Icons.class.getResourceAsStream(path)) {
                return is == null ? null : new ImageData(is);
            } catch (IOException e) {
                return null;
            }
        });
        CACHE.put(name, img);
        hookDispose(display);
        return img;
    }

    private static void hookDispose(Display display) {
        if (disposeHooked) {
            return;
        }
        disposeHooked = true;
        display.addListener(SWT.Dispose, _ -> {
            for (Image img : CACHE.values()) {
                if (img != null && !img.isDisposed()) {
                    img.dispose();
                }
            }
            CACHE.clear();
            disposeHooked = false;
        });
    }
}
