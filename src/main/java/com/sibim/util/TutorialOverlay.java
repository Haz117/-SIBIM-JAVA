package com.sibim.util;

import com.sibim.session.SessionManager;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.prefs.Preferences;

public final class TutorialOverlay {

    private static final Preferences PREFS = Preferences.userNodeForPackage(TutorialOverlay.class);

    private record Step(
        String icon, String color, String gradEnd,
        String title, String shortcut, String[] bullets
    ) {}

    private static final Step[] STEPS = {
        new Step("mdi2b-book-open-outline", "#6366F1", "#3730A3",
            "Bienvenido a SIBIM", null, new String[]{
            "Sistema integral de gestión del patrimonio municipal",
            "Inventario, movimientos y reportes en un solo lugar",
            "Funciona en línea, sin conexión y en modo demo"
        }),
        new Step("mdi2v-view-dashboard-outline", "#0EA5E9", "#0369A1",
            "Dashboard", "Ctrl + 1", new String[]{
            "Métricas en tiempo real: total de bienes y valor del inventario",
            "Gráfica de salud del inventario (normal / bajo stock / agotado)",
            "Acciones rápidas para los flujos más frecuentes del día"
        }),
        new Step("mdi2p-package-variant", "#6366F1", "#3730A3",
            "Bienes / Inventario", "Ctrl + 3", new String[]{
            "Búsqueda en tiempo real por nombre, código o área asignada",
            "Doble clic en un bien para ver ficha completa e historial",
            "Altas, bajas y edición desde la misma pantalla"
        }),
        new Step("mdi2s-swap-vertical-bold", "#7C3AED", "#5B21B6",
            "Movimientos", "Ctrl + 5", new String[]{
            "Entradas (adquisiciones), salidas (bajas) y asignaciones entre áreas",
            "Conteo físico para verificar el inventario en campo",
            "Cada movimiento actualiza el stock automáticamente"
        }),
        new Step("mdi2b-bell-ring-outline", "#DC2626", "#991B1B",
            "Alertas", "Ctrl + 6", new String[]{
            "Alerta automática cuando un bien llega a su stock mínimo",
            "Aviso de bienes próximos a vencer con período configurable",
            "Badge rojo en el menú lateral cuando hay alertas pendientes"
        }),
        new Step("mdi2f-file-chart-outline", "#059669", "#065F46",
            "Reportes", "Ctrl + 7", new String[]{
            "Exporta a Excel (.xlsx) y PDF con un solo clic",
            "Filtra por rango de fechas, área y categoría de bien",
            "Incluye gráficas y resúmenes listos para auditorías"
        }),
        new Step("mdi2k-keyboard-outline", "#0891B2", "#0E7490",
            "Atajos de Teclado", "F1", new String[]{
            "Ctrl+1 al Ctrl+9 para navegar entre módulos al instante",
            "Ctrl+F para buscar · F5 para actualizar · F1 para esta ayuda",
            "En tablas: Ctrl+N nuevo · Ctrl+E editar · Supr para eliminar"
        }),
        new Step("mdi2c-check-circle-outline", "#16A34A", "#14532D",
            "¡Todo listo!", null, new String[]{
            "Presiona F1 en cualquier momento para ver todos los atajos",
            "Botón \"Tutorial\" en el menú lateral para volver a este recorrido",
            "El sistema guarda tus cambios aunque pierdas la conexión"
        })
    };

    private TutorialOverlay() {}

