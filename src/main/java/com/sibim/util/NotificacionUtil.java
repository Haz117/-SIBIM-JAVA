package com.sibim.util;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

public class NotificacionUtil {

    public enum Tipo { EXITO, ERROR, INFO, ADVERTENCIA }

    private static int activeToasts = 0;
    private static final double TOAST_OFFSET  = 64;
    private static final int    MAX_TOASTS    = 4;

    public static void mostrar(Scene scene, String mensaje, Tipo tipo) {
        if (scene == null) return;
        Platform.runLater(() -> show(scene.getWindow(), mensaje, tipo));
    }

    public static void exito(Scene scene, String mensaje)       { mostrar(scene, mensaje, Tipo.EXITO); }
    public static void error(Scene scene, String mensaje)       { mostrar(scene, mensaje, Tipo.ERROR); }
    public static void info(Scene scene, String mensaje)        { mostrar(scene, mensaje, Tipo.INFO); }
    public static void advertencia(Scene scene, String mensaje) { mostrar(scene, mensaje, Tipo.ADVERTENCIA); }

    /** Toast de éxito con botón de acción (p.ej. "↶ Deshacer"). Se oculta a los 8 s o al pulsar el botón. */
    public static void exitoConAccion(Scene scene, String mensaje, String btnLabel, Runnable onAction) {
        if (scene == null) return;
        Platform.runLater(() -> showConAccion(scene.getWindow(), mensaje, btnLabel, onAction));
    }

    /** Success toast for a Transferencia — same position/timing/style as every
     *  other toast (no separate floating overlay), just with the origin →
     *  destino route shown as a small chip row under the message so the route
     *  detail doesn't need a second, differently-placed animation. */
    public static void exitoTransferencia(Scene scene, String producto, String from, String to) {
        if (scene == null) return;
        Platform.runLater(() -> showTransferencia(scene.getWindow(), producto, from, to));
    }

    /** Same centered-card celebration as {@link #exitoTransferencia}, for the
     *  "physical count closed with zero discrepancies" case — simpler content
     *  (no route chips), same card look so both share one visual language. */
    public static void exitoConteo(Scene scene, int totalItems) {
        if (scene == null) return;
        Platform.runLater(() -> showConteoOk(scene.getWindow(), totalItems));
    }

    /** Centered card for "database restore finished" — same card language as
     *  the other one-off system events, but a calmer entrance (no bounce) and
     *  a neutral/info tone instead of a festive one: this is a critical,
     *  rare action (it replaces all data), not something to celebrate. */
    public static void restauracionCompletada(Scene scene, String archivoNombre) {
        if (scene == null) return;
        Platform.runLater(() -> showRestauracion(scene.getWindow(), archivoNombre));
    }

    private static void show(Window owner, String mensaje, Tipo tipo) {
        if (owner == null) return;
        if (activeToasts >= MAX_TOASTS) return;

        String iconLiteral, toastClass, iconClass;
        switch (tipo) {
            case EXITO       -> { iconLiteral = "mdi2c-check-circle";      toastClass = "toast-success"; iconClass = "toast-icon-success"; }
            case ERROR       -> { iconLiteral = "mdi2c-close-circle";      toastClass = "toast-error";   iconClass = "toast-icon-error"; }
            case ADVERTENCIA -> { iconLiteral = "mdi2a-alert-circle";      toastClass = "toast-warning"; iconClass = "toast-icon-warning"; }
            default          -> { iconLiteral = "mdi2i-information-outline"; toastClass = "toast-info";  iconClass = "toast-icon-info"; }
        }

        FontIcon iconNode = new FontIcon(iconLiteral);
        iconNode.setIconSize(16);
        iconNode.getStyleClass().add(iconClass);

        StackPane iconBadge = new StackPane(iconNode);
        iconBadge.getStyleClass().addAll("toast-icon-badge", iconClass);

        Label lbl = new Label(mensaje);
        lbl.getStyleClass().add("toast-msg");
        lbl.setWrapText(true);
        lbl.setMaxWidth(390);
        HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);

        FontIcon closeBtn = new FontIcon("mdi2c-close");
        closeBtn.setIconSize(14);
        closeBtn.getStyleClass().add("toast-close");

        HBox box = new HBox(12, iconBadge, lbl, closeBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(13, 16, 13, 16));
        box.getStyleClass().addAll("toast-box", toastClass);

        // Popup is not in the main scene, so apply stylesheet directly to the root node
        var css = NotificacionUtil.class.getResource("/css/styles.css");
        if (css != null) box.getStylesheets().add(css.toExternalForm());

