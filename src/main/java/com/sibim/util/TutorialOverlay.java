package com.sibim.util;

import com.sibim.controller.MainController;
import com.sibim.model.Usuario;
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

import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

/** Shows the guides of {@link TutorialGuias}: a menu of what can be learned, and each
 *  guide over the real screens, marking the exact button and able to open the window. */
public final class TutorialOverlay {

    private static final Preferences PREFS = Preferences.userNodeForPackage(TutorialOverlay.class);
    private static final double CARD_W  = 420;
    private static final double ARROW_W = 16;
    // HBox spacing = -2, so card body starts at (ARROW_W - 2) = 14 px from HBox left

    private record Step(
        String icon, String color, String gradEnd,
        String title, String shortcut, String navigateId, String foco,
        String accionTexto, Runnable accion, String[] bullets
    ) {
        Step(TutorialGuias.Paso p) {
            this(p.icon(), p.color(), p.gradEnd(), p.title(), p.shortcut(), p.navigateId(), p.foco(),
                 p.accionTexto(), p.accion(), p.bullets());
        }
    }

    /** F2 with the tutorial already open used to stack a second one on top of it. */
    private static boolean abierto = false;

    private TutorialOverlay() {}

    private static String usuario() {
        return SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getUsername() : "unknown";
    }

    private static String claveVista(String guia) {
        return "guia.vista." + guia + "." + usuario();
    }

