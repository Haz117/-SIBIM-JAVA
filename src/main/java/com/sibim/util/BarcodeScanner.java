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
    /** After a scan, the rest of the same burst: a SIBIM QR carries the
     *  bien's ficha in several lines and the scanner types them all, each
     *  ending in Enter. Only the first line (the código) counts. */
    private       boolean       restoDeRafaga = false;
    /** What the burst has typed so far into whichever control has the focus. */
    private final StringBuilder tecleado = new StringBuilder();

    private BarcodeScanner() {}

    /** A reader is a keyboard: until its Enter says "this was a scan", every
     *  character has already gone into the focused field — the search box of
     *  the conteo (which then filtered the list down to nothing), or a count.
     *  Takes them back out, only if they are still there as typed. */
    static void quitarTecleado(javafx.scene.Node foco, String tecleado) {
        javafx.scene.control.TextInputControl campo =
            foco instanceof javafx.scene.control.TextInputControl t ? t
            : foco instanceof javafx.scene.control.Spinner<?> s ? s.getEditor()
            : foco instanceof javafx.scene.control.ComboBox<?> c && c.isEditable() ? c.getEditor()
            : null;
        if (campo == null || tecleado.isEmpty()) return;
        int fin = campo.getCaretPosition();
        int inicio = fin - tecleado.length();
        if (inicio >= 0 && fin <= campo.getLength() && campo.getText(inicio, fin).equals(tecleado))
            campo.deleteText(inicio, fin);
    }

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
                bs.tecleado.setLength(0);
                bs.restoDeRafaga = false;   // a pause: whatever comes next is new input
            }
            bs.lastTime = now;

            if (bs.restoDeRafaga) {   // the ficha lines after the código: swallow them
                event.consume();
                return;
            }
            KeyCode code = event.getCode();
            if (code == KeyCode.ENTER || code == KeyCode.TAB) {
                String scanned = bs.buffer.toString().trim();
                bs.buffer.setLength(0);
                if (scanned.length() >= MIN_LENGTH) {
                    bs.restoDeRafaga = true;
                    quitarTecleado(scene.getFocusOwner(), bs.tecleado.toString());
                    onScan.accept(scanned);
                    event.consume(); // prevent Enter from triggering focused buttons
                }
                bs.tecleado.setLength(0);
            } else if (!code.isModifierKey() && !code.isFunctionKey()
                       && !code.isNavigationKey() && !code.isArrowKey()) {
                bs.buffer.append(event.getText());
            }
        };
        // Keeps the rest of a burst from being typed into the focused field.
        javafx.event.EventHandler<KeyEvent> tecleo = event -> {
            if (bs.restoDeRafaga && System.currentTimeMillis() - bs.lastTime <= THRESHOLD_MS) {
                event.consume();
                return;
            }
            String c = event.getCharacter();
            if (c != null && !c.isEmpty() && c.charAt(0) >= ' ' && c.charAt(0) != 127) bs.tecleado.append(c);
        };
        scene.addEventFilter(KeyEvent.KEY_PRESSED, handler);
        scene.addEventFilter(KeyEvent.KEY_TYPED, tecleo);
        return () -> {
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, handler);
            scene.removeEventFilter(KeyEvent.KEY_TYPED, tecleo);
        };
    }
}
