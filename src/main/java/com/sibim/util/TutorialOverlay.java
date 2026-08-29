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
    private static final double CARD_W  = 370;  // card body width
    private static final double ARROW_W = 14;   // left-pointing arrow width

    private record Step(
        String icon, String color, String gradEnd,
        String title, String shortcut, String navigateId, String[] bullets
    ) {}

    private static final Step[] STEPS = {
        // 0 — Bienvenido
        new Step("mdi2b-book-open-outline", "#6366F1", "#3730A3",
            "Bienvenido a SIBIM", null, null, new String[]{
            "Sistema integral de gestión del patrimonio municipal",
            "Inventario, movimientos, reportes y depreciación en un solo lugar",
            "Funciona en línea, sin conexión y en modo demo"
        }),
        // 1 — Dashboard
        new Step("mdi2v-view-dashboard-outline", "#0EA5E9", "#0369A1",
            "Dashboard", "Ctrl + 1", "dashboard", new String[]{
            "Métricas en tiempo real: total de bienes y valor del inventario",
            "Gráfica de salud del inventario y tendencia mensual de movimientos",
            "Accesos rápidos a los flujos más frecuentes"
        }),
        // 2 — Bienes / Inventario
        new Step("mdi2p-package-variant", "#6366F1", "#3730A3",
            "Bienes / Inventario", "Ctrl + 3", "productos", new String[]{
            "Alta, edición y baja de bienes patrimoniales con foto y código",
            "Búsqueda en tiempo real por nombre, código, categoría o área",
            "Doble clic en cualquier bien para ver su ficha completa e historial"
        }),
        // 3 — Categorías
        new Step("mdi2t-tag-multiple-outline", "#0891B2", "#0E7490",
            "Categorías", "Ctrl + 4", "categorias", new String[]{
            "Organiza los bienes en grupos: vehículos, mobiliario, equipo de cómputo…",
            "Cada categoría tiene ícono y color personalizables",
            "Los filtros de Bienes, Movimientos y Alertas usan estas categorías"
        }),
        // 4 — Movimientos
        new Step("mdi2s-swap-vertical-bold", "#7C3AED", "#5B21B6",
            "Movimientos", "Ctrl + 5", "movimientos", new String[]{
            "Registra entradas, salidas, ajustes y transferencias entre áreas",
            "Las transferencias requieren aprobación del administrador",
            "Exporta el historial a CSV o Excel con los filtros activos"
        }),
        // 5 — Alertas
        new Step("mdi2b-bell-ring-outline", "#DC2626", "#991B1B",
            "Alertas", "Ctrl + 6", "alertas", new String[]{
            "Notificación automática cuando el stock baja del mínimo definido",
            "Aviso de bienes cuya fecha de vencimiento se aproxima",
            "Badge rojo en el menú cuando hay alertas activas sin resolver"
        }),
        // 6 — Reportes
        new Step("mdi2f-file-chart-outline", "#059669", "#065F46",
            "Reportes", "Ctrl + 7", "reportes", new String[]{
            "Genera inventario general, movimientos y bienes por área",
            "Exporta a Excel (.xlsx) y PDF con un solo clic",
            "Los reportes respetan los filtros de fecha y categoría activos"
        }),
        // 7 — Depreciación
        new Step("mdi2c-chart-line", "#4338CA", "#3730A3",
            "Depreciación", "Ctrl + 8", "depreciacion", new String[]{
            "Calcula el valor en libros por método de línea recta (SAT México)",
            "Identifica bienes totalmente depreciados — candidatos a baja o reemplazo",
            "Exporta fichas técnicas individuales o en lote a PDF"
        }),
        // 8 — Organigrama
        new Step("mdi2o-office-building-outline", "#2563EB", "#1D4ED8",
            "Organigrama", "Ctrl + 2", "organigrama", new String[]{
            "Visualiza la distribución de bienes por secretaría y dirección",
            "Expande cada área para ver sus bienes asignados",
            "Exporta el organigrama completo a PDF"
        }),
        // 9 — Atajos
        new Step("mdi2k-keyboard-outline", "#64748B", "#475569",
            "Atajos de Teclado", "F1", null, new String[]{
            "Ctrl+1 a Ctrl+9 navega entre módulos sin el mouse",
            "Ctrl+F busca · F5 actualiza · Ctrl+K abre la paleta de comandos",
            "En tablas: Ctrl+N nuevo · Ctrl+E editar · Supr eliminar"
        }),
        // 10 — ¡Todo listo!
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
        String key = "tutorial.v3." + username;
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

        // ── Layer 1: dim (absorbs mouse, full-screen) ─────────────────────────
        Region dimLayer = new Region();
        dimLayer.getStyleClass().add("tutorial-dim-layer");
        dimLayer.setOnMousePressed(javafx.event.Event::consume);
        dimLayer.setOnMouseClicked(javafx.event.Event::consume);

        // ── Layer 2: ring (mouse-transparent, above dim) ──────────────────────
        Pane ringLayer = new Pane();
        ringLayer.setMouseTransparent(true);
        ringLayer.setPickOnBounds(false);
        StackPane ring = new StackPane();
        ring.setMouseTransparent(true);
        ring.setVisible(false);
        ringLayer.getChildren().add(ring);

        // ── Card header (gradient + progress + icon badge) ────────────────────
        StackPane header = new StackPane();
        header.setPrefHeight(88);
        header.setMaxHeight(88);

        Region progressTrack = new Region();
        progressTrack.getStyleClass().add("tutorial-progress-track");
        progressTrack.setPrefHeight(4); progressTrack.setMaxHeight(4);

        Region progressFill = new Region();
        progressFill.getStyleClass().add("tutorial-progress-fill");
        progressFill.setPrefHeight(4); progressFill.setMaxHeight(4);
        progressFill.setPrefWidth(0);

        StackPane progressPane = new StackPane(progressTrack, progressFill);
        progressPane.setAlignment(Pos.CENTER_LEFT);
        progressPane.setPrefHeight(4); progressPane.setMaxHeight(4);
        progressTrack.prefWidthProperty().bind(header.widthProperty());
        StackPane.setAlignment(progressPane, Pos.TOP_LEFT);

        FontIcon icon = new FontIcon();
        icon.setIconSize(28);
        StackPane iconBadge = new StackPane(icon);
        iconBadge.getStyleClass().add("tutorial-icon-badge");
        iconBadge.setPrefSize(60, 60); iconBadge.setMaxSize(60, 60);
        iconBadge.setTranslateY(30);
        StackPane.setAlignment(iconBadge, Pos.BOTTOM_CENTER);
        header.getChildren().addAll(progressPane, iconBadge);

        // ── Slide content (title + bullets + shortcut) ─────────────────────────
        Label lblTitle = new Label();
        lblTitle.getStyleClass().add("tutorial-title");
        lblTitle.setWrapText(true);
        lblTitle.setMaxWidth(CARD_W - 56);
        lblTitle.setAlignment(Pos.CENTER);

        VBox bulletBox = new VBox(7);
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

        VBox slideContent = new VBox(13, lblTitle, bulletBox, shortcutWrap);
        slideContent.setAlignment(Pos.CENTER);
        slideContent.setMaxWidth(CARD_W - 56);

        // ── Dots + counter ─────────────────────────────────────────────────────
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

        // ── Navigation buttons ─────────────────────────────────────────────────
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

        // ── Card body VBox ─────────────────────────────────────────────────────
        VBox contentPad = new VBox(16, slideContent, dotsArea, btnRow);
        contentPad.setAlignment(Pos.CENTER);
        // top padding = badge overlap (30) + gap (12)
        contentPad.setPadding(new Insets(42, 24, 18, 24));

        VBox card = new VBox(0, header, contentPad);
        card.getStyleClass().add("tutorial-card");
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(CARD_W);
        card.setMaxWidth(CARD_W);

        // ── Left-pointing arrow caret (connects card to nav button) ───────────
        // Polygon: tip=(0,10), top-right=(14,0), bottom-right=(14,20)
        Polygon arrowPoly = new Polygon(0.0, 10.0, ARROW_W, 0.0, ARROW_W, 20.0);
        arrowPoly.setFill(Color.WHITE);
        arrowPoly.setEffect(new DropShadow(6, -3, 0, Color.rgb(15, 23, 42, 0.18)));
        arrowPoly.setVisible(false);

        // HBox wraps [arrowPoly | card] for unified positioning
        HBox cardWithArrow = new HBox(0, arrowPoly, card);
        cardWithArrow.setAlignment(Pos.CENTER_LEFT);

        // ── Layer 3: card container (Pane = absolute coordinates) ─────────────
        Pane cardContainer = new Pane(cardWithArrow);
        cardContainer.setPickOnBounds(false);
        cardContainer.setFocusTraversable(true);

        outerStack.getChildren().addAll(dimLayer, ringLayer, cardContainer);

        // ── Dismiss ────────────────────────────────────────────────────────────
        Runnable dismiss = () -> {
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

        // ── positionOverlay: place ring on nav button + dock card beside it ────
        Runnable[] positionOverlay = {null};
        positionOverlay[0] = () -> {
            Step s = STEPS[step[0]];
            if (pulseAnim[0] != null) { pulseAnim[0].stop(); ring.setScaleX(1); ring.setScaleY(1); }

            MainController mc = MainController.getInstance();
            if (s.navigateId() != null && mc != null) {
                Button navBtn = mc.getNavButton(s.navigateId());
                if (navBtn != null && navBtn.getScene() != null) {
                    Bounds bScene = navBtn.localToScene(navBtn.getBoundsInLocal());
                    Bounds bStack = outerStack.sceneToLocal(bScene);

                    // ── Ring around nav button ────────────────────────────────
                    double pad = 5;
                    ring.setLayoutX(bStack.getMinX() - pad);
                    ring.setLayoutY(bStack.getMinY() - pad);
                    ring.setPrefWidth(bStack.getWidth()  + pad * 2);
                    ring.setPrefHeight(bStack.getHeight() + pad * 2);
                    ring.setStyle(
                        "-fx-border-color: " + s.color() + ";"
                        + "-fx-border-width: 3;"
                        + "-fx-border-radius: 10;"
                        + "-fx-background-color: " + hexToRgba(s.color(), 0.20) + ";"
                        + "-fx-background-radius: 10;"
                        + "-fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.80) + ", 28, 0.12, 0, 0);"
                    );
                    ring.setVisible(true);

                    // ── Card positioned to the right of the nav button ────────
                    double cardH    = cardWithArrow.getHeight() > 20 ? cardWithArrow.getHeight() : 340;
                    double totalW   = CARD_W + ARROW_W;
                    double stackW   = outerStack.getWidth();
                    double stackH   = outerStack.getHeight();
                    double btnCY    = (bStack.getMinY() + bStack.getMaxY()) / 2;

                    double targetX  = bStack.getMaxX() + 12;
                    double targetY  = btnCY - cardH / 2;
                    // Clamp to stay within window
                    targetY = Math.max(12, Math.min(targetY, stackH - cardH - 12));
                    if (targetX + totalW > stackW - 12) targetX = stackW - totalW - 12;

                    // Arrow: translate to point at nav button center
                    double arrowCenter  = cardH / 2;
                    double arrowTarget  = btnCY - targetY;
                    arrowTarget = Math.max(18, Math.min(arrowTarget, cardH - 26));
                    arrowPoly.setTranslateY(arrowTarget - arrowCenter);
                    arrowPoly.setVisible(true);

                    // Animate card to new position (snap on first time)
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

                    // Pulsing ring animation
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

            // Intro / outro steps: center the card, hide ring + arrow
            ring.setVisible(false);
            arrowPoly.setVisible(false);
            double cardH  = cardWithArrow.getHeight() > 20 ? cardWithArrow.getHeight() : 340;
            double totalW = CARD_W + ARROW_W;
            double targetX = (outerStack.getWidth()  - totalW) / 2;
            double targetY = (outerStack.getHeight() - cardH)  / 2;
            targetX = Math.max(12, targetX);
            targetY = Math.max(12, targetY);
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
        };

        // ── render: update card content and trigger overlay positioning ────────
        Runnable render = () -> {
            Step s = STEPS[step[0]];

            header.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
                + s.color() + ", " + s.gradEnd() + ");");
            icon.setIconLiteral(s.icon());
            icon.setIconColor(Color.web(s.color()));
            iconBadge.setStyle("-fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.50) + ", 18, 0.04, 0, 4);");
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
            if (s.navigateId() != null && mc != null) {
                mc.navigateToView(s.navigateId());
                dimLayer.setOpacity(0.22);
            } else {
                dimLayer.setOpacity(0.55);
            }
            javafx.application.Platform.runLater(positionOverlay[0]);
        };

        // ── Animated step transition ───────────────────────────────────────────
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

                // Progress bar
                double targetW = progressPane.getWidth() * (newIdx + 1.0) / STEPS.length;
                new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(progressFill.prefWidthProperty(), progressFill.getPrefWidth())),
                    new KeyFrame(Duration.millis(270),
                        new KeyValue(progressFill.prefWidthProperty(), targetW, Interpolator.EASE_OUT))
                ).play();

                // Icon badge bounce
                iconBadge.setScaleX(0.5); iconBadge.setScaleY(0.5);
                new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(iconBadge.scaleXProperty(), 0.5, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 0.5, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(200),
                        new KeyValue(iconBadge.scaleXProperty(), 1.08, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 1.08, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(290),
                        new KeyValue(iconBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(iconBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                ).play();

                // Slide + fade in new content
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

                // Stagger bullets in
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

                // Shortcut pill pop-in
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

        // ── Dot clicks ─────────────────────────────────────────────────────────
        for (int i = 0; i < STEPS.length; i++) {
            final int idx = i;
            circles[i].setOnMouseClicked(e -> {
                if (idx != step[0] && !btnNext.isDisabled())
                    goTo.accept(idx, idx > step[0] ? 1 : -1);
            });
        }

        // ── Buttons ────────────────────────────────────────────────────────────
        btnNext.setOnAction(e -> {
            if (step[0] < STEPS.length - 1) goTo.accept(step[0] + 1, 1);
            else dismiss.run();
        });
        btnPrev.setOnAction(e -> {
            if (step[0] > 0) goTo.accept(step[0] - 1, -1);
        });
        btnSkip.setOnAction(e -> dismiss.run());

        // ── Keyboard ───────────────────────────────────────────────────────────
        cardContainer.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case RIGHT -> { if (!btnNext.isDisabled()) btnNext.fire(); }
                case LEFT  -> { if (step[0] > 0 && !btnPrev.isDisabled()) btnPrev.fire(); }
                case ESCAPE -> dismiss.run();
                default -> {}
            }
        });

        // ── Initial render ──────────────────────────────────────────────────────
        render.run();
        double targetDimOpacity = dimLayer.getOpacity();

        // Progress bar initial width after first layout
        javafx.application.Platform.runLater(() -> {
            double w = progressPane.getWidth();
            if (w > 0) progressFill.setPrefWidth(w / STEPS.length);
        });

        // Pre-position card centered (step 0 = Bienvenido, no navigateId)
        double initCardH = 340;
        double initTotalW = CARD_W + ARROW_W;
        cardWithArrow.setLayoutX(Math.max(12, (outerStack.getWidth()  - initTotalW) / 2));
        cardWithArrow.setLayoutY(Math.max(12, (outerStack.getHeight() - initCardH)  / 2));

        // ── Entrance animation ──────────────────────────────────────────────────
        dimLayer.setOpacity(0);
        cardContainer.setOpacity(0);
        card.setScaleX(0.84); card.setScaleY(0.84);

        FadeTransition  dimIn     = new FadeTransition(Duration.millis(280), dimLayer);
        dimIn.setToValue(targetDimOpacity);
        FadeTransition  ctrIn     = new FadeTransition(Duration.millis(280), cardContainer);
        ctrIn.setFromValue(0); ctrIn.setToValue(1);
        ScaleTransition cardScale = new ScaleTransition(Duration.millis(360), card);
        cardScale.setFromX(0.84); cardScale.setFromY(0.84);
        cardScale.setToX(1.0);   cardScale.setToY(1.0);
        cardScale.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition  cardFade  = new FadeTransition(Duration.millis(360), card);
        cardFade.setFromValue(0); cardFade.setToValue(1);

        ParallelTransition entrance = new ParallelTransition(dimIn, ctrIn, cardScale, cardFade);
        entrance.setOnFinished(ev -> {
            cardContainer.requestFocus();
            // Stagger initial bullets
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

    private static String hexToRgba(String hex, double alpha) {
        hex = hex.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, alpha);
    }
}
