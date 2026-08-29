package com.sibim.util;

import com.sibim.controller.MainController;
import com.sibim.session.SessionManager;
import javafx.animation.*;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.prefs.Preferences;

public final class TutorialOverlay {

    private static final Preferences PREFS = Preferences.userNodeForPackage(TutorialOverlay.class);
    private static final double CARD_W  = 360;
    private static final double ARROW_W = 16;
    // HBox spacing = -2, so card body starts at (ARROW_W - 2) = 14 px from HBox left

    private record Step(
        String icon, String color, String gradEnd,
        String title, String shortcut, String navigateId, String[] bullets
    ) {}

    private static final Step[] STEPS = {
        new Step("mdi2b-book-open-outline", "#6366F1", "#3730A3",
            "Bienvenido a SIBIM", null, null, new String[]{
            "Sistema integral de gestión del patrimonio municipal",
            "Inventario, movimientos, reportes y depreciación en un solo lugar",
            "Funciona en línea, sin conexión y en modo demo"
        }),
        new Step("mdi2v-view-dashboard-outline", "#0EA5E9", "#0369A1",
            "Dashboard", "Ctrl + 1", "dashboard", new String[]{
            "Métricas en tiempo real: total de bienes y valor del inventario",
            "Gráfica de salud del inventario y tendencia mensual de movimientos",
            "Accesos rápidos a los flujos más frecuentes"
        }),
        new Step("mdi2p-package-variant", "#6366F1", "#3730A3",
            "Bienes / Inventario", "Ctrl + 3", "productos", new String[]{
            "Alta, edición y baja de bienes patrimoniales con foto y código",
            "Búsqueda en tiempo real por nombre, código, categoría o área",
            "Doble clic en cualquier bien para ver su ficha completa e historial"
        }),
        new Step("mdi2t-tag-multiple-outline", "#0891B2", "#0E7490",
            "Categorías", "Ctrl + 4", "categorias", new String[]{
            "Organiza los bienes en grupos: vehículos, mobiliario, equipo de cómputo…",
            "Cada categoría tiene ícono y color personalizables",
            "Los filtros de Bienes, Movimientos y Alertas usan estas categorías"
        }),
        new Step("mdi2s-swap-vertical-bold", "#7C3AED", "#5B21B6",
            "Movimientos", "Ctrl + 5", "movimientos", new String[]{
            "Registra entradas, salidas, ajustes y transferencias entre áreas",
            "Las transferencias requieren aprobación del administrador",
            "Exporta el historial a CSV o Excel con los filtros activos"
        }),
        new Step("mdi2b-bell-ring-outline", "#DC2626", "#991B1B",
            "Alertas", "Ctrl + 6", "alertas", new String[]{
            "Notificación automática cuando el stock baja del mínimo definido",
            "Aviso de bienes cuya fecha de vencimiento se aproxima",
            "Badge rojo en el menú cuando hay alertas activas sin resolver"
        }),
        new Step("mdi2f-file-chart-outline", "#059669", "#065F46",
            "Reportes", "Ctrl + 7", "reportes", new String[]{
            "Genera inventario general, movimientos y bienes por área",
            "Exporta a Excel (.xlsx) y PDF con un solo clic",
            "Los reportes respetan los filtros de fecha y categoría activos"
        }),
        new Step("mdi2c-chart-line", "#4338CA", "#3730A3",
            "Depreciación", "Ctrl + 8", "depreciacion", new String[]{
            "Calcula el valor en libros por método de línea recta (SAT México)",
            "Identifica bienes totalmente depreciados — candidatos a baja o reemplazo",
            "Exporta fichas técnicas individuales o en lote a PDF"
        }),
        new Step("mdi2o-office-building-outline", "#2563EB", "#1D4ED8",
            "Organigrama", "Ctrl + 2", "organigrama", new String[]{
            "Visualiza la distribución de bienes por secretaría y dirección",
            "Expande cada área para ver sus bienes asignados",
            "Exporta el organigrama completo a PDF"
        }),
        new Step("mdi2k-keyboard-outline", "#64748B", "#475569",
            "Atajos de Teclado", "F1", null, new String[]{
            "Ctrl+1 a Ctrl+9 navega entre módulos sin el mouse",
            "Ctrl+F busca · F5 actualiza · Ctrl+K abre la paleta de comandos",
            "En tablas: Ctrl+N nuevo · Ctrl+E editar · Supr eliminar"
        }),
        new Step("mdi2c-check-circle-outline", "#16A34A", "#14532D",
            "¡Todo listo!", null, "dashboard", new String[]{
            "Explora cada módulo desde la barra lateral izquierda",
            "Presiona F1 en cualquier momento para ver todos los atajos",
            "El sistema guarda tus cambios aunque pierdas la conexión"
        })
    };