        box.setOpacity(0);
        box.setTranslateY(-16);

        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().add(box);

        double x = owner.getX() + (owner.getWidth() - 500) / 2.0;
        double y = owner.getY() + 22 + (activeToasts * TOAST_OFFSET);
        activeToasts++;
        popup.show(owner, x, y);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), box);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), box);
        slideIn.setFromY(-16); slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        long ms = (tipo == Tipo.ERROR || tipo == Tipo.ADVERTENCIA) ? 4500 : 2800;
        PauseTransition pause = new PauseTransition(Duration.millis(ms));

        TranslateTransition slideOut = new TranslateTransition(Duration.millis(260), box);
        slideOut.setToX(54);
        slideOut.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(260), box);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        ParallelTransition dismissAnim = new ParallelTransition(slideOut, fadeOut);
        dismissAnim.setOnFinished(e -> { popup.hide(); activeToasts--; });

        SequentialTransition seq = new SequentialTransition(
            new ParallelTransition(fadeIn, slideIn), pause, dismissAnim);
        seq.play();

        // Guards against a fast double-click: without it, a second click
        // during the 150ms quickFade below re-enters this handler and plays
        // a second fade whose onFinished decrements activeToasts a second
        // time for the same toast, throwing off the vertical stacking
        // offset of whichever toasts show next.
        boolean[] closing = { false };
        closeBtn.setOnMouseClicked(e -> {
            if (closing[0]) return;
            closing[0] = true;
            seq.stop();
            TranslateTransition quickSlide = new TranslateTransition(Duration.millis(190), box);
            quickSlide.setToX(54);
            quickSlide.setInterpolator(Interpolator.EASE_IN);
            FadeTransition quickFade = new FadeTransition(Duration.millis(190), box);
            quickFade.setFromValue(box.getOpacity()); quickFade.setToValue(0);
            ParallelTransition quickDismiss = new ParallelTransition(quickSlide, quickFade);
            quickDismiss.setOnFinished(ev -> { popup.hide(); activeToasts--; });
            quickDismiss.play();
        });
    }

    /** Centered (not stacked with the corner toasts — this is a one-off,
     *  standalone celebration), a bit bigger than a normal toast, with a
     *  proper entrance flourish: the card pops in with a slight overshoot,
     *  then the arrow slides in and the destination chip pops in right after. */
    private static void showTransferencia(Window owner, String producto, String from, String to) {
        if (owner == null) return;

        FontIcon iconLbl = new FontIcon("mdi2c-check-circle");
        iconLbl.setIconSize(26);
        iconLbl.getStyleClass().add("toast-icon-success");

        Label titleLbl = new Label("Transferencia registrada");
        titleLbl.getStyleClass().add("transfer-cel-title");

        Label productLbl = new Label(producto != null ? producto : "");
        productLbl.getStyleClass().add("transfer-cel-product");
        productLbl.setWrapText(true);

        Label fromLbl = new Label(from != null ? from : "—");
        fromLbl.getStyleClass().add("transfer-cel-chip-from");
        Label arrowLbl = new Label("→");
        arrowLbl.getStyleClass().add("transfer-cel-arrow");
        arrowLbl.setTranslateX(-10);
        arrowLbl.setOpacity(0);
        Label toLbl = new Label(to != null ? to : "—");
        toLbl.getStyleClass().add("transfer-cel-chip-to");
        toLbl.setScaleX(0.6); toLbl.setScaleY(0.6);
        toLbl.setOpacity(0);
        HBox routeRow = new HBox(8, fromLbl, arrowLbl, toLbl);
        routeRow.setAlignment(Pos.CENTER);

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(9, iconLbl, titleLbl, productLbl, routeRow);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(22, 30, 22, 30));
        box.setMaxWidth(340);
        box.getStyleClass().add("transfer-cel-card");

        var css = NotificacionUtil.class.getResource("/css/styles.css");
        if (css != null) box.getStylesheets().add(css.toExternalForm());

        box.setOpacity(0);
        box.setScaleX(0.85); box.setScaleY(0.85);

        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().add(box);
        popup.show(owner, owner.getX() + owner.getWidth() / 2.0, owner.getY() + owner.getHeight() / 2.0);

        // Re-center now that the box has a real width/height, then start
        // the entrance animation — width/height are 0 until after the first
        // layout pass, so this has to happen post-show.
        Platform.runLater(() -> {
            popup.setX(owner.getX() + (owner.getWidth() - box.getWidth()) / 2.0);
            popup.setY(owner.getY() + (owner.getHeight() - box.getHeight()) / 2.0);

            ScaleTransition popIn = new ScaleTransition(Duration.millis(340), box);
            popIn.setToX(1.04); popIn.setToY(1.04);
            popIn.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition settle = new ScaleTransition(Duration.millis(140), box);
            settle.setToX(1); settle.setToY(1);
            settle.setInterpolator(Interpolator.EASE_IN);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(260), box);
            fadeIn.setToValue(1);

            TranslateTransition arrowSlide = new TranslateTransition(Duration.millis(280), arrowLbl);
            arrowSlide.setToX(0);
            arrowSlide.setDelay(Duration.millis(260));
            arrowSlide.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition arrowFade = new FadeTransition(Duration.millis(220), arrowLbl);
            arrowFade.setToValue(1);
            arrowFade.setDelay(Duration.millis(260));

            ScaleTransition toPop = new ScaleTransition(Duration.millis(280), toLbl);
            toPop.setToX(1.12); toPop.setToY(1.12);
            toPop.setDelay(Duration.millis(440));
            toPop.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition toSettle = new ScaleTransition(Duration.millis(140), toLbl);
            toSettle.setToX(1); toSettle.setToY(1);
            toSettle.setDelay(Duration.millis(720));
            toSettle.setInterpolator(Interpolator.EASE_IN);
            FadeTransition toFade = new FadeTransition(Duration.millis(220), toLbl);
            toFade.setToValue(1);
            toFade.setDelay(Duration.millis(440));

            FadeTransition fadeOut = new FadeTransition(Duration.millis(280), box);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> popup.hide());

            new SequentialTransition(
                new ParallelTransition(fadeIn, new SequentialTransition(popIn, settle),
                    arrowSlide, arrowFade, toPop, toSettle, toFade),
                new PauseTransition(Duration.millis(1900)),
                fadeOut
            ).play();
        });
    }

    /** Centered pop-in card, simpler than {@link #showTransferencia} (no
     *  route chips) — just icon, title and a subtitle with the item count. */
    private static void showConteoOk(Window owner, int totalItems) {
        if (owner == null) return;

        FontIcon iconLbl = new FontIcon("mdi2c-check-circle");
        iconLbl.setIconSize(26);
        iconLbl.getStyleClass().add("toast-icon-success");

        Label titleLbl = new Label("Conteo completado");
        titleLbl.getStyleClass().add("transfer-cel-title");

        Label subtitleLbl = new Label("Sin diferencias en " + totalItems + " bien(es)");
        subtitleLbl.getStyleClass().add("transfer-cel-product");
        subtitleLbl.setWrapText(true);

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(9, iconLbl, titleLbl, subtitleLbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(22, 30, 22, 30));
        box.setMaxWidth(340);
        box.getStyleClass().add("transfer-cel-card");

        var css = NotificacionUtil.class.getResource("/css/styles.css");
        if (css != null) box.getStylesheets().add(css.toExternalForm());

        box.setOpacity(0);
        box.setScaleX(0.85); box.setScaleY(0.85);

        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().add(box);
        popup.show(owner, owner.getX() + owner.getWidth() / 2.0, owner.getY() + owner.getHeight() / 2.0);

        Platform.runLater(() -> {
            popup.setX(owner.getX() + (owner.getWidth() - box.getWidth()) / 2.0);
            popup.setY(owner.getY() + (owner.getHeight() - box.getHeight()) / 2.0);

            ScaleTransition popIn = new ScaleTransition(Duration.millis(340), box);
            popIn.setToX(1.04); popIn.setToY(1.04);
            popIn.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition settle = new ScaleTransition(Duration.millis(140), box);
            settle.setToX(1); settle.setToY(1);
            settle.setInterpolator(Interpolator.EASE_IN);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(260), box);
            fadeIn.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(280), box);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> popup.hide());

            new SequentialTransition(
                new ParallelTransition(fadeIn, new SequentialTransition(popIn, settle)),
                new PauseTransition(Duration.millis(1700)),
                fadeOut
            ).play();
        });
    }

    /** Calmer cousin of {@link #showConteoOk}: no bounce overshoot on entry
     *  (a plain fade+scale-up feels more "confirmed" than "celebrated"), an
     *  info-blue icon instead of success-green, and a longer hold time since
     *  the restart reminder in the subtitle actually needs to be read. */
    private static void showRestauracion(Window owner, String archivoNombre) {
        if (owner == null) return;

        FontIcon iconLbl = new FontIcon("mdi2s-shield-check-outline");
        iconLbl.setIconSize(26);
        iconLbl.getStyleClass().add("toast-icon-info");

        Label titleLbl = new Label("Base de datos restaurada");
        titleLbl.getStyleClass().add("transfer-cel-title");

        Label subtitleLbl = new Label("Desde \"" + (archivoNombre != null ? archivoNombre : "") + "\" — "
            + "reinicia la aplicación para ver los datos actualizados");
        subtitleLbl.getStyleClass().add("transfer-cel-product");
        subtitleLbl.setWrapText(true);
        subtitleLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(9, iconLbl, titleLbl, subtitleLbl);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(22, 30, 22, 30));
        box.setMaxWidth(360);
        box.getStyleClass().add("transfer-cel-card");

        var css = NotificacionUtil.class.getResource("/css/styles.css");
        if (css != null) box.getStylesheets().add(css.toExternalForm());

        box.setOpacity(0);
        box.setScaleX(0.94); box.setScaleY(0.94);

        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().add(box);
        popup.show(owner, owner.getX() + owner.getWidth() / 2.0, owner.getY() + owner.getHeight() / 2.0);

        Platform.runLater(() -> {
            popup.setX(owner.getX() + (owner.getWidth() - box.getWidth()) / 2.0);
            popup.setY(owner.getY() + (owner.getHeight() - box.getHeight()) / 2.0);

            ScaleTransition growIn = new ScaleTransition(Duration.millis(240), box);
            growIn.setToX(1); growIn.setToY(1);
            growIn.setInterpolator(Interpolator.EASE_OUT);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(240), box);
            fadeIn.setToValue(1);

            FadeTransition fadeOut = new FadeTransition(Duration.millis(280), box);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> popup.hide());

            new SequentialTransition(
                new ParallelTransition(fadeIn, growIn),
                new PauseTransition(Duration.millis(3400)),
                fadeOut
            ).play();
        });
    }

    private static void showConAccion(Window owner, String mensaje, String btnLabel, Runnable onAction) {
        if (owner == null) return;
        if (activeToasts >= MAX_TOASTS) return;

        FontIcon iconLbl = new FontIcon("mdi2c-check-circle");
        iconLbl.setIconSize(16);
        iconLbl.getStyleClass().add("toast-icon-success");
        StackPane iconBadge = new StackPane(iconLbl);
        iconBadge.getStyleClass().addAll("toast-icon-badge", "toast-icon-success");

        Label lbl = new Label(mensaje);
        lbl.getStyleClass().add("toast-msg");
        lbl.setWrapText(true);
        lbl.setMaxWidth(220);
        HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.control.Button actionBtn = new javafx.scene.control.Button(btnLabel);
        actionBtn.getStyleClass().add("toast-action-btn");

        FontIcon closeBtn = new FontIcon("mdi2c-close");
        closeBtn.setIconSize(14);
        closeBtn.getStyleClass().add("toast-close");

        HBox box = new HBox(10, iconBadge, lbl, actionBtn, closeBtn);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(13, 16, 13, 16));
        box.getStyleClass().addAll("toast-box", "toast-success");

        var css = NotificacionUtil.class.getResource("/css/styles.css");
        if (css != null) box.getStylesheets().add(css.toExternalForm());

        box.setOpacity(0);
        box.setTranslateY(-16);

        Popup popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().add(box);

        double x = owner.getX() + (owner.getWidth() - 500) / 2.0;
        double y = owner.getY() + 22 + (activeToasts * TOAST_OFFSET);
        activeToasts++;
        popup.show(owner, x, y);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), box);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), box);
        slideIn.setFromY(-16); slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);
        PauseTransition pause = new PauseTransition(Duration.millis(8000));
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(260), box);
        slideOut.setToX(54);
        slideOut.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(260), box);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        ParallelTransition dismissAnim = new ParallelTransition(slideOut, fadeOut);
        dismissAnim.setOnFinished(e -> { popup.hide(); activeToasts--; });

        SequentialTransition seq = new SequentialTransition(
            new ParallelTransition(fadeIn, slideIn), pause, dismissAnim);
        seq.play();

        Runnable dismiss = () -> {
            seq.stop();
            TranslateTransition qs = new TranslateTransition(Duration.millis(190), box);
            qs.setToX(54); qs.setInterpolator(Interpolator.EASE_IN);
            FadeTransition qf = new FadeTransition(Duration.millis(190), box);
            qf.setFromValue(box.getOpacity()); qf.setToValue(0);
            ParallelTransition qd = new ParallelTransition(qs, qf);
            qd.setOnFinished(ev -> { popup.hide(); activeToasts--; });
            qd.play();
        };

        actionBtn.setOnAction(e -> { dismiss.run(); onAction.run(); });
        closeBtn.setOnMouseClicked(e -> dismiss.run());
    }
}