    public static void showIfFirstTime(StackPane outerStack) {
        String username = SessionManager.getCurrentUser() != null
            ? SessionManager.getCurrentUser().getUsername() : "unknown";
        String key = "tutorial.v1." + username;
        if (PREFS.getBoolean(key, false)) return;
        PREFS.putBoolean(key, true);
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    public static void show(StackPane outerStack) {
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    private static void buildAndShow(StackPane outerStack) {
        int[] step = {0};

        // ── Gradient header ────────────────────────────────────────────────────
        StackPane header = new StackPane();
        header.setPrefHeight(102);
        header.setMaxHeight(102);

        // Progress bar track + fill inside header at top
        Region progressTrack = new Region();
        progressTrack.getStyleClass().add("tutorial-progress-track");
        progressTrack.setPrefHeight(5);
        progressTrack.setMaxHeight(5);

        Region progressFill = new Region();
        progressFill.getStyleClass().add("tutorial-progress-fill");
        progressFill.setPrefHeight(5);
        progressFill.setMaxHeight(5);
        progressFill.setPrefWidth(0);

        StackPane progressPane = new StackPane(progressTrack, progressFill);
        progressPane.setAlignment(Pos.CENTER_LEFT);
        progressPane.setPrefHeight(5);
        progressPane.setMaxHeight(5);
        progressTrack.prefWidthProperty().bind(header.widthProperty());

        StackPane.setAlignment(progressPane, Pos.TOP_LEFT);

        // Icon badge floats at header bottom, extends 38px into white area
        FontIcon icon = new FontIcon();
        icon.setIconSize(34);

        StackPane iconBadge = new StackPane(icon);
        iconBadge.getStyleClass().add("tutorial-icon-badge");
        iconBadge.setPrefSize(76, 76);
        iconBadge.setMaxSize(76, 76);
        iconBadge.setTranslateY(38); // extend below header

        StackPane.setAlignment(iconBadge, Pos.BOTTOM_CENTER);

        header.getChildren().addAll(progressPane, iconBadge);

        // ── Sliding content pane ───────────────────────────────────────────────
        Label lblTitle = new Label();
        lblTitle.getStyleClass().add("tutorial-title");
        lblTitle.setWrapText(true);
        lblTitle.setMaxWidth(360);
        lblTitle.setAlignment(Pos.CENTER);

        VBox bulletBox = new VBox(10);
        bulletBox.setAlignment(Pos.CENTER_LEFT);
        bulletBox.setMaxWidth(352);

        // Shortcut pill
        FontIcon kbIcon = new FontIcon("mdi2k-keyboard-outline");
        kbIcon.setIconSize(13);
        kbIcon.getStyleClass().add("tutorial-shortcut-icon");
        Label lblShortcut = new Label();
        lblShortcut.getStyleClass().add("tutorial-shortcut-text");
        HBox shortcutPill = new HBox(7, kbIcon, lblShortcut);
        shortcutPill.getStyleClass().add("tutorial-shortcut-pill");
        shortcutPill.setAlignment(Pos.CENTER);
        shortcutPill.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane shortcutWrap = new StackPane(shortcutPill);
        shortcutWrap.setAlignment(Pos.CENTER);

        VBox slideContent = new VBox(16, lblTitle, bulletBox, shortcutWrap);
        slideContent.setAlignment(Pos.CENTER);
        slideContent.setMaxWidth(380);

        // ── Dots & counter ─────────────────────────────────────────────────────
        HBox dotsRow = new HBox(9);
        dotsRow.setAlignment(Pos.CENTER);
        Circle[] circles = new Circle[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            circles[i] = new Circle(4);
            circles[i].getStyleClass().add("tutorial-dot");
            circles[i].setCursor(Cursor.HAND);
            dotsRow.getChildren().add(circles[i]);
        }

        Label lblCounter = new Label();
        lblCounter.getStyleClass().add("tutorial-counter");

        VBox dotsArea = new VBox(6, dotsRow, lblCounter);
        dotsArea.setAlignment(Pos.CENTER);

        // ── Buttons ────────────────────────────────────────────────────────────
        Button btnPrev = new Button("← Anterior");
        btnPrev.getStyleClass().add("tutorial-btn-secondary");
        Button btnNext = new Button("Siguiente →");
        btnNext.getStyleClass().add("tutorial-btn-primary");
        Button btnSkip = new Button("Saltar");
        btnSkip.getStyleClass().add("tutorial-btn-skip");

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox btnRow = new HBox(8, btnSkip, btnSpacer, btnPrev, btnNext);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        // ── Card assembly ──────────────────────────────────────────────────────
        VBox contentArea = new VBox(22, slideContent, dotsArea, btnRow);
        contentArea.setAlignment(Pos.CENTER);
        // top padding = iconBadge overlap (38) + gap (16)
        contentArea.setPadding(new Insets(54, 42, 30, 42));

        VBox card = new VBox(0, header, contentArea);
        card.getStyleClass().add("tutorial-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(490);
        card.setMaxWidth(490);

        // ── Backdrop ───────────────────────────────────────────────────────────
        StackPane backdrop = new StackPane(card);
        backdrop.getStyleClass().add("tutorial-backdrop");
        outerStack.getChildren().add(backdrop);

        // ── Dismiss ────────────────────────────────────────────────────────────
        Runnable dismiss = () -> {
            FadeTransition fade = new FadeTransition(Duration.millis(230), backdrop);
            fade.setToValue(0);
            ScaleTransition shrink = new ScaleTransition(Duration.millis(230), card);
            shrink.setToX(0.91); shrink.setToY(0.91);
            shrink.setInterpolator(Interpolator.EASE_IN);
            ParallelTransition exit = new ParallelTransition(fade, shrink);
            exit.setOnFinished(e -> outerStack.getChildren().remove(backdrop));
            exit.play();
        };

        // ── Render (instant, no animation) ────────────────────────────────────
        Runnable render = () -> {
            Step s = STEPS[step[0]];

            // Header gradient
            header.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
                + s.color() + ", " + s.gradEnd() + ");");

            // Icon — white badge (bg+radius in .tutorial-icon-badge CSS) with per-step drop shadow
            icon.setIconLiteral(s.icon());
            icon.setIconColor(Color.web(s.color()));
            iconBadge.setStyle("-fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.50) + ", 22, 0.04, 0, 5);");

            // Title
            lblTitle.setText(s.title());

            // Bullets
            bulletBox.getChildren().clear();
            for (String b : s.bullets()) {
                FontIcon chevron = new FontIcon("mdi2c-chevron-right");
                chevron.setIconSize(15);
                chevron.setStyle("-fx-icon-color: " + s.color() + ";");
                Label lbl = new Label(b);
                lbl.getStyleClass().add("tutorial-bullet-text");
                lbl.setWrapText(true);
                lbl.setMaxWidth(320);
                HBox row = new HBox(9, chevron, lbl);
                row.setAlignment(Pos.TOP_LEFT);
                row.getStyleClass().add("tutorial-bullet-row");
                bulletBox.getChildren().add(row);
            }

            // Shortcut pill
            if (s.shortcut() != null) {
                lblShortcut.setText(s.shortcut());
                // Static layout (radius, border-width, padding) in .tutorial-shortcut-pill CSS
                // Static text style (font-size, font-weight) in .tutorial-shortcut-text CSS
                kbIcon.setStyle("-fx-icon-color: " + s.color() + ";");
                shortcutPill.setStyle(
                    "-fx-background-color: " + hexToRgba(s.color(), 0.09) + ";"
                    + "-fx-border-color: " + hexToRgba(s.color(), 0.30) + ";");
                lblShortcut.setStyle("-fx-text-fill: " + s.color() + ";");
                shortcutWrap.setVisible(true);
                shortcutWrap.setManaged(true);
            } else {
                shortcutWrap.setVisible(false);
                shortcutWrap.setManaged(false);
            }

            // Dots
            for (int i = 0; i < circles.length; i++) {
                circles[i].getStyleClass().removeAll("tutorial-dot-active");
                if (i == step[0]) {
                    circles[i].getStyleClass().add("tutorial-dot-active");
                    circles[i].setStyle("-fx-fill: " + s.color() + ";");
                    circles[i].setRadius(5.5);
                } else {
                    circles[i].setStyle("");
                    circles[i].setRadius(3.5);
                }
            }

            // Counter & buttons
            lblCounter.setText("Paso " + (step[0] + 1) + " de " + STEPS.length);
            boolean isFirst = step[0] == 0;
            boolean isLast  = step[0] == STEPS.length - 1;
            btnPrev.setVisible(!isFirst); btnPrev.setManaged(!isFirst);
            btnNext.setText(isLast ? "Comenzar  ✓" : "Siguiente →");
            btnNext.setStyle("-fx-background-color: " + s.color()
                + "; -fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.40) + ", 10, 0, 0, 2);");
            btnSkip.setVisible(!isLast); btnSkip.setManaged(!isLast);
        };

        // ── Animated go-to (handles both advance and retreat) ─────────────────
        java.util.function.BiConsumer<Integer, Integer> goTo = (newIdx, dir) -> {
            // Lock controls during transition
            btnNext.setDisable(true);
            btnPrev.setDisable(true);
            btnSkip.setDisable(true);
            dotsRow.setMouseTransparent(true);

            // Slide + fade out current content
            TranslateTransition slideOut = new TranslateTransition(Duration.millis(170), slideContent);
            slideOut.setToX(dir * -55.0);
            slideOut.setInterpolator(Interpolator.EASE_IN);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), slideContent);
            fadeOut.setToValue(0);
            ParallelTransition exitAnim = new ParallelTransition(slideOut, fadeOut);

            exitAnim.setOnFinished(ev -> {
                step[0] = newIdx;
                render.run();

                // Animate progress bar fill width
                double targetW = progressPane.getWidth() * (newIdx + 1.0) / STEPS.length;
                new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(progressFill.prefWidthProperty(), progressFill.getPrefWidth())),
                    new KeyFrame(Duration.millis(290),
                        new KeyValue(progressFill.prefWidthProperty(), targetW, Interpolator.EASE_OUT))
                ).play();

                // Icon badge bounce (scale from 0.5 → 1.08 → 1.0)
                iconBadge.setScaleX(0.5); iconBadge.setScaleY(0.5);
                Timeline iconPop = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(iconBadge.scaleXProperty(), 0.5, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 0.5, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(220),
                        new KeyValue(iconBadge.scaleXProperty(), 1.08, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 1.08, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(320),
                        new KeyValue(iconBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(iconBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                );
                iconPop.play();

                // Slide + fade in from opposite direction
                slideContent.setTranslateX(dir * 55.0);
                TranslateTransition slideIn = new TranslateTransition(Duration.millis(230), slideContent);
                slideIn.setToX(0);
                slideIn.setInterpolator(Interpolator.EASE_OUT);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(210), slideContent);
                fadeIn.setFromValue(0); fadeIn.setToValue(1);
                ParallelTransition enterAnim = new ParallelTransition(slideIn, fadeIn);

                enterAnim.setOnFinished(done -> {
                    // Unlock controls
                    btnNext.setDisable(false);
                    btnPrev.setDisable(false);
                    btnSkip.setDisable(false);
                    dotsRow.setMouseTransparent(false);
                });
                enterAnim.play();

                // Stagger bullets in
                bulletBox.getChildren().forEach(n -> { n.setOpacity(0); n.setTranslateY(14); });
                int[] idx = {0};
                bulletBox.getChildren().forEach(n -> {
                    int delay = 80 + idx[0] * 65;
                    FadeTransition bf = new FadeTransition(Duration.millis(210), n);
                    bf.setFromValue(0); bf.setToValue(1); bf.setDelay(Duration.millis(delay));
                    TranslateTransition bt = new TranslateTransition(Duration.millis(210), n);
                    bt.setFromY(14); bt.setToY(0); bt.setDelay(Duration.millis(delay));
                    bt.setInterpolator(Interpolator.EASE_OUT);
                    new ParallelTransition(bf, bt).play();
                    idx[0]++;
                });

                // Shortcut pill pop-in
                if (shortcutWrap.isVisible()) {
                    shortcutPill.setOpacity(0);
                    shortcutPill.setScaleX(0.65); shortcutPill.setScaleY(0.65);
                    FadeTransition sf = new FadeTransition(Duration.millis(220), shortcutPill);
                    sf.setFromValue(0); sf.setToValue(1); sf.setDelay(Duration.millis(200));
                    ScaleTransition sp = new ScaleTransition(Duration.millis(260), shortcutPill);
                    sp.setFromX(0.65); sp.setFromY(0.65);
                    sp.setToX(1); sp.setToY(1);
                    sp.setInterpolator(Interpolator.EASE_OUT); sp.setDelay(Duration.millis(200));
                    new ParallelTransition(sf, sp).play();
                }
            });
            exitAnim.play();
        };

        // ── Dot clicks ─────────────────────────────────────────────────────────
        for (int i = 0; i < STEPS.length; i++) {
            final int idx = i;
            circles[i].setOnMouseClicked(e -> {
                if (idx != step[0] && !btnNext.isDisabled())
                    goTo.accept(idx, idx > step[0] ? 1 : -1);
            });
        }

        // ── Button actions ─────────────────────────────────────────────────────
        btnNext.setOnAction(e -> {
            if (step[0] < STEPS.length - 1) goTo.accept(step[0] + 1, 1);
            else dismiss.run();
        });
        btnPrev.setOnAction(e -> {
            if (step[0] > 0) goTo.accept(step[0] - 1, -1);
        });
        btnSkip.setOnAction(e -> dismiss.run());

        // ── Keyboard nav ───────────────────────────────────────────────────────
        backdrop.setFocusTraversable(true);
        backdrop.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case RIGHT -> { if (!btnNext.isDisabled()) btnNext.fire(); }
                case LEFT  -> { if (step[0] > 0 && !btnPrev.isDisabled()) btnPrev.fire(); }
                case ESCAPE -> dismiss.run();
                default -> {}
            }
        });

        // ── Initial render + entrance animation ────────────────────────────────
        render.run();

        // Set progress bar initial width after first layout
        javafx.application.Platform.runLater(() -> {
            double w = progressPane.getWidth();
            if (w > 0) progressFill.setPrefWidth(w / STEPS.length);
        });

        backdrop.setOpacity(0);
        card.setScaleX(0.82); card.setScaleY(0.82);
        FadeTransition backdropIn = new FadeTransition(Duration.millis(280), backdrop);
        backdropIn.setFromValue(0); backdropIn.setToValue(1);
        ScaleTransition cardIn = new ScaleTransition(Duration.millis(380), card);
        cardIn.setFromX(0.82); cardIn.setFromY(0.82);
        cardIn.setToX(1.0); cardIn.setToY(1.0);
        cardIn.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition cardFade = new FadeTransition(Duration.millis(380), card);
        cardFade.setFromValue(0); cardFade.setToValue(1);

        ParallelTransition entrance = new ParallelTransition(backdropIn, cardIn, cardFade);
        entrance.setOnFinished(ev -> {
            backdrop.requestFocus();
            // Stagger initial bullets in
            bulletBox.getChildren().forEach(n -> { n.setOpacity(0); n.setTranslateY(12); });
            int[] idx = {0};
            bulletBox.getChildren().forEach(n -> {
                int delay = idx[0] * 70;
                FadeTransition bf = new FadeTransition(Duration.millis(210), n);
                bf.setFromValue(0); bf.setToValue(1); bf.setDelay(Duration.millis(delay));
                TranslateTransition bt = new TranslateTransition(Duration.millis(210), n);
                bt.setFromY(12); bt.setToY(0); bt.setDelay(Duration.millis(delay));
                bt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(bf, bt).play();
                idx[0]++;
            });
        });
        entrance.play();
    }

    private static String hexToRgba(String hex, double alpha) {
        hex = hex.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, alpha);
    }
}
