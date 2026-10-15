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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;

public final class TutorialOverlay {

    private static final Preferences PREFS = Preferences.userNodeForPackage(TutorialOverlay.class);
    private static final double CARD_W  = 420;
    private static final double ARROW_W = 16;
    // HBox spacing = -2, so card body starts at (ARROW_W - 2) = 14 px from HBox left

    private record Step(
        String icon, String color, String gradEnd,
        String title, String shortcut, String navigateId, String[] bullets
    ) {}

    private static Step[] buildSteps() {
        Usuario user = SessionManager.getCurrentUser();
        String primerNombre = (user != null && user.getNombre() != null && !user.getNombre().isBlank())
            ? user.getNombre().split("\\s+")[0] : null;
        String welcomeTitle = primerNombre != null ? "¡Hola, " + primerNombre + "!" : "Bienvenido a SIBIM";
        String rolDesc = SessionManager.isAdmin()
            ? "Tienes acceso completo como Administrador — incluye gestión de usuarios y auditoría"
            : SessionManager.isSecretario()
                ? "Ves los bienes de tu secretaría y sus direcciones, y mantienes al día su ubicación, descripción, estado físico y fotos"
                : "Ves los bienes de tu área y mantienes al día su ubicación, descripción, estado físico y fotos";

        List<Step> list = new ArrayList<>(List.of(
            // ── Bienvenida personalizada ──────────────────────────────────────────
            new Step("mdi2b-book-open-outline", "#A2232D", "#5A141A",
                welcomeTitle, null, null, new String[]{
                rolDesc,
                "Cada paso navega al módulo que describe — explóralo en vivo mientras lees",
                "Retoma este tutorial en cualquier momento presionando F2"
            }),

            // ── Inicio ───────────────────────────────────────────────────────────
            new Step("mdi2v-view-dashboard-outline", "#A2232D", "#5A141A",
                "Inicio", "Ctrl + 1", "dashboard", SessionManager.isAdmin() ? new String[]{
                "Los recuadros de estadísticas son clicables — llevan directo al módulo",
                "Haz clic en un segmento de la gráfica para filtrar Bienes por esa categoría",
                "El botón ↺ actualiza métricas en tiempo real tras registrar cambios"
            } : new String[]{
                "\"Pendientes de tu área\": los bienes sin foto, sin ubicación o sin estado físico",
                "Da clic en un pendiente, elige el bien y \"Actualizar datos\"",
                "Si Patrimonio te transfirió bienes, ahí mismo confirmas que los recibiste"
            }),

            // ── Categorías ANTES que Bienes: flujo correcto de onboarding ────────
            new Step("mdi2t-tag-multiple-outline", "#A2232D", "#5A141A",
                "Categorías", "Ctrl + 4", "categorias", new String[]{
                "Crea las categorías PRIMERO: Mobiliario, Cómputo, Vehículos, Equipo…",
                "Cada categoría tiene ícono y color — los bienes los heredan en todos los filtros",
                "El contador junto a cada categoría es clicable: filtra Bienes al instante"
            }),

            // ── Bienes ───────────────────────────────────────────────────────────
            new Step("mdi2p-package-variant", "#A2232D", "#5A141A",
                "Bienes / Inventario", "Ctrl + 3", "productos", new String[]{
                SessionManager.isAdmin()
                    ? "Ctrl+N abre el formulario: llena nombre, código, área, categoría y valor"
                    : "Selecciona un bien y \"Actualizar datos\" (Ctrl+E): ubicación, estado físico, descripción y fotos",
                "Doble clic en cualquier fila: foto, código QR e historial de movimientos",
                "Busca en tiempo real y combina filtros de categoría, área y estado"
            }),

            // ── Movimientos ──────────────────────────────────────────────────────
            new Step("mdi2s-swap-vertical", "#235B4E", "#1B4A3F",
                "Movimientos", "Ctrl + 5", "movimientos", new String[]{
                SessionManager.isAdmin()
                    ? "Entrada y Salida cambian la cantidad; Ajuste la corrige después de un conteo"
                    : "Aquí consultas el historial de tus bienes; los movimientos los registra Patrimonio",
                SessionManager.isAdmin()
                    ? "Transferencia mueve el bien completo a otra área, con su nuevo código"
                    : "Filtra por tipo y fecha para ver qué entró, salió o cambió de área",
                "La transferencia se aplica al instante; el área que recibe solo confirma que ya tiene el bien"
            }),

            // ── Sin conexión ─────────────────────────────────────────────────────
            new Step("mdi2w-wifi-off", "#9C7A3C", "#6E5118",
                "Sin internet", null, null, new String[]{
                "Si se va el internet aparece la franja \"Sin conexión\" y puedes seguir trabajando",
                "Lo que captures se guarda en esta computadora y se sube solo al volver la conexión",
                "Los documentos nuevos llevan un folio provisional; el definitivo se asigna al sincronizar"
            }),

            // ── Resguardos ───────────────────────────────────────────────────────
            new Step("mdi2c-clipboard-account-outline", "#9C7A3C", "#6E5118",
                "Resguardos", null, "resguardos", new String[]{
                "Cada resguardo dice quién tiene un bien a su cargo — es el documento oficial de asignación",
                "Consulta e imprime el PDF del resguardo para firma",
                "Cancela el resguardo cuando el bien cambia de responsable o de área"
            }),

            // ── Préstamos ────────────────────────────────────────────────────────
            new Step("mdi2s-swap-horizontal", "#9C7A3C", "#6E5118",
                "Préstamos", null, "prestamos", new String[]{
                "Registra el préstamo temporal de un bien con fecha de devolución esperada",
                "Vista kanban: columnas Activos / Vencidos / Devueltos para control rápido",
                "Exporta el comprobante en PDF para firma"
            }),

            // ── Actas de Entrega-Recepción ───────────────────────────────────────
            new Step("mdi2s-swap-horizontal-bold", "#9C7A3C", "#6E5118",
                "Actas E/R", null, "actas", new String[]{
                "Documenta la entrega formal de bienes entre funcionarios al cambio de administración",
                "Incluye inventario de bienes entregados, observaciones y firma de responsiva",
                "Exporta el acta completa a PDF lista para firma y archivo oficial"
            }),

            // ── Comodatos ────────────────────────────────────────────────────────
            new Step("mdi2h-handshake-outline", "#9C7A3C", "#6E5118",
                "Comodatos", null, "comodatos", new String[]{
                "Préstamo formal de un bien a otra institución, con contrato y vigencia",
                "Mientras está vigente el bien no se puede transferir ni dar de baja",
                "Al vencer aparece en Alertas hasta que lo concluyas o lo renueves"
            }),

            // ── Bajas ────────────────────────────────────────────────────────────
            new Step("mdi2a-archive-arrow-down-outline", "#9C7A3C", "#6E5118",
                "Bajas", null, "bajas", new String[]{
                "El área entrega la solicitud de baja firmada; Patrimonio la registra aquí",
                "El bien sale del inventario activo pero conserva su código e historial",
                "Su número queda libre para el siguiente bien nuevo del área"
            }),

            // ── Conteo Físico ────────────────────────────────────────────────────
            new Step("mdi2c-clipboard-check-outline", "#235B4E", "#1B4A3F",
                "Conteo Físico", null, null, new String[]{
                "Ábrelo desde el menú (Operación) y elige todo el inventario, un área o una categoría",
                "Da clic en la fila del bien que tienes enfrente, o escanea su etiqueta; \"Faltante\" si no está",
                "Las pestañas Pendientes y Con incidencia te dicen qué falta; lo que nadie marcó queda \"sin revisar\""
            }),

            // ── Alertas ──────────────────────────────────────────────────────────
            new Step("mdi2b-bell-outline", "#A2232D", "#5A141A",
                "Alertas", "Ctrl + 6", "alertas", new String[]{
                "Se generan solas: garantías por vencer, mantenimientos próximos y comodatos vencidos",
                "El badge rojo en el sidebar muestra cuántas alertas activas hay sin atender",
                "Pendientes patrimoniales lista los bienes sin resguardante o sin etiqueta física"
            }),

            // ── Reportes ─────────────────────────────────────────────────────────
            new Step("mdi2f-file-chart-outline", "#235B4E", "#1B4A3F",
                "Reportes", "Ctrl + 7", "reportes", new String[]{
                SessionManager.isAdmin()
                    ? "Listados del inventario y formatos oficiales (resguardo, baja, dictamen) en un solo lugar"
                    : "Listados de los bienes de tu área y los formatos para pedir una baja",
                "Elige el período arriba: los listados que muestran las fechas solo incluyen ese período",
                SessionManager.isAdmin()
                    ? "Excel y CSV para análisis; PDF para un documento listo para entrega"
                    : "Se exportan en PDF, listos para imprimir y firmar"
            }),

            // ── Depreciación ─────────────────────────────────────────────────────
            new Step("mdi2c-chart-line", "#235B4E", "#1B4A3F",
                "Depreciación", "Ctrl + 8", "depreciacion", new String[]{
                "Cálculo automático: valor de compra ÷ vida útil = depreciación anual (SAT)",
                "Bienes en rojo = 100% depreciados — candidatos prioritarios a baja o reemplazo",
                "Exporta fichas individuales o en lote para auditorías y levantamientos INEGI"
            }),

            // ── Organigrama ──────────────────────────────────────────────────────
            new Step("mdi2o-office-building-outline", "#A2232D", "#5A141A",
                "Organigrama", "Ctrl + 2", "organigrama", new String[]{
                "Vista jerárquica de áreas y bienes — ideal para auditorías y actas de entrega",
                "Clic en el número de bienes de un área: la lista completa, con \"Ver en Inventario\" y exportar",
                "Exporta el árbol completo a PDF desde la barra de herramientas"
            }),

            // ── Configuración (contenido distinto para admin) ─────────────────────
            new Step("mdi2c-cog-outline", "#64748B", "#334155",
                "Configuración", "Ctrl + 9", "configuracion", new String[]{
                SessionManager.isAdmin()
                    ? "Usuarios: crea las cuentas por área de un clic y guarda el PDF con sus contraseñas"
                    : "Tamaño de letra, densidad de filas y animaciones: en la barra inferior y en Accesibilidad",
                "Sube el logo del ayuntamiento — se muestra en la pantalla de inicio del sistema",
                "Genera respaldos de la base de datos y revisa el estado de la conexión"
            }),

            // ── Atajos de teclado ────────────────────────────────────────────────
            new Step("mdi2k-keyboard-outline", "#64748B", "#475569",
                "Atajos de Teclado", "F1", null, new String[]{
                "Ctrl+1…9 navega entre módulos; Ctrl+0 abre Auditoría (solo admin)",
                "Ctrl+F busca en la tabla activa · F5 actualiza · Ctrl+N crea nuevo registro",
                "En tablas: Ctrl+E edita la fila seleccionada · Supr elimina · F2 reabre el tutorial"
            }),

            // ── Primeros pasos: cierre accionable ────────────────────────────────
            new Step("mdi2c-check-circle-outline", "#235B4E", "#1B4A3F",
                "¡Listo para empezar!", null, "dashboard", new String[]{
                SessionManager.isAdmin()
                    ? "① Crea categorías (Ctrl+4), da de alta bienes (Ctrl+3 → Ctrl+N) y registra la primera entrada"
                    : "① Revisa tus bienes (Ctrl+3) y actualiza ubicación, fotos y estado físico",
                SessionManager.isAdmin()
                    ? "② Asigna resguardos a los responsables y documenta préstamos temporales"
                    : "② La guía rápida imprimible está en el menú de tu cuenta",
                "③ F2 en cualquier momento reabre este tutorial · F1 muestra todos los atajos"
            })
        ));

        if (!SessionManager.isAdmin())
            list.removeIf(paso -> "Conteo Físico".equals(paso.title()) || paso.navigateId() != null
                && java.util.Set.of("movimientos", "comodatos", "actas", "bajas", "categorias", "depreciacion", "resguardos", "prestamos")
                    .contains(paso.navigateId()));

        // A shared área account: who is using it goes into every record.
        if (user != null && com.sibim.service.CuentasAreaService.CARGO_COMPARTIDA.equals(user.getCargo()))
            list.add(1, new Step("mdi2a-account-edit-outline", "#A2232D", "#5A141A",
                "Cuenta compartida", null, null, new String[]{
                "Esta cuenta la usa todo el personal de tu área",
                "Al entrar escribe tu nombre, o confirma el que aparece: así tus cambios quedan a tu nombre",
                "Si alguien más se sienta sin cerrar sesión: menú de tu cuenta › \"Cambiar quién usa la cuenta\""
            }));

        if (SessionManager.isAdmin()) {
            // Insert Auditoría before the last two steps (Atajos + ¡Listo!)
            list.add(list.size() - 2,
                new Step("mdi2s-shield-lock-outline", "#475569", "#1E293B",
                    "Auditoría del Sistema", "Ctrl + 0", "auditoria", new String[]{
                    "Registra automáticamente todas las acciones: altas, bajas, ediciones, sesiones",
                    "Filtra por entidad, acción, usuario y fechas; \"Limpiar filtros\" aparece cuando hay alguno",
                    "Exporta el log completo a Excel para revisiones de cumplimiento y control"
                })
            );
        }
        return list.toArray(new Step[0]);
    }

