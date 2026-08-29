package com.sibim.util;

import com.sibim.controller.MainController;
import com.sibim.session.SessionManager;
import javafx.animation.*;
import javafx.geometry.Bounds;
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
        String title, String shortcut, String navigateId, String[] bullets
    ) {}

    private static final Step[] STEPS = {
        new Step("mdi2b-book-open-outline", "#6366F1", "#3730A3",
            "Bienvenido a SIBIM", null, null, new String[]{
            "Sistema integral de gestión del patrimonio municipal",
            "Inventario, movimientos y reportes en un solo lugar",
            "Funciona en línea, sin conexión y en modo demo"
        }),
        new Step("mdi2v-view-dashboard-outline", "#0EA5E9", "#0369A1",
            "Dashboard", "Ctrl + 1", "dashboard", new String[]{
            "Métricas en tiempo real: total de bienes y valor del inventario",
            "Gráfica de salud del inventario (normal / bajo stock / agotado)",
            "Acciones rápidas para los flujos más frecuentes del día"
        }),
        new Step("mdi2p-package-variant", "#6366F1", "#3730A3",
            "Bienes / Inventario", "Ctrl + 3", "productos", new String[]{
            "Búsqueda en tiempo real por nombre, código o área asignada",
            "Doble clic en un bien para ver ficha completa e historial",
            "Altas, bajas y edición desde la misma pantalla"
        }),
        new Step("mdi2s-swap-vertical-bold", "#7C3AED", "#5B21B6",
            "Movimientos", "Ctrl + 5", "movimientos", new String[]{
            "Entradas (adquisiciones), salidas (bajas) y asignaciones entre áreas",
            "Conteo físico para verificar el inventario en campo",
            "Cada movimiento actualiza el stock automáticamente"
        }),
        new Step("mdi2b-bell-ring-outline", "#DC2626", "#991B1B",
            "Alertas", "Ctrl + 6", "alertas", new String[]{
            "Alerta automática cuando un bien llega a su stock mínimo",
            "Aviso de bienes próximos a vencer con período configurable",
            "Badge rojo en el menú lateral cuando hay alertas pendientes"
        }),
        new Step("mdi2f-file-chart-outline", "#059669", "#065F46",
            "Reportes", "Ctrl + 7", "reportes", new String[]{
            "Exporta a Excel (.xlsx) y PDF con un solo clic",
            "Filtra por rango de fechas, área y categoría de bien",
            "Incluye gráficas y resúmenes listos para auditorías"
        }),
        new Step("mdi2k-keyboard-outline", "#0891B2", "#0E7490",
            "Atajos de Teclado", "F1", null, new String[]{
            "Ctrl+1 al Ctrl+9 para navegar entre módulos al instante",
            "Ctrl+F para buscar · F5 para actualizar · F1 para esta ayuda",
            "En tablas: Ctrl+N nuevo · Ctrl+E editar · Supr para eliminar"
        }),
        new Step("mdi2c-check-circle-outline", "#16A34A", "#14532D",
            "¡Todo listo!", null, "dashboard", new String[]{
            "Presiona F1 en cualquier momento para ver todos los atajos",
            "Botón \"Tutorial\" en el menú lateral para volver a este recorrido",
            "El sistema guarda tus cambios aunque pierdas la conexión"
        })
    };

    private TutorialOverlay() {}

    public static void showIfFirstTime(StackPane outerStack) {
        String username = SessionManager.getCurrentUser() != null
            ? SessionManager.getCurrentUser().getUsername() : "unknown";
        String key = "tutorial.v2." + username;
        if (PREFS.getBoolean(key, false)) return;
        PREFS.putBoolean(key, true);
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    public static void show(StackPane outerStack) {
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    private static void buildAndShow(StackPane outerStack) {
        int[] step = {0};
        Timeline[] pulseAnim = {null};

        // ── Layer 1: dim overlay (absorbs mouse so main app is blocked) ─────────
        Region dimLayer = new Region();
        dimLayer.getStyleClass().add("tutorial-dim-layer");
        dimLayer.setOnMousePressed(javafx.event.Event::consume);
        dimLayer.setOnMouseClicked(javafx.event.Event::consume);

        // ── Layer 2: ring layer (mouse-transparent, drawn above dim) ─────────────
        Pane ringLayer = new Pane();
        ringLayer.setMouseTransparent(true);
        ringLayer.setPickOnBounds(false);

        StackPane ring = new StackPane();
        ring.setMouseTransparent(true);
        ring.setVisible(false);
        ringLayer.getChildren().add(ring);

        // ── Gradient header ────────────────────────────────────────────────────
        StackPane header = new StackPane();
        header.setPrefHeight(102);
        header.setMaxHeight(102);

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

        FontIcon icon = new FontIcon();
        icon.setIconSize(34);
        StackPane iconBadge = new StackPane(icon);
        iconBadge.getStyleClass().add("tutorial-icon-badge");
        iconBadge.setPrefSize(76, 76);
        iconBadge.setMaxSize(76, 76);
        iconBadge.setTranslateY(38);
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

        // Nav tip label — appears when a module is being shown
        Label navTipLabel = new Label();
        navTipLabel.getStyleClass().add("tutorial-nav-tip");
        navTipLabel.setVisible(false);
        navTipLabel.setManaged(false);

        VBox slideContent = new VBox(16, lblTitle, bulletBox, shortcutWrap, navTipLabel);
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
        contentArea.setPadding(new Insets(54, 42, 30, 42));

        VBox card = new VBox(0, header, contentArea);
        card.getStyleClass().add("tutorial-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(490);
        card.setMaxWidth(490);

        // ── Layer 3: card layer (transparent background, contains card + keyboard) ─
        StackPane cardLayer = new StackPane(card);
        cardLayer.setStyle("-fx-background-color: transparent;");
        cardLayer.setPickOnBounds(false);
        cardLayer.setFocusTraversable(true);

        // Add all three layers to outerStack
        outerStack.getChildren().addAll(dimLayer, ringLayer, cardLayer);

        // ── Dismiss ────────────────────────────────────────────────────────────
        Runnable dismiss = () -> {
            if (pulseAnim[0] != null) { pulseAnim[0].stop(); pulseAnim[0] = null; }
            FadeTransition fadeDim  = new FadeTransition(Duration.millis(230), dimLayer);
            fadeDim.setToValue(0);
            FadeTransition fadeCard = new FadeTransition(Duration.millis(230), cardLayer);
            fadeCard.setToValue(0);
            ScaleTransition shrink  = new ScaleTransition(Duration.millis(230), card);
            shrink.setToX(0.91); shrink.setToY(0.91);
            shrink.setInterpolator(Interpolator.EASE_IN);
            ParallelTransition exit = new ParallelTransition(fadeDim, fadeCard, shrink);
            exit.setOnFinished(e -> outerStack.getChildren().removeAll(dimLayer, ringLayer, cardLayer));
            exit.play();
        };

        // ── Position ring on nav button ────────────────────────────────────────
        Runnable[] positionRing = {null};
        positionRing[0] = () -> {
            Step s = STEPS[step[0]];
            if (pulseAnim[0] != null) { pulseAnim[0].stop(); ring.setScaleX(1); ring.setScaleY(1); }
            MainController mc = MainController.getInstance();
            if (s.navigateId() != null && mc != null) {
                Button navBtn = mc.getNavButton(s.navigateId());
                if (navBtn != null && navBtn.getScene() != null) {
                    Bounds bScene = navBtn.localToScene(navBtn.getBoundsInLocal());
                    Bounds bStack = outerStack.sceneToLocal(bScene);
                    double pad = 5;
                    ring.setLayoutX(bStack.getMinX() - pad);
                    ring.setLayoutY(bStack.getMinY() - pad);
                    ring.setPrefWidth(bStack.getWidth() + pad * 2);
                    ring.setPrefHeight(bStack.getHeight() + pad * 2);
                    ring.setStyle(
                        "-fx-border-color: " + s.color() + ";"
                        + "-fx-border-width: 3;"
                        + "-fx-border-radius: 10;"
                        + "-fx-background-color: " + hexToRgba(s.color(), 0.18) + ";"
                        + "-fx-background-radius: 10;"
                        + "-fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.75) + ", 22, 0, 0, 0);"
                    );
                    ring.setVisible(true);

                    pulseAnim[0] = new Timeline(
                        new KeyFrame(Duration.ZERO,
                            new KeyValue(ring.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                            new KeyValue(ring.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)),
                        new KeyFrame(Duration.millis(900),
                            new KeyValue(ring.scaleXProperty(), 1.09, Interpolator.EASE_BOTH),
                            new KeyValue(ring.scaleYProperty(), 1.09, Interpolator.EASE_BOTH)),
                        new KeyFrame(Duration.millis(1800),
                            new KeyValue(ring.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                            new KeyValue(ring.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                    );
                    pulseAnim[0].setCycleCount(Timeline.INDEFINITE);
                    pulseAnim[0].play();
                } else {
                    ring.setVisible(false);
                }
            } else {
                ring.setVisible(false);
            }
        };

        // ── Render (instant, no animation) ────────────────────────────────────
        Runnable render = () -> {
            Step s = STEPS[step[0]];

            // Header gradient
            header.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
                + s.color() + ", " + s.gradEnd() + ");");

            // Icon badge
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

            // Nav tip — shown only on module steps
            if (s.navigateId() != null) {
                navTipLabel.setText("↑ Activo en la barra lateral");
                navTipLabel.setStyle("-fx-text-fill: " + hexToRgba(s.color(), 0.75) + ";");
                navTipLabel.setVisible(true);
                navTipLabel.setManaged(true);
            } else {
                navTipLabel.setVisible(false);
                navTipLabel.setManaged(false);
            }

            // Navigate to module and position ring
            MainController mc = MainController.getInstance();
            if (s.navigateId() != null && mc != null) {
                mc.navigateToView(s.navigateId());
                dimLayer.setOpacity(0.62);
            } else {
                dimLayer.setOpacity(0.85);
            }
            javafx.application.Platform.runLater(positionRing[0]);

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

        // ── Animated transition ────────────────────────────────────────────────
        java.util.function.BiConsumer<Integer, Integer> goTo = (newIdx, dir) -> {
            btnNext.setDisable(true);
            btnPrev.setDisable(true);
            btnSkip.setDisable(true);
            dotsRow.setMouseTransparent(true);

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(170), slideContent);
            slideOut.setToX(dir * -55.0);
            slideOut.setInterpolator(Interpolator.EASE_IN);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(150), slideContent);
            fadeOut.setToValue(0);
            ParallelTransition exitAnim = new ParallelTransition(slideOut, fadeOut);

            exitAnim.setOnFinished(ev -> {
                step[0] = newIdx;
                render.run();

                // Progress bar
                double targetW = progressPane.getWidth() * (newIdx + 1.0) / STEPS.length;
                new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(progressFill.prefWidthProperty(), progressFill.getPrefWidth())),
                    new KeyFrame(Duration.millis(290),
                        new KeyValue(progressFill.prefWidthProperty(), targetW, Interpolator.EASE_OUT))
                ).play();

                // Icon badge bounce
                iconBadge.setScaleX(0.5); iconBadge.setScaleY(0.5);
                new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(iconBadge.scaleXProperty(), 0.5, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 0.5, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(220),
                        new KeyValue(iconBadge.scaleXProperty(), 1.08, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 1.08, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(320),
                        new KeyValue(iconBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(iconBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                ).play();

                // Slide + fade in
                slideContent.setTranslateX(dir * 55.0);
                TranslateTransition slideIn = new TranslateTransition(Duration.millis(230), slideContent);
                slideIn.setToX(0);
                slideIn.setInterpolator(Interpolator.EASE_OUT);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(210), slideContent);
                fadeIn.setFromValue(0); fadeIn.setToValue(1);
                ParallelTransition enterAnim = new ParallelTransition(slideIn, fadeIn);
                enterAnim.setOnFinished(done -> {
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
        cardLayer.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case RIGHT -> { if (!btnNext.isDisabled()) btnNext.fire(); }
                case LEFT  -> { if (step[0] > 0 && !btnPrev.isDisabled()) btnPrev.fire(); }
                case ESCAPE -> dismiss.run();
                default -> {}
            }
        });

        // ── Initial render ─────────────────────────────────────────────────────
        render.run();
        double targetDimOpacity = dimLayer.getOpacity(); // captured from render()

        // Progress bar initial width (needs layout pass)
        javafx.application.Platform.runLater(() -> {
            double w = progressPane.getWidth();
            if (w > 0) progressFill.setPrefWidth(w / STEPS.length);
        });

        // ── Entrance animation ─────────────────────────────────────────────────
        dimLayer.setOpacity(0);
        cardLayer.setOpacity(0);
        card.setScaleX(0.82); card.setScaleY(0.82);

        FadeTransition dimIn = new FadeTransition(Duration.millis(280), dimLayer);
        dimIn.setToValue(targetDimOpacity);
        FadeTransition cardLayerIn = new FadeTransition(Duration.millis(280), cardLayer);
        cardLayerIn.setFromValue(0); cardLayerIn.setToValue(1);
        ScaleTransition cardIn = new ScaleTransition(Duration.millis(380), card);
        cardIn.setFromX(0.82); cardIn.setFromY(0.82);
        cardIn.setToX(1.0); cardIn.setToY(1.0);
        cardIn.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition cardFade = new FadeTransition(Duration.millis(380), card);
        cardFade.setFromValue(0); cardFade.setToValue(1);

        ParallelTransition entrance = new ParallelTransition(dimIn, cardLayerIn, cardIn, cardFade);
        entrance.setOnFinished(ev -> {
            cardLayer.requestFocus();
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