    private TutorialOverlay() {}

    public static void showIfFirstTime(StackPane outerStack) {
        String username = SessionManager.getCurrentUser() != null
            ? SessionManager.getCurrentUser().getUsername() : "unknown";
        String key = "tutorial.v4." + username;
        if (PREFS.getBoolean(key, false)) return;
        PREFS.putBoolean(key, true);
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    public static void show(StackPane outerStack) {
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    private static void buildAndShow(StackPane outerStack) {
        int[]      step      = {0};
        Timeline[] pulseAnim = {null};
        Timeline[] posAnim   = {null};
        boolean[]  firstPos  = {true};

        // ── Size-change listener (handles fullscreen ↔ windowed) ──────────────
        @SuppressWarnings("unchecked")
        ChangeListener<Number>[] sizeListener = new ChangeListener[]{null};

        // ── Layer 1: dim ──────────────────────────────────────────────────────
        Region dimLayer = new Region();
        dimLayer.getStyleClass().add("tutorial-dim-layer");
        dimLayer.setOnMousePressed(javafx.event.Event::consume);
        dimLayer.setOnMouseClicked(javafx.event.Event::consume);

        // ── Layer 2: ring ─────────────────────────────────────────────────────
        Pane ringLayer = new Pane();
        ringLayer.setMouseTransparent(true);
        ringLayer.setPickOnBounds(false);
        StackPane ring = new StackPane();
        ring.setMouseTransparent(true);
        ring.setVisible(false);
        ringLayer.getChildren().add(ring);

        // ── Card header ───────────────────────────────────────────────────────
        StackPane header = new StackPane();
        header.setPrefHeight(96);
        header.setMaxHeight(96);

        Region progressTrack = new Region();
        progressTrack.getStyleClass().add("tutorial-progress-track");
        progressTrack.setPrefHeight(5); progressTrack.setMaxHeight(5);

        Region progressFill = new Region();
        progressFill.getStyleClass().add("tutorial-progress-fill");
        progressFill.setPrefHeight(5); progressFill.setMaxHeight(5);
        progressFill.setPrefWidth(0);

        StackPane progressPane = new StackPane(progressTrack, progressFill);
        progressPane.setAlignment(Pos.CENTER_LEFT);
        progressPane.setPrefHeight(5); progressPane.setMaxHeight(5);
        progressTrack.prefWidthProperty().bind(header.widthProperty());
        StackPane.setAlignment(progressPane, Pos.TOP_LEFT);

        FontIcon icon = new FontIcon();
        icon.setIconSize(28);
        StackPane iconBadge = new StackPane(icon);
        iconBadge.getStyleClass().add("tutorial-icon-badge");
        iconBadge.setPrefSize(64, 64); iconBadge.setMaxSize(64, 64);
        iconBadge.setTranslateY(32);
        StackPane.setAlignment(iconBadge, Pos.BOTTOM_CENTER);
        header.getChildren().addAll(progressPane, iconBadge);

        // ── Slide content ─────────────────────────────────────────────────────
        Label lblTitle = new Label();
        lblTitle.getStyleClass().add("tutorial-title");
        lblTitle.setWrapText(true);
        lblTitle.setMaxWidth(CARD_W - 56);
        lblTitle.setAlignment(Pos.CENTER);

        VBox bulletBox = new VBox(8);
        bulletBox.setAlignment(Pos.CENTER_LEFT);
        bulletBox.setMaxWidth(CARD_W - 56);

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

        VBox slideContent = new VBox(14, lblTitle, bulletBox, shortcutWrap);
        slideContent.setAlignment(Pos.CENTER);
        slideContent.setMaxWidth(CARD_W - 56);

        // ── Dots + counter ────────────────────────────────────────────────────
        HBox dotsRow = new HBox(7);
        dotsRow.setAlignment(Pos.CENTER);
        Circle[] circles = new Circle[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            circles[i] = new Circle(3.5);
            circles[i].getStyleClass().add("tutorial-dot");
            circles[i].setCursor(Cursor.HAND);
            dotsRow.getChildren().add(circles[i]);
        }
        Label lblCounter = new Label();
        lblCounter.getStyleClass().add("tutorial-counter");
        VBox dotsArea = new VBox(5, dotsRow, lblCounter);
        dotsArea.setAlignment(Pos.CENTER);

        // ── Nav buttons ───────────────────────────────────────────────────────
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

        // ── Keyboard hint ─────────────────────────────────────────────────────
        Label kbHint = new Label("← → navegar  ·  Esc cerrar");
        kbHint.getStyleClass().add("tutorial-kb-hint");

        // ── Card body VBox ────────────────────────────────────────────────────
        // top padding = badge overlap (32) + gap (12) = 44
        VBox contentPad = new VBox(14, slideContent, dotsArea, btnRow, kbHint);
        contentPad.setAlignment(Pos.CENTER);
        contentPad.setPadding(new Insets(44, 24, 14, 24));

        VBox card = new VBox(0, header, contentPad);
        card.getStyleClass().add("tutorial-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(CARD_W);
        card.setMaxWidth(CARD_W);

        // ── Arrow caret ───────────────────────────────────────────────────────
        Polygon arrowPoly = new Polygon(0.0, 11.0, ARROW_W, 0.0, ARROW_W, 22.0);
        arrowPoly.setFill(Color.WHITE);
        arrowPoly.setEffect(new DropShadow(10, -4, 0, Color.rgb(15, 23, 42, 0.22)));
        arrowPoly.setVisible(false);

        // HBox spacing=-2: arrow overlaps card by 2px, eliminating any pixel gap
        HBox cardWithArrow = new HBox(-2, arrowPoly, card);
        cardWithArrow.setAlignment(Pos.CENTER_LEFT);

        // ── Layer 3: card container ───────────────────────────────────────────
        Pane cardContainer = new Pane(cardWithArrow);
        cardContainer.setPickOnBounds(false);
        cardContainer.setFocusTraversable(true);

        outerStack.getChildren().addAll(dimLayer, ringLayer, cardContainer);

        // ── Register resize listener (fullscreen ↔ windowed fix) ─────────────
        sizeListener[0] = (obs, ov, nv) -> {
            firstPos[0] = true;   // snap, don't animate on resize
            javafx.application.Platform.runLater(() -> {
                if (posAnim[0] != null) { posAnim[0].stop(); posAnim[0] = null; }
                positionCard(step, firstPos, posAnim, outerStack, ring, arrowPoly,
                             cardWithArrow, card, pulseAnim, STEPS);
            });
        };
        outerStack.widthProperty().addListener(sizeListener[0]);
        outerStack.heightProperty().addListener(sizeListener[0]);

        // ── Dismiss ───────────────────────────────────────────────────────────
        Runnable dismiss = () -> {
            outerStack.widthProperty().removeListener(sizeListener[0]);
            outerStack.heightProperty().removeListener(sizeListener[0]);
            if (pulseAnim[0] != null) { pulseAnim[0].stop(); pulseAnim[0] = null; }
            if (posAnim[0]   != null) { posAnim[0].stop();   posAnim[0]   = null; }
            FadeTransition fadeDim  = new FadeTransition(Duration.millis(220), dimLayer);
            fadeDim.setToValue(0);
            FadeTransition fadeCard = new FadeTransition(Duration.millis(220), cardContainer);
            fadeCard.setToValue(0);
            ScaleTransition shrink  = new ScaleTransition(Duration.millis(220), card);
            shrink.setToX(0.92); shrink.setToY(0.92);
            shrink.setInterpolator(Interpolator.EASE_IN);
            ParallelTransition exit = new ParallelTransition(fadeDim, fadeCard, shrink);
            exit.setOnFinished(e -> outerStack.getChildren().removeAll(dimLayer, ringLayer, cardContainer));
            exit.play();
        };

        // ── positionOverlay (inner) ────────────────────────────────────────────
        Runnable[] positionOverlay = {null};
        positionOverlay[0] = () ->
            positionCard(step, firstPos, posAnim, outerStack, ring, arrowPoly,
                         cardWithArrow, card, pulseAnim, STEPS);

        // ── render ────────────────────────────────────────────────────────────
        Runnable render = () -> {
            Step s = STEPS[step[0]];
            boolean hasNav = s.navigateId() != null;

            // Header: dynamic left radius — squared when arrow is shown
            String hRadius = hasNav ? "0 20 0 0" : "20 20 0 0";
            header.setStyle(
                "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
                + s.color() + ", " + s.gradEnd() + ");"
                + " -fx-background-radius: " + hRadius + ";"
            );

            // Card: left corners square when arrow is shown, fully rounded when centered
            if (hasNav) {
                card.setStyle("-fx-background-radius: 0 20 20 0; -fx-border-radius: 0 20 20 0;");
            } else {
                card.setStyle("");
            }

            icon.setIconLiteral(s.icon());
            icon.setIconColor(Color.web(s.color()));
            iconBadge.setStyle(
                "-fx-border-color: " + hexToRgba(s.color(), 0.28) + ";"
                + " -fx-border-width: 2.5; -fx-border-radius: 38;"
                + " -fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.42) + ", 20, 0.04, 0, 4);"
            );
            lblTitle.setText(s.title());

            bulletBox.getChildren().clear();
            for (String b : s.bullets()) {
                FontIcon chevron = new FontIcon("mdi2c-chevron-right");
                chevron.setIconSize(14);
                chevron.setStyle("-fx-icon-color: " + s.color() + ";");
                Label lbl = new Label(b);
                lbl.getStyleClass().add("tutorial-bullet-text");
                lbl.setWrapText(true);
                lbl.setMaxWidth(CARD_W - 72);
                HBox row = new HBox(8, chevron, lbl);
                row.setAlignment(Pos.TOP_LEFT);
                row.getStyleClass().add("tutorial-bullet-row");
                bulletBox.getChildren().add(row);
            }

            if (s.shortcut() != null) {
                lblShortcut.setText(s.shortcut());
                kbIcon.setStyle("-fx-icon-color: " + s.color() + ";");
                shortcutPill.setStyle(
                    "-fx-background-color: " + hexToRgba(s.color(), 0.09) + ";"
                    + "-fx-border-color: " + hexToRgba(s.color(), 0.30) + ";");
                lblShortcut.setStyle("-fx-text-fill: " + s.color() + ";");
                shortcutWrap.setVisible(true);  shortcutWrap.setManaged(true);
            } else {
                shortcutWrap.setVisible(false); shortcutWrap.setManaged(false);
            }

            for (int i = 0; i < circles.length; i++) {
                circles[i].getStyleClass().removeAll("tutorial-dot-active");
                if (i == step[0]) {
                    circles[i].getStyleClass().add("tutorial-dot-active");
                    circles[i].setStyle("-fx-fill: " + s.color() + ";");
                    circles[i].setRadius(5.0);
                } else {
                    circles[i].setStyle("");
                    circles[i].setRadius(3.5);
                }
            }

            boolean isFirst = step[0] == 0;
            boolean isLast  = step[0] == STEPS.length - 1;
            lblCounter.setText("Paso " + (step[0] + 1) + " de " + STEPS.length);
            btnPrev.setVisible(!isFirst); btnPrev.setManaged(!isFirst);
            btnNext.setText(isLast ? "Comenzar  ✓" : "Siguiente →");
            btnNext.setStyle("-fx-background-color: " + s.color()
                + "; -fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.40) + ", 10, 0, 0, 2);");
            btnSkip.setVisible(!isLast); btnSkip.setManaged(!isLast);

            MainController mc = MainController.getInstance();
            if (hasNav && mc != null) {
                mc.navigateToView(s.navigateId());
                dimLayer.setOpacity(0.22);
            } else {
                dimLayer.setOpacity(0.55);
            }
            javafx.application.Platform.runLater(positionOverlay[0]);
        };

        // ── Animated step transition ──────────────────────────────────────────
        java.util.function.BiConsumer<Integer, Integer> goTo = (newIdx, dir) -> {
            btnNext.setDisable(true); btnPrev.setDisable(true);
            btnSkip.setDisable(true); dotsRow.setMouseTransparent(true);

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(155), slideContent);
            slideOut.setToX(dir * -48.0);
            slideOut.setInterpolator(Interpolator.EASE_IN);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(135), slideContent);
            fadeOut.setToValue(0);
            ParallelTransition exitAnim = new ParallelTransition(slideOut, fadeOut);

            exitAnim.setOnFinished(ev -> {
                step[0] = newIdx;
                render.run();

                double targetW = progressPane.getWidth() * (newIdx + 1.0) / STEPS.length;
                new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(progressFill.prefWidthProperty(), progressFill.getPrefWidth())),
                    new KeyFrame(Duration.millis(270),
                        new KeyValue(progressFill.prefWidthProperty(), targetW, Interpolator.EASE_OUT))
                ).play();

                iconBadge.setScaleX(0.5); iconBadge.setScaleY(0.5);
                new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(iconBadge.scaleXProperty(), 0.5, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 0.5, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(200),
                        new KeyValue(iconBadge.scaleXProperty(), 1.10, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 1.10, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(300),
                        new KeyValue(iconBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(iconBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                ).play();

                slideContent.setTranslateX(dir * 48.0);
                TranslateTransition slideIn = new TranslateTransition(Duration.millis(210), slideContent);
                slideIn.setToX(0); slideIn.setInterpolator(Interpolator.EASE_OUT);
                FadeTransition fadeIn = new FadeTransition(Duration.millis(190), slideContent);
                fadeIn.setFromValue(0); fadeIn.setToValue(1);
                ParallelTransition enterAnim = new ParallelTransition(slideIn, fadeIn);
                enterAnim.setOnFinished(done -> {
                    btnNext.setDisable(false); btnPrev.setDisable(false);
                    btnSkip.setDisable(false); dotsRow.setMouseTransparent(false);
                });
                enterAnim.play();

                bulletBox.getChildren().forEach(n -> { n.setOpacity(0); n.setTranslateY(11); });
                int[] idx = {0};
                bulletBox.getChildren().forEach(n -> {
                    int delay = 75 + idx[0] * 55;
                    FadeTransition bf = new FadeTransition(Duration.millis(195), n);
                    bf.setFromValue(0); bf.setToValue(1); bf.setDelay(Duration.millis(delay));
                    TranslateTransition bt = new TranslateTransition(Duration.millis(195), n);
                    bt.setFromY(11); bt.setToY(0); bt.setDelay(Duration.millis(delay));
                    bt.setInterpolator(Interpolator.EASE_OUT);
                    new ParallelTransition(bf, bt).play();
                    idx[0]++;
                });

                if (shortcutWrap.isVisible()) {
                    shortcutPill.setOpacity(0);
                    shortcutPill.setScaleX(0.65); shortcutPill.setScaleY(0.65);
                    FadeTransition sf = new FadeTransition(Duration.millis(200), shortcutPill);
                    sf.setFromValue(0); sf.setToValue(1); sf.setDelay(Duration.millis(180));
                    ScaleTransition sp = new ScaleTransition(Duration.millis(240), shortcutPill);
                    sp.setFromX(0.65); sp.setFromY(0.65);
                    sp.setToX(1); sp.setToY(1);
                    sp.setInterpolator(Interpolator.EASE_OUT); sp.setDelay(Duration.millis(180));
                    new ParallelTransition(sf, sp).play();
                }
            });
            exitAnim.play();
        };

        for (int i = 0; i < STEPS.length; i++) {
            final int idx = i;
            circles[i].setOnMouseClicked(e -> {
                if (idx != step[0] && !btnNext.isDisabled())
                    goTo.accept(idx, idx > step[0] ? 1 : -1);
            });
        }

        btnNext.setOnAction(e -> {
            if (step[0] < STEPS.length - 1) goTo.accept(step[0] + 1, 1);
            else dismiss.run();
        });
        btnPrev.setOnAction(e -> {
            if (step[0] > 0) goTo.accept(step[0] - 1, -1);
        });
        btnSkip.setOnAction(e -> dismiss.run());

        cardContainer.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case RIGHT -> { if (!btnNext.isDisabled()) btnNext.fire(); }
                case LEFT  -> { if (step[0] > 0 && !btnPrev.isDisabled()) btnPrev.fire(); }
                case ESCAPE -> dismiss.run();
                default -> {}
            }
        });

        // ── Initial render ────────────────────────────────────────────────────
        render.run();
        double targetDimOpacity = dimLayer.getOpacity();

        javafx.application.Platform.runLater(() -> {
            double w = progressPane.getWidth();
            if (w > 0) progressFill.setPrefWidth(w / STEPS.length);
        });

        // Step 0 is centered (no arrow); card body center = outerStack center
        double arrowOffset = ARROW_W - 2; // px before card body in HBox
        cardWithArrow.setLayoutX(Math.max(12, (outerStack.getWidth()  - CARD_W) / 2 - arrowOffset));
        cardWithArrow.setLayoutY(Math.max(12, (outerStack.getHeight() - 340)    / 2));

        // ── Entrance animation ────────────────────────────────────────────────
        dimLayer.setOpacity(0);
        cardContainer.setOpacity(0);
        card.setScaleX(0.86); card.setScaleY(0.86);
        card.setTranslateY(16);

        FadeTransition  dimIn     = new FadeTransition(Duration.millis(280), dimLayer);
        dimIn.setToValue(targetDimOpacity);
        FadeTransition  ctrIn     = new FadeTransition(Duration.millis(280), cardContainer);
        ctrIn.setFromValue(0); ctrIn.setToValue(1);
        ScaleTransition cardScale = new ScaleTransition(Duration.millis(380), card);
        cardScale.setFromX(0.86); cardScale.setFromY(0.86);
        cardScale.setToX(1.0);   cardScale.setToY(1.0);
        cardScale.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition cardRise = new TranslateTransition(Duration.millis(380), card);
        cardRise.setFromY(16); cardRise.setToY(0);
        cardRise.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition  cardFade  = new FadeTransition(Duration.millis(380), card);
        cardFade.setFromValue(0); cardFade.setToValue(1);

        ParallelTransition entrance = new ParallelTransition(dimIn, ctrIn, cardScale, cardRise, cardFade);
        entrance.setOnFinished(ev -> {
            cardContainer.requestFocus();
            bulletBox.getChildren().forEach(n -> { n.setOpacity(0); n.setTranslateY(10); });
            int[] idx = {0};
            bulletBox.getChildren().forEach(n -> {
                int delay = idx[0] * 60;
                FadeTransition bf = new FadeTransition(Duration.millis(195), n);
                bf.setFromValue(0); bf.setToValue(1); bf.setDelay(Duration.millis(delay));
                TranslateTransition bt = new TranslateTransition(Duration.millis(195), n);
                bt.setFromY(10); bt.setToY(0); bt.setDelay(Duration.millis(delay));
                bt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(bf, bt).play();
                idx[0]++;
            });
        });
        entrance.play();
    }

    // ── Card + ring positioning (extracted to allow resize-listener to call it) ──
    private static void positionCard(
            int[] step, boolean[] firstPos, Timeline[] posAnim,
            StackPane outerStack, StackPane ring, Polygon arrowPoly,
            HBox cardWithArrow, VBox card, Timeline[] pulseAnim, Step[] steps) {

        Step s = steps[step[0]];
        if (pulseAnim[0] != null) { pulseAnim[0].stop(); ring.setScaleX(1); ring.setScaleY(1); }

        double stackW = outerStack.getWidth();
        double stackH = outerStack.getHeight();
        // HBox total width: ARROW_W + (-2 spacing) + CARD_W
        double totalW = ARROW_W - 2 + CARD_W;

        MainController mc = MainController.getInstance();
        if (s.navigateId() != null && mc != null) {
            Button navBtn = mc.getNavButton(s.navigateId());
            if (navBtn != null && navBtn.getScene() != null) {
                Bounds bScene = navBtn.localToScene(navBtn.getBoundsInLocal());
                Bounds bStack = outerStack.sceneToLocal(bScene);

                // Ring
                double pad = 5;
                ring.setLayoutX(bStack.getMinX() - pad);
                ring.setLayoutY(bStack.getMinY() - pad);
                ring.setPrefWidth(bStack.getWidth()   + pad * 2);
                ring.setPrefHeight(bStack.getHeight() + pad * 2);
                ring.setStyle(
                    "-fx-border-color: " + s.color() + ";"
                    + "-fx-border-width: 3;"
                    + "-fx-border-radius: 10;"
                    + "-fx-background-color: " + hexToRgba(s.color(), 0.18) + ";"
                    + "-fx-background-radius: 10;"
                    + "-fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.75) + ", 26, 0.12, 0, 0);"
                );
                ring.setVisible(true);

                // Card positioned to the right of nav button
                double cardH   = cardWithArrow.getHeight() > 20 ? cardWithArrow.getHeight() : 360;
                double btnCY   = (bStack.getMinY() + bStack.getMaxY()) / 2;

                double targetX = bStack.getMaxX() + 12;
                double targetY = btnCY - cardH / 2;
                targetY = Math.max(12, Math.min(targetY, stackH - cardH - 12));
                if (targetX + totalW > stackW - 12) targetX = stackW - totalW - 12;

                // Arrow translateY so its tip points at nav button center
                double arrowH      = 22;
                double arrowCenter = cardH / 2;
                double arrowTarget = btnCY - targetY;
                arrowTarget = Math.max(arrowH, Math.min(arrowTarget, cardH - arrowH * 1.2));
                arrowPoly.setTranslateY(arrowTarget - arrowCenter);
                arrowPoly.setVisible(true);

                animateCardTo(firstPos, posAnim, cardWithArrow, targetX, targetY);

                // Pulsing ring
                pulseAnim[0] = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(ring.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(ring.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.millis(900),
                        new KeyValue(ring.scaleXProperty(), 1.10, Interpolator.EASE_BOTH),
                        new KeyValue(ring.scaleYProperty(), 1.10, Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.millis(1800),
                        new KeyValue(ring.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(ring.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                );
                pulseAnim[0].setCycleCount(Timeline.INDEFINITE);
                pulseAnim[0].play();
                return;
            }
        }

        // Intro / outro: center card body on screen, hide ring + arrow
        ring.setVisible(false);
        arrowPoly.setVisible(false);
        double cardH   = cardWithArrow.getHeight() > 20 ? cardWithArrow.getHeight() : 360;
        double arrowOffset = ARROW_W - 2; // px before card body in HBox
        double targetX = Math.max(12, (stackW - CARD_W) / 2 - arrowOffset);
        double targetY = Math.max(12, Math.min((stackH - cardH) / 2, stackH - cardH - 12));
        animateCardTo(firstPos, posAnim, cardWithArrow, targetX, targetY);
    }

    private static void animateCardTo(
            boolean[] firstPos, Timeline[] posAnim,
            HBox cardWithArrow, double targetX, double targetY) {
        if (posAnim[0] != null) posAnim[0].stop();
        if (firstPos[0]) {
            cardWithArrow.setLayoutX(targetX);
            cardWithArrow.setLayoutY(targetY);
            firstPos[0] = false;
        } else {
            posAnim[0] = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(cardWithArrow.layoutXProperty(), cardWithArrow.getLayoutX(), Interpolator.EASE_OUT),
                    new KeyValue(cardWithArrow.layoutYProperty(), cardWithArrow.getLayoutY(), Interpolator.EASE_OUT)),
                new KeyFrame(Duration.millis(300),
                    new KeyValue(cardWithArrow.layoutXProperty(), targetX, Interpolator.EASE_OUT),
                    new KeyValue(cardWithArrow.layoutYProperty(), targetY, Interpolator.EASE_OUT))
            );
            posAnim[0].play();
        }
    }

    private static String hexToRgba(String hex, double alpha) {
        hex = hex.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, alpha);
    }
}
