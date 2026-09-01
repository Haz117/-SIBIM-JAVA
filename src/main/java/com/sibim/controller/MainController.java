package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import com.sibim.repository.AuditLogRepository;
import com.sibim.service.ProductoService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.TutorialOverlay;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane contentArea;
    @FXML private Label userNameLabel;
    @FXML private Label userRolLabel;
    @FXML private Label userAvatarLabel;
    @FXML private Button btnDashboard;
    @FXML private Button btnOrganigrama;
    @FXML private Button btnProductos;
    @FXML private Button btnCategorias;
    @FXML private Button btnMovimientos;
    @FXML private Button btnAlertas;
    @FXML private Button btnReportes;
    @FXML private Button btnDepreciacion;
    @FXML private Button btnConfiguracion;
    @FXML private Label alertBadge;
    @FXML private javafx.scene.layout.HBox offlineBanner;
    @FXML private Label offlineBannerLabel;
    @FXML private javafx.scene.control.Button offlineBannerSyncBtn;
    @FXML private Label statusDbLabel;
    @FXML private Label statusUserLabel;
    @FXML private Label statusTimeLabel;
    @FXML private StackPane outerStack;
    @FXML private VBox     sidebar;
    @FXML private Region   sidebarBackdrop;
    @FXML private VBox     logoTextBox;
    @FXML private VBox     userInfoVBox;
    @FXML private Button   btnToggleSidebar;
    @FXML private FontIcon statusDotIcon;
    private boolean sidebarCollapsed = false;

    private Button   activeButton;
    private Object   currentController;
    private Timeline badgeRefresh;
    private Timeline badgePulse;
    private final Map<Button, Timeline> navHoverAnims = new HashMap<>();
    private Timeline clock;
    private Timeline sessionGuard;
    private final ProductoService alertProductoService = new ProductoService();
            private final AuditLogRepository auditRepo = new AuditLogRepository();

    // Session inactivity timeout — 30 minutes
    private static final long INACTIVITY_TIMEOUT_MS = 30 * 60_000L;
    private static final long INACTIVITY_WARN_MS    = INACTIVITY_TIMEOUT_MS - 5 * 60_000L;
    private long    lastActivityMs    = System.currentTimeMillis();
    private boolean inactivityWarned  = false;

    // Tab overlay — extends the active sidebar pill into the content area
    private static final double SIDEBAR_WIDTH  = 220;
    private static final double TAB_OVERLAP    = 6;    // starts this many px before sidebar edge
    private static final double TAB_EXTENSION  = 40;   // extends this many px past sidebar edge
    private Pane   tabOverlay;
    private Region tabProtrusion;
    private boolean tabPositioned = false;
    private boolean startupTasksScheduled = false;

    // Only one MainController is ever active at a time — this lets child
    // views loaded into contentArea (e.g. Organigrama) trigger navigation
    // without needing an injected reference threaded through every FXML load.
    private static MainController instance;

    public static MainController getInstance() { return instance; }

    /** The controller for whatever view is currently loaded into contentArea. */
    public Object getCurrentController() { return currentController; }

    /** Used by SyncService to route toasts to the right Scene from a
     *  background poll, where it has no Node of its own to hang one off of. */
    public javafx.scene.Scene getContentAreaScene() { return contentArea.getScene(); }

    /** Called by SyncService once every queued offline change has been
     *  synced, so whatever screen the user is looking at reflects the
     *  server's state instead of the stale offline snapshot it loaded with. */
    public void refreshCurrentViewAfterSync() {
        refreshCurrentView();
        updateStatusBar();
    }

    @FXML
    public void initialize() {
        instance = this;
        if (SessionManager.getCurrentUser() != null) {
            String nombre = SessionManager.getCurrentUser().getNombre();
            userNameLabel.setText(nombre);
            userRolLabel.setText(SessionManager.getCurrentUser().getRol().getEtiqueta());
            if (userAvatarLabel != null && nombre != null && !nombre.isBlank())
                userAvatarLabel.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }
        navigateTo("dashboard", btnDashboard);
        addNavTooltips();
        setupNavHover(btnDashboard, btnOrganigrama, btnProductos, btnCategorias,
                      btnMovimientos, btnAlertas, btnReportes, btnDepreciacion, btnConfiguracion);

        // Single scene listener — consolidates what were three separate listeners.
        // Non-critical startup tasks (badge, vencidos, update check) are staggered
        // so they don't compete with the dashboard's 6 parallel DB queries at boot.
        contentArea.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                setupKeyboardShortcuts(scene);
                scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> { lastActivityMs = System.currentTimeMillis(); inactivityWarned = false; });
                scene.addEventFilter(KeyEvent.KEY_PRESSED,     e -> { lastActivityMs = System.currentTimeMillis(); inactivityWarned = false; });
                javafx.application.Platform.runLater(this::setupTabProtrusion);
                if (!startupTasksScheduled) {
                    startupTasksScheduled = true;
                    javafx.application.Platform.runLater(() -> TutorialOverlay.showIfFirstTime(outerStack));

                    if (DatabaseConfig.isDemoMode())
                        NotificacionUtil.advertencia(scene,
                            "Modo demostración activo — los datos no son reales. "
                            + "Para conectar a PostgreSQL, crea %APPDATA%\\SIBIM\\.env con DB_URL, DB_USER y DB_PASSWORD.");

                    // Delay badge 800 ms so dashboard queries finish first
                    javafx.animation.PauseTransition badgeDelay =
                        new javafx.animation.PauseTransition(Duration.millis(800));
                    badgeDelay.setOnFinished(e -> loadAlertBadge());
                    badgeDelay.play();
                    // Vencidos check: 1.5 s (informational toast, not blocking)
                    javafx.animation.PauseTransition vencidosDelay =
                        new javafx.animation.PauseTransition(Duration.millis(1500));
                    vencidosDelay.setOnFinished(e -> checkVencidosOnStart(scene));
                    vencidosDelay.play();
                    // Update check: 3 s (network request, lowest priority)
                    javafx.animation.PauseTransition updateDelay =
                        new javafx.animation.PauseTransition(Duration.millis(3000));
                    updateDelay.setOnFinished(e -> checkForUpdate(scene));
                    updateDelay.play();
                }
            }
        });

        // Stop any timelines left over from a previous session (re-login path)
        if (badgeRefresh  != null) badgeRefresh.stop();
        if (clock         != null) clock.stop();
        if (sessionGuard  != null) sessionGuard.stop();

        // Refresh badge every 3 minutes; also nudges the status bar so the
        // offline-mode pending-sync count doesn't go stale between syncs.
        badgeRefresh = new Timeline(new KeyFrame(Duration.minutes(3), e -> { loadAlertBadge(); updateStatusBar(); }));
        badgeRefresh.setCycleCount(Timeline.INDEFINITE);
        badgeRefresh.play();

        updateStatusBar();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateStatusTime()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        sessionGuard = new Timeline(new KeyFrame(Duration.minutes(1), e -> checkInactivity()));
        sessionGuard.setCycleCount(Timeline.INDEFINITE);
        sessionGuard.play();
    }

    @FXML private void onDashboard()     { navigateTo("dashboard",     btnDashboard); }
    @FXML private void onOrganigrama()   { navigateTo("organigrama",   btnOrganigrama); }
    @FXML private void onProductos()     { navigateTo("productos",     btnProductos); }
    @FXML private void onCategorias()    { navigateTo("categorias",    btnCategorias); }
    @FXML private void onMovimientos()   { navigateTo("movimientos",   btnMovimientos); }
    @FXML private void onAlertas()       { navigateTo("alertas",       btnAlertas); }
    @FXML private void onReportes()      { navigateTo("reportes",      btnReportes); }
    @FXML private void onDepreciacion()  { navigateTo("depreciacion",  btnDepreciacion); }
    @FXML private void onConfiguracion() { navigateTo("configuracion", btnConfiguracion); }

    @FXML
    private void onAcercaDe() {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2d-domain", "Acerca de SIBIM",
            "Sistema Integral de Bienes Municipales",
            "#6366F1", "#4F46E5");

        GridPane g = new GridPane();
        g.setHgap(16); g.setVgap(10);
        g.setPadding(new Insets(16, 22, 16, 22));
        String[][] rows = {
            { "Versión",         "1.0.0" },
            { "Plataforma",      "Java " + System.getProperty("java.version") + " · JavaFX 21" },
            { "Sistema",         System.getProperty("os.name") + " " + System.getProperty("os.version") },
            { "Modo de datos",   DatabaseConfig.isDemoMode() ? "Demo (sin base de datos)"
                                : DatabaseConfig.isOfflineMode() ? "Offline (" + SyncService.pendingCount() + " pendiente(s) de sincronizar)"
                                : "PostgreSQL (conectado)" },
            { "Desarrollado por","H. Ayuntamiento Municipal" },
            { "Año",             "2026" },
        };
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("dlg-detail-label");
            k.setMinWidth(130);
            Label v = new Label(rows[i][1]);
            v.getStyleClass().add("dlg-detail-value");
            v.setWrapText(true);
            g.add(k, 0, i); g.add(v, 1, i);
        }

        VBox content = new VBox(0, header, g);
        dialog.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, g), 260, 70);
        dialog.showAndWait();
    }

    @FXML
    private void onShowTutorial() {
        TutorialOverlay.show(outerStack);
    }

    @FXML
    private void onLogout() {
        if (!ConfirmacionUtil.confirmar("Cerrar sesión", "¿Deseas cerrar tu sesión?")) return;
        stopTimers();
        instance = null;
        auditRepo.log("sesion", SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getId() : null,
            SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getNombre() : null,
            "logout", "Cierre de sesión manual");
        SessionManager.logout();
        try { MainApp.showLogin(); } catch (Exception e) { log.error("No se pudo volver a la pantalla de login", e); }
    }

    /** Stops the recurring Timelines owned by this controller (and any
     *  running in the currently loaded child view) so they don't keep
     *  firing — and keeping this whole controller tree alive — after the
     *  user logs out. JavaFX's animation engine holds a running Timeline
     *  alive on its own, independent of Java reachability. */
    private void startBadgePulse() {
        stopBadgePulse();
        if (alertBadge == null) return;
        // Soft heartbeat: scale 1.0 → 1.18 → 1.0, every 2.4 s
        badgePulse = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(alertBadge.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(alertBadge.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)),
            new KeyFrame(Duration.millis(160),
                new KeyValue(alertBadge.scaleXProperty(), 1.18, Interpolator.EASE_OUT),
                new KeyValue(alertBadge.scaleYProperty(), 1.18, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(360),
                new KeyValue(alertBadge.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                new KeyValue(alertBadge.scaleYProperty(), 1.0, Interpolator.EASE_IN)),
            new KeyFrame(Duration.millis(600),
                new KeyValue(alertBadge.scaleXProperty(), 1.08, Interpolator.EASE_OUT),
                new KeyValue(alertBadge.scaleYProperty(), 1.08, Interpolator.EASE_OUT)),
            new KeyFrame(Duration.millis(820),
                new KeyValue(alertBadge.scaleXProperty(), 1.0, Interpolator.EASE_IN),
                new KeyValue(alertBadge.scaleYProperty(), 1.0, Interpolator.EASE_IN))
        );
        badgePulse.setCycleCount(Timeline.INDEFINITE);
        badgePulse.setDelay(Duration.millis(600));
        badgePulse.play();
    }

    private void stopBadgePulse() {
        if (badgePulse != null) { badgePulse.stop(); badgePulse = null; }
        if (alertBadge != null) { alertBadge.setScaleX(1.0); alertBadge.setScaleY(1.0); }
    }

    private void stopTimers() {
        if (badgeRefresh != null) badgeRefresh.stop();
        if (clock != null) clock.stop();
        if (sessionGuard != null) sessionGuard.stop();
        stopBadgePulse();
        if (currentController instanceof AlertasController ac) ac.stopAutoRefresh();
    }

    public static Button resolveNavigationButton(String view,
                                                Button dashboard,
                                                Button organigrama,
                                                Button productos,
                                                Button categorias,
                                                Button movimientos,
                                                Button alertas,
                                                Button reportes,
                                                Button configuracion,
                                                Button depreciacion,
                                                Button fallback) {
        Button resolved = switch (view) {
            case "dashboard"     -> dashboard;
            case "organigrama"   -> organigrama;
            case "productos"     -> productos;
            case "categorias"    -> categorias;
            case "movimientos"   -> movimientos;
            case "alertas"       -> alertas;
            case "reportes"      -> reportes;
            case "configuracion" -> configuracion;
            case "depreciacion"  -> depreciacion;
            default              -> fallback;
        };
        return resolved != null ? resolved : fallback;
    }

    /** Navigate programmatically by view name — used by TutorialOverlay. */
    public void navigateToView(String view) {
        navigateTo(view, resolveNavigationButton(view,
            btnDashboard, btnOrganigrama, btnProductos, btnCategorias,
            btnMovimientos, btnAlertas, btnReportes, btnConfiguracion,
            btnDepreciacion, btnDashboard));
    }

    /** Return the sidebar Button for a given view — used by TutorialOverlay for ring positioning. */
    public Button getNavButton(String view) {
        return resolveNavigationButton(view,
            btnDashboard, btnOrganigrama, btnProductos, btnCategorias,
            btnMovimientos, btnAlertas, btnReportes, btnConfiguracion,
            btnDepreciacion, null);
    }

    private void navigateTo(String view, Button button) {
        try {
            if (button == null) {
                log.warn("Intento de navegación con botón nulo para vista: {}", view);
                return;
            }
            // Update nav state + reset hover transforms immediately
            if (activeButton != null) activeButton.getStyleClass().remove("nav-active");
            Timeline pendingHover = navHoverAnims.remove(button);
            if (pendingHover != null) pendingHover.stop();
            button.setTranslateX(0);
            button.getStyleClass().add("nav-active");
            activeButton = button;
            updateTabProtrusion(button);

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/" + view + ".fxml")));
            Node node = loader.load();

            if (currentController instanceof AlertasController ac) ac.stopAutoRefresh();
            currentController = loader.getController();

            if (!contentArea.getChildren().isEmpty()) {
                Node current = contentArea.getChildren().get(0);
                FadeTransition out = new FadeTransition(Duration.millis(110), current);
                out.setFromValue(current.getOpacity());
                out.setToValue(0);
                out.setOnFinished(ev -> {
                    contentArea.getChildren().setAll(node);
                    AnimationUtils.pageIn(node);
                });
                out.play();
            } else {
                contentArea.getChildren().setAll(node);
                AnimationUtils.pageIn(node);
            }
        } catch (Exception e) {
            log.error("No se pudo cargar la vista: {}", view, e);
            // contentArea may not be in a scene yet during initial load — fall back to stage scene
            javafx.scene.Scene errScene = contentArea.getScene() != null
                ? contentArea.getScene()
                : MainApp.getPrimaryStage() != null ? MainApp.getPrimaryStage().getScene() : null;
            NotificacionUtil.error(errScene, "No se pudo cargar la vista: " + view);
        }
    }

    // ── Tab overlay ──────────────────────────────────────────────────────────
    private void setupTabProtrusion() {
        if (outerStack == null || tabOverlay != null) return;

        tabProtrusion = new Region();
        tabProtrusion.setPrefWidth(TAB_OVERLAP + TAB_EXTENSION);
        tabProtrusion.getStyleClass().add("nav-tab-extension");

        tabOverlay = new Pane(tabProtrusion);
        tabOverlay.setMouseTransparent(true);
        tabOverlay.setPickOnBounds(false);
        outerStack.getChildren().add(tabOverlay);

        javafx.application.Platform.runLater(() -> updateTabProtrusion(btnDashboard));
    }

    private double currentSidebarWidth() {
        return (sidebar != null && sidebar.getWidth() > 0) ? sidebar.getWidth() : SIDEBAR_WIDTH;
    }

    private void updateTabProtrusion(Button btn) {
        if (tabProtrusion == null || outerStack == null || outerStack.getScene() == null) return;
        javafx.application.Platform.runLater(() -> {
            // translateX is horizontal-only, so minY/maxY are stable during animation.
            Bounds  b    = btn.localToScene(btn.getBoundsInLocal());
            Point2D org  = outerStack.sceneToLocal(0, 0);
            double  btnTop = b.getMinY() + org.getY();
            double  btnBot = b.getMaxY() + org.getY();
            double  btnH   = btnBot - btnTop;

            double sw    = currentSidebarWidth();
            double extX  = sw - TAB_OVERLAP;

            if (!tabPositioned) {
                tabProtrusion.setLayoutX(extX); tabProtrusion.setLayoutY(btnTop);
                tabPositioned = true;
            } else {
                glideTab(tabProtrusion, extX, btnTop);
            }
            tabProtrusion.setPrefHeight(btnH);
        });
    }

    private void glideTab(Region node, double targetX, double targetY) {
        double fromY = node.getLayoutY() + node.getTranslateY();
        node.setLayoutX(targetX);
        node.setLayoutY(targetY);
        node.setTranslateY(fromY - targetY);
        TranslateTransition tt = new TranslateTransition(Duration.millis(190), node);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_OUT);
        tt.play();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void loadAlertBadge() {
        DialogUtil.runAsync(
            () -> alertProductoService.getAgotados().size()
                + alertProductoService.getBajoStock().size()
                + alertProductoService.getVencidosProximos(30).size(),
            total -> {
                if (alertBadge == null) return;
                if (total > 0) {
                    alertBadge.setText(total > 99 ? "99+" : String.valueOf(total));
                    boolean wasHidden = !alertBadge.isVisible();
                    alertBadge.setVisible(true);
                    alertBadge.setManaged(true);
                    if (wasHidden) {
                        ScaleTransition pop = new ScaleTransition(Duration.millis(320), alertBadge);
                        pop.setFromX(0.3); pop.setFromY(0.3);
                        pop.setToX(1.0);   pop.setToY(1.0);
                        pop.setInterpolator(Interpolator.EASE_OUT);
                        pop.setOnFinished(ev -> startBadgePulse());
                        pop.play();
                    } else {
                        AnimationUtils.pulse(alertBadge, 3);
                    }
                } else {
                    stopBadgePulse();
                    alertBadge.setVisible(false);
                    alertBadge.setManaged(false);
                }
            },
            e -> { /* silently ignore — badge is decorative */ }
        );
    }

    private void updateStatusBar() {
        if (statusUserLabel != null && SessionManager.getCurrentUser() != null)
            statusUserLabel.setText(SessionManager.getCurrentUser().getNombre() +
                "  ·  " + SessionManager.getCurrentUser().getRol().getEtiqueta());

        boolean offline = DatabaseConfig.isOfflineMode();
        boolean demo    = DatabaseConfig.isDemoMode();
        int pending     = offline ? SyncService.pendingCount() : 0;

        if (statusDbLabel != null) {
            String text = offline
                ? "Modo offline · " + pending + " pendiente(s)"
                : demo ? "Modo demo" : "Base de datos conectada";
            statusDbLabel.setText(text);
            if (statusDotIcon != null) {
                statusDotIcon.getStyleClass().removeAll("status-dot-icon-ok", "status-dot-icon-demo");
                statusDotIcon.getStyleClass().add((demo || offline) ? "status-dot-icon-demo" : "status-dot-icon-ok");
            }
        }

        if (offlineBanner != null) {
            boolean show = offline || demo;
            offlineBanner.setVisible(show);
            offlineBanner.setManaged(show);
            if (show && offlineBannerLabel != null) {
                offlineBannerLabel.setText(offline
                    ? (pending > 0
                        ? "Sin conexión — " + pending + " cambio(s) guardados localmente, se sincronizarán al reconectar"
                        : "Sin conexión — trabajando en modo offline")
                    : "Modo demostración — los datos no se guardan");
            }
            if (offlineBannerSyncBtn != null) {
                offlineBannerSyncBtn.setVisible(offline);
                offlineBannerSyncBtn.setManaged(offline);
            }
            offlineBanner.getStyleClass().removeAll("offline-banner-demo");
            if (demo) offlineBanner.getStyleClass().add("offline-banner-demo");
        }

        updateStatusTime();
    }

    @FXML
    private void onSyncNow() {
        if (offlineBannerSyncBtn != null) offlineBannerSyncBtn.setDisable(true);
        com.sibim.util.AppExecutor.submit(() -> {
            com.sibim.db.offline.SyncService.syncNow();
            javafx.application.Platform.runLater(() -> {
                updateStatusBar();
                if (offlineBannerSyncBtn != null) offlineBannerSyncBtn.setDisable(false);
            });
        });
    }

    private static final double SIDEBAR_COLLAPSED_WIDTH = 76;

    @FXML
    private void onToggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        double targetW = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH;

        sidebar.setMinWidth(Region.USE_PREF_SIZE);
        sidebar.setMaxWidth(Region.USE_PREF_SIZE);
        sidebarBackdrop.setMinWidth(Region.USE_PREF_SIZE);
        sidebarBackdrop.setMaxWidth(Region.USE_PREF_SIZE);

        // Collect nodes to show/hide
        java.util.Set<javafx.scene.Node> sectionLabels = sidebar.lookupAll(".nav-section-label");
        java.util.List<Button> navBtns = java.util.stream.Stream.of(
                btnDashboard, btnOrganigrama, btnProductos, btnCategorias,
                btnMovimientos, btnAlertas, btnReportes, btnDepreciacion, btnConfiguracion)
            .filter(b -> b != null).collect(java.util.stream.Collectors.toList());
        java.util.List<Button> footerBtns = new java.util.ArrayList<>();
        sidebar.lookupAll(".logout-btn").forEach(n -> { if (n instanceof Button b) footerBtns.add(b); });
        sidebar.lookupAll(".about-btn" ).forEach(n -> { if (n instanceof Button b) footerBtns.add(b); });

        // The logo HBox and its badge label; user card HBox
        javafx.scene.Node logoBadge = sidebar.lookup(".sidebar-logo-badge");
        javafx.scene.layout.HBox logoHBox = (logoTextBox != null && logoTextBox.getParent() instanceof javafx.scene.layout.HBox h) ? h : null;
        javafx.scene.Node userCard = (userInfoVBox != null) ? userInfoVBox.getParent() : null;

        // Nav scroll VBox — reduce inner padding so icons fit at collapsed width
        javafx.scene.control.ScrollPane navScroll =
            sidebar.lookup(".sidebar-scroll") instanceof javafx.scene.control.ScrollPane sp ? sp : null;
        javafx.scene.layout.VBox navVBox =
            (navScroll != null && navScroll.getContent() instanceof javafx.scene.layout.VBox v) ? v : null;

        if (sidebarCollapsed) sidebar.getStyleClass().add("sidebar-collapsed");
        else sidebar.getStyleClass().remove("sidebar-collapsed");

        if (sidebarCollapsed) {
            // Logo: hide badge + text, shrink HBox padding so only toggle button shows
            if (logoBadge != null) { logoBadge.setVisible(false); logoBadge.setManaged(false); }
            logoTextBox.setVisible(false); logoTextBox.setManaged(false);
            if (logoHBox != null) { logoHBox.setPadding(new Insets(10, 4, 0, 4)); logoHBox.setSpacing(0); }
            // User card: hide entirely (avatar 38px would overflow 76px with its padding)
            if (userCard != null) { userCard.setVisible(false); userCard.setManaged(false); }
            // Nav
            sectionLabels.forEach(n -> { n.setVisible(false); n.setManaged(false); });
            navBtns.forEach(b -> b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));
            if (navVBox != null) navVBox.setPadding(new Insets(6, 12, 6, 12));
            // Footer
            footerBtns.forEach(b -> b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));
            ((FontIcon) btnToggleSidebar.getGraphic()).setIconLiteral("mdi2c-chevron-right");
        } else {
            ((FontIcon) btnToggleSidebar.getGraphic()).setIconLiteral("mdi2c-chevron-left");
        }

        Timeline t = new Timeline(
            new KeyFrame(Duration.millis(220),
                new KeyValue(sidebar.prefWidthProperty(), targetW, Interpolator.EASE_BOTH),
                new KeyValue(sidebarBackdrop.prefWidthProperty(), targetW, Interpolator.EASE_BOTH))
        );
        t.setOnFinished(ev -> {
            if (!sidebarCollapsed) {
                if (logoBadge != null) { logoBadge.setVisible(true); logoBadge.setManaged(true); }
                logoTextBox.setVisible(true); logoTextBox.setManaged(true);
                if (logoHBox != null) { logoHBox.setPadding(new Insets(18, 18, 0, 10)); logoHBox.setSpacing(10); }
                if (userCard != null) { userCard.setVisible(true); userCard.setManaged(true); }
                sectionLabels.forEach(n -> { n.setVisible(true); n.setManaged(true); });
                navBtns.forEach(b -> b.setContentDisplay(ContentDisplay.LEFT));
                if (navVBox != null) navVBox.setPadding(new Insets(6, 18, 6, 10));
                footerBtns.forEach(b -> b.setContentDisplay(ContentDisplay.LEFT));
            }
            if (activeButton != null) updateTabProtrusion(activeButton);
        });
        t.play();
    }

    private void updateStatusTime() {
        if (statusTimeLabel != null)
            statusTimeLabel.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private void checkInactivity() {
        long idle = System.currentTimeMillis() - lastActivityMs;
        if (idle > INACTIVITY_TIMEOUT_MS) {
            log.info("Sesión cerrada por inactividad");
            inactivityWarned = false;
            auditRepo.log("sesion", SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getId() : null,
                SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getNombre() : null,
                "logout", "Cierre automático por inactividad");
            SessionManager.logout();
            try { MainApp.showLogin(); }
            catch (Exception e) { log.error("Error al cerrar sesión por inactividad", e); }
        } else if (!inactivityWarned && idle > INACTIVITY_WARN_MS) {
            inactivityWarned = true;
            javafx.application.Platform.runLater(this::showInactivityWarning);
        }
    }

    private void showInactivityWarning() {
        javafx.scene.control.ButtonType btnContinuar =
            new javafx.scene.control.ButtonType("Continuar sesión", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        javafx.scene.control.ButtonType btnLogout =
            new javafx.scene.control.ButtonType("Cerrar sesión", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<javafx.scene.control.ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Sesión por expirar");
        dlg.getDialogPane().getButtonTypes().addAll(btnContinuar, btnLogout);
        dlg.getDialogPane().setPrefWidth(400);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.setOnCloseRequest(javafx.event.Event::consume);

        HBox header = DialogUtil.gradientHeader("mdi2t-timer-outline", "Sesión inactiva",
            "Tu sesión cerrará automáticamente por inactividad.", "#B45309", "#92400E");

        Label lblCountdown = new Label("5:00");
        lblCountdown.getStyleClass().add("inactivity-countdown");

        Label lblHint = new Label("Presiona \"Continuar sesión\" para seguir trabajando.");
        lblHint.getStyleClass().add("muted-sm");
        lblHint.setWrapText(true);

        VBox body = new VBox(14, lblCountdown, lblHint);
        body.setPadding(new Insets(24, 24, 20, 24));
        body.setAlignment(javafx.geometry.Pos.CENTER);

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, body), 260, 70);
        dlg.getDialogPane().setContent(new VBox(header, body));

        long[] msLeft = { 5 * 60_000L };
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            msLeft[0] = Math.max(0, msLeft[0] - 1000);
            long mins = msLeft[0] / 60_000;
            long secs = (msLeft[0] % 60_000) / 1000;
            lblCountdown.setText(String.format("%d:%02d", mins, secs));
            if (msLeft[0] == 0) dlg.close();
        }));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();

        dlg.showAndWait().ifPresent(r -> {
            countdown.stop();
            if (r == btnContinuar) {
                lastActivityMs = System.currentTimeMillis();
                inactivityWarned = false;
                NotificacionUtil.info(contentArea.getScene(), "Sesión extendida — bienvenido de vuelta");
            } else {
                log.info("Usuario cerró sesión desde el aviso de inactividad");
                auditRepo.log("sesion", SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getId() : null,
                    SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getNombre() : null,
                    "logout", "Cierre de sesión desde aviso de inactividad");
                SessionManager.logout();
                try { MainApp.showLogin(); } catch (Exception ex) { log.error("Error al cerrar sesión", ex); }
            }
        });
        countdown.stop();
    }

    private void setupKeyboardShortcuts(javafx.scene.Scene scene) {
        var a = scene.getAccelerators();
        a.put(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.CONTROL_DOWN), () -> onDashboard());
        a.put(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.CONTROL_DOWN), () -> onOrganigrama());
        a.put(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN), () -> onProductos());
        a.put(new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.CONTROL_DOWN), () -> onCategorias());
        a.put(new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.CONTROL_DOWN), () -> onMovimientos());
        a.put(new KeyCodeCombination(KeyCode.DIGIT6, KeyCombination.CONTROL_DOWN), () -> onAlertas());
        a.put(new KeyCodeCombination(KeyCode.DIGIT7, KeyCombination.CONTROL_DOWN), () -> onReportes());
        a.put(new KeyCodeCombination(KeyCode.DIGIT8, KeyCombination.CONTROL_DOWN), () -> onDepreciacion());
        a.put(new KeyCodeCombination(KeyCode.DIGIT9, KeyCombination.CONTROL_DOWN), () -> onConfiguracion());
        a.put(new KeyCodeCombination(KeyCode.F5),                                   () -> refreshCurrentView());
        a.put(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),      () -> refreshCurrentView());
        a.put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),      () -> focusCurrentSearch(scene));
        a.put(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),      this::onCommandPalette);
        a.put(new KeyCodeCombination(KeyCode.F1), () -> showShortcutHelp());
        a.put(new KeyCodeCombination(KeyCode.F2), () -> onShowTutorial());
    }

    private void focusCurrentSearch(javafx.scene.Scene scene) {
        if (scene == null) return;
        javafx.scene.Node found = scene.lookup("#searchField");
        if (found instanceof TextField tf) {
            tf.requestFocus();
            tf.selectAll();
        }
    }

    private void onCommandPalette() {
        javafx.stage.Stage stage = (javafx.stage.Stage) contentArea.getScene().getWindow();
        java.util.List<SearchPaletteDialog.NavEntry> navEntries = java.util.List.of(
            new SearchPaletteDialog.NavEntry("mdi2v-view-dashboard",      "Dashboard",           "Ctrl+1", this::onDashboard),
            new SearchPaletteDialog.NavEntry("mdi2s-sitemap",             "Organigrama",         "Ctrl+2", this::onOrganigrama),
            new SearchPaletteDialog.NavEntry("mdi2p-package-variant",     "Bienes / Inventario", "Ctrl+3", this::onProductos),
            new SearchPaletteDialog.NavEntry("mdi2t-tag-multiple",        "Categorías",          "Ctrl+4", this::onCategorias),
            new SearchPaletteDialog.NavEntry("mdi2s-swap-vertical",       "Movimientos",         "Ctrl+5", this::onMovimientos),
            new SearchPaletteDialog.NavEntry("mdi2b-bell-alert",          "Alertas",             "Ctrl+6", this::onAlertas),
            new SearchPaletteDialog.NavEntry("mdi2f-file-chart",          "Reportes",            "Ctrl+7", this::onReportes),
            new SearchPaletteDialog.NavEntry("mdi2c-chart-line",          "Depreciación",        "Ctrl+8", this::onDepreciacion),
            new SearchPaletteDialog.NavEntry("mdi2c-cog-outline",         "Configuración",       "Ctrl+9", this::onConfiguracion)
        );
        com.sibim.util.AppExecutor.submit(() -> {
            java.util.List<com.sibim.model.Producto> productos;
            try {
                productos = alertProductoService.getAll();
            } catch (Exception e) {
                log.warn("No se pudieron cargar bienes para la paleta de búsqueda", e);
                productos = java.util.List.of();
            }
            final java.util.List<com.sibim.model.Producto> finalProductos = productos;
            javafx.application.Platform.runLater(() ->
                SearchPaletteDialog.show(stage, finalProductos, producto -> {
                    NavigationContext.setPendingProductId(producto.getId());
                    navigateTo("productos", btnProductos);
                }, navEntries)
            );
        });
    }

    private void addNavTooltips() {
        addNavTooltip(btnDashboard,     "Dashboard  (Ctrl+1)");
        addNavTooltip(btnOrganigrama,   "Organigrama  (Ctrl+2)");
        addNavTooltip(btnProductos,     "Bienes / Inventario  (Ctrl+3)");
        addNavTooltip(btnCategorias,    "Categorías  (Ctrl+4)");
        addNavTooltip(btnMovimientos,   "Movimientos  (Ctrl+5)");
        addNavTooltip(btnAlertas,       "Alertas  (Ctrl+6)");
        addNavTooltip(btnReportes,      "Reportes  (Ctrl+7)");
        addNavTooltip(btnDepreciacion,  "Depreciación  (Ctrl+8)");
        addNavTooltip(btnConfiguracion, "Configuración  (Ctrl+9)");
    }

    private void setupNavHover(Button... buttons) {
        for (Button btn : buttons) {
            if (btn == null) continue;
            btn.setOnMouseEntered(e -> {
                if (btn == activeButton) return;
                Timeline prev = navHoverAnims.get(btn);
                if (prev != null) prev.stop();
                Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(btn.translateXProperty(), btn.getTranslateX())),
                    new KeyFrame(Duration.millis(170),
                        new KeyValue(btn.translateXProperty(), 6.0, Interpolator.EASE_OUT))
                );
                navHoverAnims.put(btn, t);
                t.play();
            });
            btn.setOnMouseExited(e -> {
                if (btn == activeButton) return;
                Timeline prev = navHoverAnims.get(btn);
                if (prev != null) prev.stop();
                Timeline t = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(btn.translateXProperty(), btn.getTranslateX())),
                    new KeyFrame(Duration.millis(210),
                        new KeyValue(btn.translateXProperty(), 0.0, Interpolator.EASE_OUT))
                );
                navHoverAnims.put(btn, t);
                t.play();
            });
        }
    }

    private void addNavTooltip(Button btn, String text) {
        if (btn == null) return;
        Tooltip tip = new Tooltip(text);
        tip.setShowDelay(javafx.util.Duration.millis(700));
        tip.setHideDelay(javafx.util.Duration.millis(200));
        Tooltip.install(btn, tip);
    }

    private void refreshCurrentView() {
        // Re-navigate to the current active view to trigger a refresh
        if (activeButton != null) activeButton.fire();
    }

    @FXML
    private void onShowShortcuts() { showShortcutHelp(); }

    private void showShortcutHelp() {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(540);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2k-keyboard-outline", "Atajos de Teclado",
            "Referencia rápida de todos los atajos disponibles en SIBIM",
            "#4338CA", "#3730A3");

        // Section builder helper
        java.util.function.BiFunction<String, String[][], GridPane> makeSection = (title, rows) -> {
            GridPane g = new GridPane();
            g.setHgap(20); g.setVgap(5);
            g.setPadding(new Insets(6, 16, 10, 16));
            Label tit = new Label(title);
            tit.getStyleClass().add("nav-section-label");
            tit.setPadding(new Insets(0, 0, 4, 0));
            g.add(tit, 0, 0, 2, 1);
            for (int i = 0; i < rows.length; i++) {
                Label key = new Label(rows[i][0]);
                key.getStyleClass().add("shortcut-key");
                Label desc = new Label(rows[i][1]);
                desc.getStyleClass().add("shortcut-desc");
                g.add(key, 0, i + 1);
                g.add(desc, 1, i + 1);
            }
            return g;
        };

        GridPane navGrid = makeSection.apply("NAVEGACIÓN", new String[][]{
            {"Ctrl + 1",  "Dashboard"},
            {"Ctrl + 2",  "Organigrama"},
            {"Ctrl + 3",  "Bienes / Inventario"},
            {"Ctrl + 4",  "Categorías"},
            {"Ctrl + 5",  "Movimientos"},
            {"Ctrl + 6",  "Alertas"},
            {"Ctrl + 7",  "Reportes"},
            {"Ctrl + 8",  "Depreciación"},
            {"Ctrl + 9",  "Configuración"},
        });

        GridPane accGrid = makeSection.apply("ACCIONES EN TABLA", new String[][]{
            {"Ctrl + N",        "Nuevo registro (Bienes / Movimientos / Categorías)"},
            {"Ctrl + E",        "Editar fila seleccionada (Bienes / Categorías)"},
            {"Ctrl + I",        "Importar bienes desde CSV (sólo en Bienes)"},
            {"Supr",            "Dar de baja / eliminar fila seleccionada"},
            {"Escape",          "Deseleccionar todas las filas de la tabla"},
            {"Doble clic",      "Ver detalle del registro"},
            {"F5",              "Actualizar datos de la vista actual"},
            {"Ctrl / Shift+clic","Selección múltiple — activa barra de acciones en lote (sólo Bienes)"},
        });

        GridPane busqGrid = makeSection.apply("BÚSQUEDA Y FILTROS", new String[][]{
            {"Escribir",             "Búsqueda en tiempo real (con debounce 280 ms)"},
            {"✕ (botón)",            "Limpiar campo de búsqueda"},
            {"Hoy / Semana / Mes",   "Presets de rango de fechas en Movimientos y Reportes"},
            {"Guardar preset",        "Guarda los filtros activos como preset con nombre (sólo Bienes)"},
            {"Clic en chip de preset","Aplica o elimina un preset de filtros guardado (sólo Bienes)"},
            {"Clic derecho en fila",  "Menú contextual: Ver detalle · Imprimir ficha técnica · (editar/baja si admin)"},
        });

        GridPane sysGrid = makeSection.apply("SISTEMA", new String[][]{
            {"Ctrl+K",      "Búsqueda global / paleta de comandos"},
            {"F1",          "Mostrar esta ayuda de atajos"},
            {"F2",          "Tutorial interactivo del sistema"},
            {"F5 / Ctrl+R", "Actualizar vista actual"},
            {"Ctrl+F",      "Enfocar campo de búsqueda"},
        });

        VBox content = new VBox(0, header, navGrid, new Separator(),
            accGrid, new Separator(), busqGrid,
            new Separator(), sysGrid);

        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    private void checkVencidosOnStart(javafx.scene.Scene scene) {
        DialogUtil.runAsync(
            () -> alertProductoService.getVencidosProximos(7),
            vencidos -> {
                if (!vencidos.isEmpty())
                    NotificacionUtil.advertencia(scene,
                        vencidos.size() + " bien(es) vence(n) en los próximos 7 días — revisa la sección Alertas");
            },
            e -> log.debug("Startup vencidos check failed", e)
        );
    }

    private void checkForUpdate(javafx.scene.Scene scene) {
        com.sibim.util.AppExecutor.submit(() -> {
            com.sibim.util.UpdateChecker.UpdateInfo info = com.sibim.util.UpdateChecker.checkForUpdate();
            if (info != null) {
                javafx.application.Platform.runLater(() ->
                    NotificacionUtil.exitoConAccion(scene,
                        "Nueva versión disponible: v" + info.latestVersion(),
                        "Ver actualización",
                        () -> {
                            try { java.awt.Desktop.getDesktop().browse(new java.net.URI(info.releaseUrl())); }
                            catch (Exception ex) { log.warn("No se pudo abrir el navegador", ex); }
                        }
                    )
                );
            }
        });
    }

    /** Called from child controllers (e.g. Alertas → Movimientos). */
    public void navigateTo(String view) { navigateToView(view); }
}
