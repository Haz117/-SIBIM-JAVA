package com.sibim.util;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.function.Consumer;

/**
 * Intercepts USB HID barcode/QR reader input on a JavaFX scene.
 * Readers type characters very fast (< THRESHOLD_MS per char) followed by Enter.
 * Human typing is slower, so we distinguish by keystroke timing.
 */
public final class BarcodeScanner {

    private static final long THRESHOLD_MS = 80;   // ms max between chars from a scanner
    private static final int  MIN_LENGTH   = 3;    // minimum barcode length to consider

    private final StringBuilder buffer   = new StringBuilder();
    private       long          lastTime = 0;

    private BarcodeScanner() {}

    /**
     * Attaches a barcode scanner listener to {@code scene}.
     * When a barcode is detected, {@code onScan} is called on the FX thread
     * with the scanned string. Returns a Runnable to detach if needed.
     */
    public static Runnable attach(Scene scene, Consumer<String> onScan) {
        BarcodeScanner bs = new BarcodeScanner();
        javafx.event.EventHandler<KeyEvent> handler = event -> {
            if (event.getEventType() != KeyEvent.KEY_PRESSED) return;
            long now = System.currentTimeMillis();

            if (now - bs.lastTime > THRESHOLD_MS) {
                bs.buffer.setLength(0);
            }
            bs.lastTime = now;

            KeyCode code = event.getCode();
            if (code == KeyCode.ENTER || code == KeyCode.TAB) {
                String scanned = bs.buffer.toString().trim();
                bs.buffer.setLength(0);
                if (scanned.length() >= MIN_LENGTH) {
                    onScan.accept(scanned);
                    event.consume(); // prevent Enter from triggering focused buttons
                }
            } else if (!code.isModifierKey() && !code.isFunctionKey()
                       && !code.isNavigationKey() && !code.isArrowKey()) {
                bs.buffer.append(event.getText());
            }
        };
        scene.addEventFilter(KeyEvent.KEY_PRESSED, handler);
        return () -> scene.removeEventFilter(KeyEvent.KEY_PRESSED, handler);
    }
}