    private TutorialOverlay() {}

    public static void showIfFirstTime(StackPane outerStack) {
        String username = SessionManager.getCurrentUser() != null
            ? SessionManager.getCurrentUser().getUsername() : "unknown";
        String key = "tutorial.v6." + username;
        if (PREFS.getBoolean(key, false)) return;
        PREFS.putBoolean(key, true);
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    public static void show(StackPane outerStack) {
        javafx.application.Platform.runLater(() -> buildAndShow(outerStack));
    }

    private static void buildAndShow(StackPane outerStack) {
        final Step[] STEPS = buildSteps();
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
        Button btnSkip = new Button("Saltar tutorial");
        btnSkip.getStyleClass().add("tutorial-btn-skip");
        btnSkip.setAccessibleText("Saltar tutorial");
        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox btnRow = new HBox(8, btnSkip, btnSpacer, btnPrev, btnNext);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        // ── Keyboard hint ─────────────────────────────────────────────────────
        Label kbHint = new Label("← → para avanzar o regresar  ·  Esc para cerrar");
        kbHint.getStyleClass().add("tutorial-kb-hint");

        // ── Card body VBox ────────────────────────────────────────────────────
        VBox contentPad = new VBox(16, slideContent, btnRow, kbHint);
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
            FadeTransition fadeDim  = new FadeTransition(AnimationUtils.duracion(220), dimLayer);
            fadeDim.setToValue(0);
            FadeTransition fadeCard = new FadeTransition(AnimationUtils.duracion(220), cardContainer);
            fadeCard.setToValue(0);
            ScaleTransition shrink  = new ScaleTransition(AnimationUtils.duracion(220), card);
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
            if (hasNav) card.getStyleClass().add("tutorial-card-with-arrow");
            else        card.getStyleClass().remove("tutorial-card-with-arrow");

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
            btnNext.setText(isLast ? "Comenzar  ✓" : "Siguiente →");
            btnNext.setAccessibleText(isLast ? "Comenzar a usar SIBIM" : "Siguiente paso");
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

        btnClose.setOnAction(e -> dismiss.run());

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