    /** On signing in: the guides the first time; once per version, what changed. */
    public static void showIfFirstTime(StackPane outerStack) {
        String key = "tutorial.v6." + usuario();
        String keyVersion = "novedades." + UpdateChecker.currentVersion() + "." + usuario();
        if (!PREFS.getBoolean(key, false)) {
            PREFS.putBoolean(key, true);
            // Somebody new doesn't need what changed: all of it is new.
            PREFS.putBoolean(keyVersion, true);
            javafx.application.Platform.runLater(() -> mostrarMenu(outerStack, true));
            return;
        }
        // sibim.test is only ever set by surefire: an overlay would sit on the screens under test.
        if (Boolean.getBoolean("sibim.test") || PREFS.getBoolean(keyVersion, false)) return;
        PREFS.putBoolean(keyVersion, true);
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack, TutorialGuias.novedades()));
    }

    /** F2 and the account menu: what can be learned. */
    public static void show(StackPane outerStack) {
        javafx.application.Platform.runLater(() -> mostrarMenu(outerStack, false));
    }

    // ── El menú de guías ──────────────────────────────────────────────────────

    static void mostrarMenu(StackPane outerStack, boolean bienvenida) {
        if (abierto) return;
        abierto = true;

        Region dim = new Region();
        dim.getStyleClass().add("tutorial-dim-layer");
        dim.setOpacity(0.55);
        dim.setOnMousePressed(javafx.event.Event::consume);
        dim.setOnMouseClicked(javafx.event.Event::consume);

        Usuario user = SessionManager.getCurrentUser();
        String primerNombre = user != null && user.getNombre() != null && !user.getNombre().isBlank()
            ? user.getNombre().split("\\s+")[0] : null;
        HBox header = DialogUtil.gradientHeader("mdi2b-book-open-outline",
            bienvenida ? (primerNombre != null ? "¡Bienvenido, " + primerNombre + "!" : "Bienvenido a SIBIM") : "Guías de SIBIM",
            bienvenida ? "¿Por dónde empezamos? Cada guía te lleva a la pantalla y te deja probarlo"
                       : "Elige qué quieres hacer: te lleva a la pantalla, marca el botón y te deja probarlo",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        VBox lista = new VBox(8);
        lista.setPadding(new Insets(2, 6, 2, 2));
        Runnable[] cerrar = {null};
        java.util.function.Consumer<TutorialGuias.Guia> abrirGuia = g -> {
            cerrar[0].run();
            javafx.application.Platform.runLater(() -> buildAndShow(outerStack, g));
        };
        lista.getChildren().add(filaGuia(TutorialGuias.novedades(), true, abrirGuia));
        for (TutorialGuias.Guia g : TutorialGuias.disponibles()) lista.getChildren().add(filaGuia(g, false, abrirGuia));

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button pdf = new Button("Guía para imprimir (PDF)");
        pdf.setGraphic(new FontIcon("mdi2b-book-open-page-variant-outline"));
        pdf.getStyleClass().add("btn-secondary");
        pdf.setOnAction(e -> {
            javafx.scene.Scene scene = outerStack.getScene();
            cerrar[0].run();
            DialogUtil.runAsyncWithProgress(scene, "Generando la guía…",
                () -> new com.sibim.service.ReporteGuiaRapidaService().exportGuia(),
                archivo -> DialogUtil.showExportResultDialog(scene, archivo),
                ex -> NotificacionUtil.error(scene, "No se pudo generar la guía"));
        });
        Button salir = new Button(bienvenida ? "Ahora no" : "Cerrar");
        salir.getStyleClass().add("btn-secondary");
        salir.setOnAction(e -> cerrar[0].run());
        Label nota = new Label("F2 abre estas guías cuando quieras");
        nota.getStyleClass().add("muted-sm");
        nota.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nota, Priority.ALWAYS);
        HBox pie = new HBox(10, nota, pdf, salir);
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.setPadding(new Insets(10, 18, 14, 18));

        VBox cuerpo = new VBox(scroll);
        cuerpo.setPadding(new Insets(0, 14, 0, 18));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox card = new VBox(12, header, cuerpo, pie);
        card.getStyleClass().add("tutorial-menu");
        card.setMaxSize(640, 620);
        card.setPrefWidth(640);
        StackPane capa = new StackPane(card);
        capa.setPickOnBounds(false);
        capa.setPadding(new Insets(16));
        StackPane.setAlignment(card, Pos.CENTER);

        javafx.scene.Scene scene = outerStack.getScene();
        javafx.event.EventHandler<javafx.scene.input.KeyEvent> teclas = e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) { cerrar[0].run(); e.consume(); }
        };
        if (scene != null) scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, teclas);
        cerrar[0] = () -> {
            if (scene != null) scene.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, teclas);
            outerStack.getChildren().removeAll(dim, capa);
            abierto = false;
        };
        outerStack.getChildren().addAll(dim, capa);
        if (!lista.getChildren().isEmpty()) javafx.application.Platform.runLater(lista.getChildren().get(0)::requestFocus);
    }

    private static HBox filaGuia(TutorialGuias.Guia g, boolean destacada, java.util.function.Consumer<TutorialGuias.Guia> abrir) {
        FontIcon icono = new FontIcon(g.icon());
        icono.setIconSize(18);
        icono.setIconColor(destacada ? Color.WHITE : Color.web("#A2232D"));
        StackPane fondo = new StackPane(icono);
        fondo.getStyleClass().add(destacada ? "tutorial-guia-icono-destacada" : "tutorial-guia-icono");
        fondo.setMinSize(38, 38); fondo.setPrefSize(38, 38); fondo.setMaxSize(38, 38);
        Label titulo = new Label(g.titulo());
        titulo.getStyleClass().add("tutorial-guia-titulo");
        Label desc = new Label(g.descripcion());
        desc.getStyleClass().add("tutorial-guia-desc");
        desc.setWrapText(true);
        VBox textos = new VBox(2, titulo, desc);
        textos.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textos, Priority.ALWAYS);
        boolean vista = PREFS.getBoolean(claveVista(g.id()), false);
        Label estado = new Label(vista ? "✓ Vista" : g.pasos().size() == 1 ? "1 paso" : g.pasos().size() + " pasos");
        estado.getStyleClass().add(vista ? "tutorial-guia-vista" : "tutorial-guia-pasos");
        estado.setMinWidth(Region.USE_PREF_SIZE);
        HBox fila = new HBox(12, fondo, textos, estado);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.getStyleClass().add("tutorial-guia");
        if (destacada) fila.getStyleClass().add("tutorial-guia-destacada");
        fila.setOnMouseClicked(e -> abrir.accept(g));
        AccessibilityUtils.asButton(fila, g.titulo());
        fila.setUserData(g.id());
        return fila;
    }

    // ── Una guía ──────────────────────────────────────────────────────────────

    static void buildAndShow(StackPane outerStack, TutorialGuias.Guia guia) {
        if (abierto) return;
        abierto = true;
        final Step[] STEPS = guia.pasos().stream().map(Step::new).toArray(Step[]::new);
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

        // ── Card header: icon, step, title and shortcut on the step's color ──
        // (It was a tall band with a white circle hanging off its bottom edge,
        // the title below it and twenty dots underneath: everything centered,
        // the step's name far from its number.)
        StackPane header = new StackPane();

        FontIcon icon = new FontIcon();
        icon.setIconSize(24);
        icon.setIconColor(Color.WHITE);
        StackPane iconBadge = new StackPane(icon);
        iconBadge.getStyleClass().add("tutorial-icon-badge");
        iconBadge.setMinSize(50, 50); iconBadge.setPrefSize(50, 50); iconBadge.setMaxSize(50, 50);

        Label lblCounter = new Label();
        lblCounter.getStyleClass().add("tutorial-counter");
        Label lblTitle = new Label();
        lblTitle.getStyleClass().add("tutorial-title");
        lblTitle.setWrapText(true);

        FontIcon kbIcon = new FontIcon("mdi2k-keyboard-outline");
        kbIcon.setIconSize(12);
        kbIcon.setIconColor(Color.WHITE);
        Label lblShortcut = new Label();
        lblShortcut.getStyleClass().add("tutorial-shortcut-text");
        HBox shortcutPill = new HBox(6, kbIcon, lblShortcut);
        shortcutPill.getStyleClass().add("tutorial-shortcut-pill");
        shortcutPill.setAlignment(Pos.CENTER_LEFT);
        shortcutPill.setMaxWidth(Region.USE_PREF_SIZE);
        StackPane shortcutWrap = new StackPane(shortcutPill);
        shortcutWrap.setAlignment(Pos.CENTER_LEFT);

        VBox titulos = new VBox(3, lblCounter, lblTitle, shortcutWrap);
        titulos.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titulos, Priority.ALWAYS);
        HBox headerContent = new HBox(14, iconBadge, titulos);
        headerContent.setAlignment(Pos.CENTER_LEFT);
        headerContent.setPadding(new Insets(18, 52, 16, 20));

        Button btnClose = new Button("×");
        btnClose.getStyleClass().add("tutorial-close-btn");
        btnClose.setFocusTraversable(false);
        btnClose.setAccessibleText("Cerrar tutorial");
        StackPane.setAlignment(btnClose, Pos.TOP_RIGHT);
        StackPane.setMargin(btnClose, new Insets(10, 10, 0, 0));

        header.getChildren().addAll(headerContent, btnClose);

        // ── Progress: one thin bar under the header, instead of twenty dots ──
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
        progressTrack.prefWidthProperty().bind(progressPane.widthProperty());

        // ── Slide content: the step's points, numbered ──
        VBox bulletBox = new VBox(10);
        bulletBox.setAlignment(Pos.CENTER_LEFT);
        bulletBox.setMaxWidth(CARD_W - 44);

        VBox slideContent = new VBox(0, bulletBox);
        slideContent.setAlignment(Pos.CENTER_LEFT);
        slideContent.setMaxWidth(CARD_W - 44);

        // ── Nav buttons ───────────────────────────────────────────────────────
        Button btnPrev = new Button("← Anterior");
        btnPrev.getStyleClass().add("tutorial-btn-secondary");
        btnPrev.setAccessibleText("Paso anterior");
        Button btnNext = new Button("Siguiente →");
        btnNext.getStyleClass().add("tutorial-btn-primary");
        btnNext.setAccessibleText("Siguiente paso");
        Button btnSkip = new Button("Ver las guías");
        btnSkip.getStyleClass().add("tutorial-btn-skip");
        btnSkip.setAccessibleText("Volver a la lista de guías");
        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox btnRow = new HBox(8, btnSkip, btnSpacer, btnPrev, btnNext);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        // ── Keyboard hint ─────────────────────────────────────────────────────
        Label kbHint = new Label("← → para avanzar o regresar  ·  Esc para cerrar");
        kbHint.getStyleClass().add("tutorial-kb-hint");

        // ── "Probarlo ahora": opens the real window ───────────────────────────
        Button btnProbar = new Button();
        btnProbar.setGraphic(new FontIcon("mdi2p-play-circle-outline"));
        btnProbar.getStyleClass().add("tutorial-btn-probar");
        btnProbar.setMaxWidth(Double.MAX_VALUE);

        // ── Card body VBox ────────────────────────────────────────────────────
        VBox contentPad = new VBox(16, slideContent, btnProbar, btnRow, kbHint);
        contentPad.setAlignment(Pos.CENTER);
        contentPad.setPadding(new Insets(18, 22, 14, 22));

        VBox card = new VBox(0, header, progressPane, contentPad);
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
                             cardWithArrow, card, header, pulseAnim, STEPS);
            });
        };
        outerStack.widthProperty().addListener(sizeListener[0]);
        outerStack.heightProperty().addListener(sizeListener[0]);

        // ── Keys: on the scene, because going to a screen takes the focus off the card ──
        // (and the arrows stopped working after the first step that changed screen).
        javafx.scene.Scene escena = outerStack.getScene();
        @SuppressWarnings("unchecked")
        javafx.event.EventHandler<javafx.scene.input.KeyEvent>[] teclas = new javafx.event.EventHandler[]{null};

        // ── Dismiss ───────────────────────────────────────────────────────────
        java.util.function.Consumer<Runnable> dismiss = despues -> {
            if (escena != null && teclas[0] != null) escena.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, teclas[0]);
            outerStack.widthProperty().removeListener(sizeListener[0]);
            outerStack.heightProperty().removeListener(sizeListener[0]);
            if (pulseAnim[0] != null) { pulseAnim[0].stop(); pulseAnim[0] = null; }
            if (posAnim[0]   != null) { posAnim[0].stop();   posAnim[0]   = null; }
            FadeTransition fadeDim  = new FadeTransition(AnimationUtils.duracion(220), dimLayer);
            fadeDim.setToValue(0);
            FadeTransition fadeCard = new FadeTransition(AnimationUtils.duracion(220), cardContainer);
            fadeCard.setToValue(0);
            ScaleTransition shrink  = new ScaleTransition(AnimationUtils.duracion(220), card);
            shrink.setToX(0.92); shrink.setToY(0.92);
            shrink.setInterpolator(Interpolator.EASE_IN);
            ParallelTransition exit = new ParallelTransition(fadeDim, fadeCard, shrink);
            exit.setOnFinished(e -> {
                outerStack.getChildren().removeAll(dimLayer, ringLayer, cardContainer);
                abierto = false;
                if (despues != null) despues.run();
            });
            exit.play();
        };

        // ── positionOverlay (inner) ────────────────────────────────────────────
        Runnable[] positionOverlay = {null};
        positionOverlay[0] = () ->
            positionCard(step, firstPos, posAnim, outerStack, ring, arrowPoly,
                         cardWithArrow, card, header, pulseAnim, STEPS);

        // ── render ────────────────────────────────────────────────────────────
        Runnable render = () -> {
            Step s = STEPS[step[0]];
            boolean hasNav = s.navigateId() != null;
            boolean marca = hasNav || s.foco() != null;

            pintarEncabezado(header, card, s, false);

            icon.setIconLiteral(s.icon());
            lblTitle.setText(s.title());

            // Numbered points: each one its own line of reading.
            bulletBox.getChildren().clear();
            int n = 1;
            for (String b : s.bullets()) {
                Label num = new Label(String.valueOf(n++));
                num.getStyleClass().add("tutorial-bullet-num");
                num.setStyle("-fx-background-color: " + hexToRgba(s.color(), 0.12) + "; -fx-text-fill: " + s.color() + ";");
                num.setMinSize(22, 22); num.setPrefSize(22, 22); num.setMaxSize(22, 22);
                Label lbl = new Label(b);
                lbl.getStyleClass().add("tutorial-bullet-text");
                lbl.setWrapText(true);
                lbl.setMaxWidth(CARD_W - 84);
                HBox row = new HBox(10, num, lbl);
                row.setAlignment(Pos.TOP_LEFT);
                row.getStyleClass().add("tutorial-bullet-row");
                bulletBox.getChildren().add(row);
            }

            if (s.shortcut() != null) {
                lblShortcut.setText(s.shortcut());
                shortcutWrap.setVisible(true);  shortcutWrap.setManaged(true);
            } else {
                shortcutWrap.setVisible(false); shortcutWrap.setManaged(false);
            }

            progressFill.setStyle("-fx-background-color: " + s.color() + ";");

            boolean isFirst = step[0] == 0;
            boolean isLast  = step[0] == STEPS.length - 1;
            lblCounter.setText("PASO " + (step[0] + 1) + " DE " + STEPS.length);
            btnPrev.setVisible(!isFirst); btnPrev.setManaged(!isFirst);
            btnNext.setText(isLast ? "Terminar  ✓" : "Siguiente →");
            btnNext.setAccessibleText(isLast ? "Terminar esta guía" : "Siguiente paso");
            boolean probar = s.accion() != null;
            btnProbar.setVisible(probar); btnProbar.setManaged(probar);
            if (probar) {
                btnProbar.setText("Probarlo ahora: " + s.accionTexto());
                btnProbar.setStyle("-fx-border-color: " + s.color() + "; -fx-text-fill: " + s.color() + ";");
                if (btnProbar.getGraphic() instanceof FontIcon fi) fi.setIconColor(Color.web(s.color()));
            }
            btnNext.setStyle("-fx-background-color: " + s.color()
                + "; -fx-effect: dropshadow(gaussian, " + hexToRgba(s.color(), 0.40) + ", 10, 0, 0, 2);");

            MainController mc = MainController.getInstance();
            if (hasNav && mc != null) {
                mc.navigateToView(s.navigateId());
            }
            dimLayer.setOpacity(marca && mc != null ? 0.22 : 0.55);
            javafx.application.Platform.runLater(positionOverlay[0]);
            // Once more when the screen just opened has been laid out: until then what is
            // to be marked measures nothing.
            javafx.animation.PauseTransition otraVez = new javafx.animation.PauseTransition(Duration.millis(320));
            otraVez.setOnFinished(ev -> { if (abierto) positionOverlay[0].run(); });
            otraVez.play();
        };

        // ── Animated step transition ──────────────────────────────────────────
        BiConsumer<Integer, Integer> goTo = (newIdx, dir) -> {
            btnNext.setDisable(true); btnPrev.setDisable(true);
            btnSkip.setDisable(true);

            TranslateTransition slideOut = new TranslateTransition(AnimationUtils.duracion(155), slideContent);
            slideOut.setToX(dir * -48.0);
            slideOut.setInterpolator(Interpolator.EASE_IN);
            FadeTransition fadeOut = new FadeTransition(AnimationUtils.duracion(135), slideContent);
            fadeOut.setToValue(0);
            ParallelTransition exitAnim = new ParallelTransition(slideOut, fadeOut);

            exitAnim.setOnFinished(ev -> {
                step[0] = newIdx;
                render.run();

                double targetW = progressPane.getWidth() * (newIdx + 1.0) / STEPS.length;
                AnimationUtils.reproducir(new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(progressFill.prefWidthProperty(), progressFill.getPrefWidth())),
                    new KeyFrame(Duration.millis(270),
                        new KeyValue(progressFill.prefWidthProperty(), targetW, Interpolator.EASE_OUT))
                ));

                iconBadge.setScaleX(0.5); iconBadge.setScaleY(0.5);
                AnimationUtils.reproducir(new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(iconBadge.scaleXProperty(), 0.5, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 0.5, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(200),
                        new KeyValue(iconBadge.scaleXProperty(), 1.10, Interpolator.EASE_OUT),
                        new KeyValue(iconBadge.scaleYProperty(), 1.10, Interpolator.EASE_OUT)),
                    new KeyFrame(Duration.millis(300),
                        new KeyValue(iconBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                        new KeyValue(iconBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
                ));

                slideContent.setTranslateX(dir * 48.0);
                TranslateTransition slideIn = new TranslateTransition(AnimationUtils.duracion(210), slideContent);
                slideIn.setToX(0); slideIn.setInterpolator(Interpolator.EASE_OUT);
                FadeTransition fadeIn = new FadeTransition(AnimationUtils.duracion(190), slideContent);
                fadeIn.setFromValue(0); fadeIn.setToValue(1);
                ParallelTransition enterAnim = new ParallelTransition(slideIn, fadeIn);
                enterAnim.setOnFinished(done -> {
                    btnNext.setDisable(false); btnPrev.setDisable(false);
                    btnSkip.setDisable(false);
                });
                enterAnim.play();

                bulletBox.getChildren().forEach(n -> { n.setOpacity(0); n.setTranslateY(11); });
                int[] idx = {0};
                bulletBox.getChildren().forEach(n -> {
                    int delay = 75 + idx[0] * 55;
                    FadeTransition bf = new FadeTransition(AnimationUtils.duracion(195), n);
                    bf.setFromValue(0); bf.setToValue(1); bf.setDelay(Duration.millis(delay));
                    TranslateTransition bt = new TranslateTransition(AnimationUtils.duracion(195), n);
                    bt.setFromY(11); bt.setToY(0); bt.setDelay(Duration.millis(delay));
                    bt.setInterpolator(Interpolator.EASE_OUT);
                    new ParallelTransition(bf, bt).play();
                    idx[0]++;
                });

                if (shortcutWrap.isVisible()) {
                    shortcutPill.setOpacity(0);
                    shortcutPill.setScaleX(0.65); shortcutPill.setScaleY(0.65);
                    FadeTransition sf = new FadeTransition(AnimationUtils.duracion(200), shortcutPill);
                    sf.setFromValue(0); sf.setToValue(1); sf.setDelay(Duration.millis(180));
                    ScaleTransition sp = new ScaleTransition(AnimationUtils.duracion(240), shortcutPill);
                    sp.setFromX(0.65); sp.setFromY(0.65);
                    sp.setToX(1); sp.setToY(1);
                    sp.setInterpolator(Interpolator.EASE_OUT); sp.setDelay(Duration.millis(180));
                    new ParallelTransition(sf, sp).play();
                }
            });
            exitAnim.play();
        };

        Runnable alMenu = () -> mostrarMenu(outerStack, false);
        btnClose.setOnAction(e -> dismiss.accept(null));

        btnNext.setOnAction(e -> {
            if (step[0] < STEPS.length - 1) goTo.accept(step[0] + 1, 1);
            else {
                PREFS.putBoolean(claveVista(guia.id()), true);
                dismiss.accept(alMenu);
            }
        });
        // Seen too: whoever goes to try it has got what the guide had to say.
        btnProbar.setOnAction(e -> {
            Runnable accion = STEPS[step[0]].accion();
            PREFS.putBoolean(claveVista(guia.id()), true);
            dismiss.accept(accion);
        });
        btnPrev.setOnAction(e -> {
            if (step[0] > 0) goTo.accept(step[0] - 1, -1);
        });
        btnSkip.setOnAction(e -> dismiss.accept(alMenu));

        teclas[0] = e -> {
            switch (e.getCode()) {
                case RIGHT -> { if (!btnNext.isDisabled()) btnNext.fire(); e.consume(); }
                case LEFT  -> { if (step[0] > 0 && !btnPrev.isDisabled()) btnPrev.fire(); e.consume(); }
                case ESCAPE -> { dismiss.accept(null); e.consume(); }
                default -> {}
            }
        };
        if (escena != null) escena.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, teclas[0]);

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

        FadeTransition  dimIn     = new FadeTransition(AnimationUtils.duracion(280), dimLayer);
        dimIn.setToValue(targetDimOpacity);
        FadeTransition  ctrIn     = new FadeTransition(AnimationUtils.duracion(280), cardContainer);
        ctrIn.setFromValue(0); ctrIn.setToValue(1);
        ScaleTransition cardScale = new ScaleTransition(AnimationUtils.duracion(380), card);
        cardScale.setFromX(0.86); cardScale.setFromY(0.86);
        cardScale.setToX(1.0);   cardScale.setToY(1.0);
        cardScale.setInterpolator(Interpolator.EASE_OUT);
        TranslateTransition cardRise = new TranslateTransition(AnimationUtils.duracion(380), card);
        cardRise.setFromY(16); cardRise.setToY(0);
        cardRise.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition  cardFade  = new FadeTransition(AnimationUtils.duracion(380), card);
        cardFade.setFromValue(0); cardFade.setToValue(1);

        ParallelTransition entrance = new ParallelTransition(dimIn, ctrIn, cardScale, cardRise, cardFade);
        entrance.setOnFinished(ev -> {
            cardContainer.requestFocus();
            bulletBox.getChildren().forEach(n -> { n.setOpacity(0); n.setTranslateY(10); });
            int[] idx = {0};
            bulletBox.getChildren().forEach(n -> {
                int delay = idx[0] * 60;
                FadeTransition bf = new FadeTransition(AnimationUtils.duracion(195), n);
                bf.setFromValue(0); bf.setToValue(1); bf.setDelay(Duration.millis(delay));
                TranslateTransition bt = new TranslateTransition(AnimationUtils.duracion(195), n);
                bt.setFromY(10); bt.setToY(0); bt.setDelay(Duration.millis(delay));
                bt.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(bf, bt).play();
                idx[0]++;
            });
        });
        entrance.play();
    }

    /** The arrow takes the card's left corners when it is showing. */
    private static void pintarEncabezado(StackPane header, VBox card, Step s, boolean conFlecha) {
        header.setStyle(
            "-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, "
            + s.color() + ", " + s.gradEnd() + ");"
            + " -fx-background-radius: " + (conFlecha ? "0 20 0 0" : "20 20 0 0") + ";"
        );
        card.getStyleClass().remove("tutorial-card-with-arrow");
        if (conFlecha) card.getStyleClass().add("tutorial-card-with-arrow");
    }

    /** What the step marks: its button on the screen, a button of the side menu, or the
     *  screen's own button of the menu when that one isn't there (a narrow window hides some). */
    private static javafx.scene.Node objetivo(Step s, StackPane outerStack) {
        MainController mc = MainController.getInstance();
        if (mc == null) return null;
        javafx.scene.Node n = null;
        if (s.foco() != null) {
            if (s.foco().startsWith("nav:")) n = mc.getNavButton(s.foco().substring(4));
            else if (outerStack.getScene() != null) n = outerStack.getScene().getRoot().lookup(s.foco());
        }
        if (!seVe(n) && s.navigateId() != null) n = mc.getNavButton(s.navigateId());
        return seVe(n) ? n : null;
    }

    private static boolean seVe(javafx.scene.Node n) {
        if (n == null || n.getScene() == null) return false;
        for (javafx.scene.Node p = n; p != null; p = p.getParent()) if (!p.isVisible()) return false;
        Bounds b = n.localToScene(n.getBoundsInLocal());
        return b.getWidth() > 0 && b.getHeight() > 0;
    }

    // ── Card + ring positioning (extracted to allow resize-listener to call it) ──
    private static void positionCard(
            int[] step, boolean[] firstPos, Timeline[] posAnim,
            StackPane outerStack, StackPane ring, Polygon arrowPoly,
            HBox cardWithArrow, VBox card, StackPane header, Timeline[] pulseAnim, Step[] steps) {

        Step s = steps[step[0]];
        if (pulseAnim[0] != null) { pulseAnim[0].stop(); ring.setScaleX(1); ring.setScaleY(1); }

        double stackW = outerStack.getWidth();
        double stackH = outerStack.getHeight();
        // HBox total width: ARROW_W + (-2 spacing) + CARD_W
        double totalW = ARROW_W - 2 + CARD_W;

        javafx.scene.Node marcado = objetivo(s, outerStack);
        if (marcado != null) {
            Bounds bScene = marcado.localToScene(marcado.getBoundsInLocal());
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

            double cardH = cardWithArrow.getHeight() > 20 ? cardWithArrow.getHeight() : 360;
            double btnCY = (bStack.getMinY() + bStack.getMaxY()) / 2;
            double targetX, targetY;
            boolean conFlecha = false;
            if (bStack.getMaxX() + 12 + totalW <= stackW - 12) {
                // Beside it, the arrow pointing at it: the buttons of the side menu.
                conFlecha = true;
                targetX = bStack.getMaxX() + 12;
                targetY = Math.max(12, Math.min(btnCY - cardH / 2, stackH - cardH - 12));
                double arrowH = 22;
                double arrowTarget = Math.max(arrowH, Math.min(btnCY - targetY, cardH - arrowH * 1.2));
                arrowPoly.setTranslateY(arrowTarget - cardH / 2);
            } else if (bStack.getWidth() > stackW * 0.5) {
                // A whole row: on its left, so the button at its right end stays in view.
                targetX = bStack.getMinX() + 16;
                targetY = Math.max(12, Math.min(bStack.getMaxY() + 12, stackH - cardH - 12));
            } else if (bStack.getMaxY() + 12 + cardH <= stackH - 12) {
                // Under it, its right edge lined up: a button at the top of a screen.
                targetX = Math.max(12, Math.min(bStack.getMaxX() - totalW, stackW - totalW - 12));
                targetY = bStack.getMaxY() + 12;
            } else {
                // Above it, or as near as it fits: it must not sit on what it marks.
                targetX = Math.max(12, Math.min(bStack.getMaxX() - totalW, stackW - totalW - 12));
                targetY = Math.max(12, bStack.getMinY() - cardH - 12);
            }
            arrowPoly.setVisible(conFlecha);
            pintarEncabezado(header, card, s, conFlecha);
            animateCardTo(firstPos, posAnim, cardWithArrow, targetX, targetY);

            // Pulsing ring
            pulseAnim[0] = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(ring.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                    new KeyValue(ring.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(900),
                    new KeyValue(ring.scaleXProperty(), 1.06, Interpolator.EASE_BOTH),
                    new KeyValue(ring.scaleYProperty(), 1.06, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1800),
                    new KeyValue(ring.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                    new KeyValue(ring.scaleYProperty(), 1.0, Interpolator.EASE_BOTH))
            );
            pulseAnim[0].setCycleCount(Timeline.INDEFINITE);
            pulseAnim[0].play();
            return;
        }

        // Nothing to mark: card body in the center of the screen, no ring or arrow
        ring.setVisible(false);
        arrowPoly.setVisible(false);
        pintarEncabezado(header, card, s, false);
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
