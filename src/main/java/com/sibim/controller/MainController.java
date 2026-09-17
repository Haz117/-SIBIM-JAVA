package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.controller.dialogs.ConteoFisicoDialog;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.PrestamoService;
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
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.layout.HBox;
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
    @FXML private Button btnConteoFisico;
    @FXML private Button btnResguardos;
    @FXML private Button btnPrestamos;
    @FXML private Button btnActas;
    @FXML private Button btnConfiguracion;
    @FXML private Button btnAuditoria;
    @FXML private Label alertBadge;
    @FXML private Label loanBadge;
    @FXML private Button btnNotificaciones;
    @FXML private Label notifBadge;
    @FXML private javafx.scene.layout.HBox offlineBanner;
    @FXML private Label offlineBannerLabel;
    @FXML private javafx.scene.control.Button offlineBannerSyncBtn;
    @FXML private Label statusDbLabel;
    @FXML private Tooltip statusDbTooltip;
    @FXML private Label statusUserLabel;
    @FXML private Label statusTimeLabel;
    @FXML private StackPane outerStack;
    
    @FXML private VBox     sidebar;
    @FXML private Region   sidebarBackdrop;
    @FXML private VBox     logoTextBox;
    @FXML private VBox     userInfoVBox;
    @FXML private Button   btnToggleSidebar;
    @FXML private FontIcon statusDotIcon;

    private SidebarManager      sidebarManager;
    private MainStatusBarManager statusBarManager;
    private Object   currentController;
    private Timeline badgeRefresh;
    private Timeline badgePulse;
    private final Map<Button, Timeline> navHoverAnims = new HashMap<>();
    private Timeline clock;
    private Timeline sessionGuard;
    private final ProductoService alertProductoService = new ProductoService();
    private final PrestamoService prestamoService = new PrestamoService();
    private final AuditLogRepository auditRepo = new AuditLogRepository();

    // Session inactivity timeout — 30 minutes
    private static final long INACTIVITY_TIMEOUT_MS = 30 * 60_000L;
    private static final long INACTIVITY_WARN_MS    = INACTIVITY_TIMEOUT_MS - 5 * 60_000L;
    private long    lastActivityMs    = System.currentTimeMillis();
    private boolean inactivityWarned  = false;
    private Dialog<javafx.scene.control.ButtonType> activeInactivityDialog;
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
        statusBarManager.update();
    }

    @FXML
    public void initialize() {
        instance = this;
        sidebarManager = new SidebarManager(
            sidebar, sidebarBackdrop, outerStack,
            logoTextBox, userInfoVBox, btnToggleSidebar,
            java.util.List.of(btnDashboard, btnOrganigrama, btnProductos, btnCategorias,
                btnMovimientos, btnAlertas, btnReportes, btnDepreciacion, btnConteoFisico,
                btnResguardos, btnPrestamos, btnActas, btnConfiguracion, btnAuditoria));
        statusBarManager = new MainStatusBarManager(
            offlineBanner, offlineBannerLabel, offlineBannerSyncBtn,
            statusDbLabel, statusDbTooltip, statusUserLabel, statusTimeLabel, statusDotIcon);
        if (SessionManager.getCurrentUser() != null) {
            String nombre = SessionManager.getCurrentUser().getNombre();
            userNameLabel.setText(nombre);
            userRolLabel.setText(SessionManager.getCurrentUser().getRol().getEtiqueta());
            if (userAvatarLabel != null && nombre != null && !nombre.isBlank())
                userAvatarLabel.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }
        setupUserCardMenu();
        NotificationCenter.setup(btnNotificaciones, notifBadge, alertProductoService, prestamoService, this::navigateTo);
        navigateTo("dashboard", btnDashboard);
        densityIndex = DENSITY_PREFS.getInt("index", 1);
        applyDensityClass();
        applyTextScaleClass();
        addNavTooltips();
        setupNavHover(btnDashboard, btnOrganigrama, btnProductos, btnCategorias,
                      btnMovimientos, btnAlertas, btnReportes, btnDepreciacion, btnConteoFisico,
                      btnResguardos, btnPrestamos, btnActas,
                      btnConfiguracion, btnAuditoria);

        if (btnAuditoria != null) {
            btnAuditoria.setVisible(SessionManager.isAdmin());
            btnAuditoria.setManaged(SessionManager.isAdmin());
        }

        // Single scene listener — consolidates what were three separate listeners.
        // Non-critical startup tasks (badge, vencidos, update check) are staggered
        // so they don't compete with the dashboard's 6 parallel DB queries at boot.
        contentArea.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                setupKeyboardShortcuts(scene);
                scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> { lastActivityMs = System.currentTimeMillis(); inactivityWarned = false; });
                scene.addEventFilter(KeyEvent.KEY_PRESSED,     e -> { lastActivityMs = System.currentTimeMillis(); inactivityWarned = false; });
                javafx.application.Platform.runLater(sidebarManager::setup);
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
                    badgeDelay.setOnFinished(e -> { loadAlertBadge(); loadLoanBadge(); refreshNotifBadge(); });
                    badgeDelay.play();
                    // Vencidos check: 1.5 s (informational toast, not blocking)
                    javafx.animation.PauseTransition vencidosDelay =
                        new javafx.animation.PauseTransition(Duration.millis(1500));
                    vencidosDelay.setOnFinished(e -> checkVencidosOnStart(scene));
                    vencidosDelay.play();
                    // Préstamos check: 2 s (vencidos + próximos a vencer)
                    javafx.animation.PauseTransition prestamosDelay =
                        new javafx.animation.PauseTransition(Duration.millis(2000));
                    prestamosDelay.setOnFinished(e -> checkPrestamosOnStart(scene));
                    prestamosDelay.play();
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
        badgeRefresh = new Timeline(new KeyFrame(Duration.minutes(3),
            e -> { loadAlertBadge(); loadLoanBadge(); refreshNotifBadge(); statusBarManager.update(); }));
        badgeRefresh.setCycleCount(Timeline.INDEFINITE);
        badgeRefresh.play();

        statusBarManager.update();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> statusBarManager.updateTime()));
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
    @FXML private void onResguardos()    { navigateTo("resguardos",   btnResguardos); }
    @FXML private void onPrestamos()     { navigateTo("prestamos",    btnPrestamos); }
    @FXML private void onActas()         { navigateTo("actas",        btnActas); }
    @FXML private void onConfiguracion() { navigateTo("configuracion", btnConfiguracion); }

    @FXML
    private void onConteoFisico() {
        javafx.scene.Scene scene = contentArea.getScene();
        if (scene == null) return;
        DialogUtil.runAsyncWithProgress(scene, "Cargando bienes para el conteo…",
            () -> new ProductoService().getAllFiltrado(null, null, null, null, EstadoProducto.ACTIVO),
            productos -> {
                if (productos.isEmpty()) {
                    NotificacionUtil.advertencia(scene, "No hay bienes activos registrados en el inventario");
                    return;
                }
                if (productos.size() > 150 && !ConfirmacionUtil.confirmar("Conteo grande",
                        "Vas a iniciar un conteo físico de " + productos.size() + " bienes.\n"
                        + "Para conteos más manejables, inicia desde Bienes filtrado por área o categoría.\n\n"
                        + "¿Continuar con los " + productos.size() + " bienes?")) return;
                ConteoFisicoDialog.show(productos, new MovimientoService(), () -> refreshCurrentView());
            },
            e -> NotificacionUtil.error(scene, "No se pudo cargar los bienes para el conteo")
        );
    }
    @FXML private void onAuditoria()     { navigateTo("auditoria",     btnAuditoria); }

    @FXML
    private void onAcercaDe() {
        MainAcercaDe.show();
    }

    @FXML
    private void onShowTutorial() {
        TutorialOverlay.show(outerStack);
    }

    private void setupUserCardMenu() {
        MainUserMenu.setup(userInfoVBox, contentArea, this::onLogout);
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
        if (currentController instanceof DashboardController dc) dc.stopAutoRefresh();
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
        if ("auditoria".equals(view))  { navigateTo(view, btnAuditoria);  return; }
        if ("resguardos".equals(view)) { navigateTo(view, btnResguardos); return; }
        if ("prestamos".equals(view))  { navigateTo(view, btnPrestamos);  return; }
        if ("actas".equals(view))      { navigateTo(view, btnActas);      return; }
        navigateTo(view, resolveNavigationButton(view,
            btnDashboard, btnOrganigrama, btnProductos, btnCategorias,
            btnMovimientos, btnAlertas, btnReportes, btnConfiguracion,
            btnDepreciacion, btnDashboard));
    }

    /** Return the sidebar Button for a given view — used by TutorialOverlay for ring positioning. */
    public Button getNavButton(String view) {
        if ("auditoria".equals(view))  return btnAuditoria;
        if ("resguardos".equals(view)) return btnResguardos;
        if ("prestamos".equals(view))  return btnPrestamos;
        if ("actas".equals(view))      return btnActas;
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
            Timeline pendingHover = navHoverAnims.remove(button);
            if (pendingHover != null) pendingHover.stop();
            button.setTranslateX(0);
            sidebarManager.setActive(button);

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/" + view + ".fxml")));
            Node node = loader.load();
            com.sibim.util.AccessibilityUtils.applyAccessibleTextFromTooltips(node);

            if (currentController instanceof AlertasController ac) ac.stopAutoRefresh();
            if (currentController instanceof DashboardController dc) dc.stopAutoRefresh();
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

    @FXML
    private void onSyncNow() {
        if (offlineBannerSyncBtn != null) {
            offlineBannerSyncBtn.setDisable(true);
            offlineBannerSyncBtn.setText("Sincronizando…");
        }
        if (offlineBannerLabel != null) {
            int pending = com.sibim.db.offline.SyncService.pendingCount();
            offlineBannerLabel.setText(pending > 0
                ? "Sincronizando " + pending + " cambio(s) pendiente(s)…"
                : "Conectando con el servidor…");
        }
        com.sibim.util.AppExecutor.submit(() -> {
            com.sibim.db.offline.SyncService.syncNow();
            javafx.application.Platform.runLater(() -> {
                statusBarManager.update();
                if (offlineBannerSyncBtn != null) {
                    offlineBannerSyncBtn.setDisable(false);
                    offlineBannerSyncBtn.setText("Sincronizar ahora");
                }
            });
        });
    }

    // ── Table density ─────────────────────────────────────────────────────────
    private static final java.util.prefs.Preferences DENSITY_PREFS =
        java.util.prefs.Preferences.userRoot().node("sibim/ui/density");
    private static final String[] DENSITY_CLASSES = { "density-compact", "", "density-comfortable" };
    private static final String[] DENSITY_LABELS  = { "Comp.", "Normal", "Cómod." };
    private static final String[] DENSITY_ICONS   = { "mdi2v-view-headline", "mdi2v-view-list", "mdi2v-view-module" };
    @FXML private Button btnDensity;
    private int densityIndex = 1;

    @FXML
    private void onToggleDensity() {
        densityIndex = (densityIndex + 1) % 3;
        DENSITY_PREFS.putInt("index", densityIndex);
        applyDensityClass();
    }

    private void applyDensityClass() {
        contentArea.getStyleClass().removeAll("density-compact", "density-comfortable");
        if (!DENSITY_CLASSES[densityIndex].isEmpty())
            contentArea.getStyleClass().add(DENSITY_CLASSES[densityIndex]);
        if (btnDensity != null) {
            btnDensity.setText(DENSITY_LABELS[densityIndex]);
            if (btnDensity.getGraphic() instanceof FontIcon fi)
                fi.setIconLiteral(DENSITY_ICONS[densityIndex]);
        }
    }

    // ── Text size (accessibility) ───────────────────────────────────────────
    @FXML private Button btnTextSize;

    @FXML
    private void onToggleTextSize() {
        com.sibim.util.AccessibilityUtils.cycleTextScaleIndex();
        applyTextScaleClass();
    }

    private void applyTextScaleClass() {
        com.sibim.util.AccessibilityUtils.applyCurrentTextScaleClass(contentArea);
        if (btnTextSize != null) {
            int idx = com.sibim.util.AccessibilityUtils.getTextScaleIndex();
            btnTextSize.setText(com.sibim.util.AccessibilityUtils.TEXT_SCALE_LABELS[idx]);
        }
    }

    @FXML
    private void onToggleSidebar() {
        sidebarManager.toggle();
    }

    private void checkInactivity() {
        long idle = System.currentTimeMillis() - lastActivityMs;
        if (idle > INACTIVITY_TIMEOUT_MS) {
            log.info("Sesión cerrada por inactividad");
            inactivityWarned = false;
            // If the warning dialog is still open when this 1-min tick catches
            // the same timeout independently, dismiss it WITHOUT a result so
            // its own showAndWait().ifPresent(...) below is skipped — otherwise
            // its countdown reaches zero moments later and redundantly repeats
            // the whole logout+showLogin sequence on top of the one this branch
            // is about to run, which can reload the login screen out from under
            // someone who already started typing on the first one.
            if (activeInactivityDialog != null) {
                activeInactivityDialog.close();
                activeInactivityDialog = null;
            }
            stopTimers();
            com.sibim.model.Usuario me = SessionManager.getCurrentUser();
            if (me != null)
                auditRepo.log("sesion", me.getId(), me.getNombre(),
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
            // Closing with no result here would make showAndWait() return
            // Optional.empty(), skipping both branches below — the dialog
            // would just vanish with the session left dangling (neither
            // logged out nor extended) until the 1-min sessionGuard tick
            // catches up. Set the same result "Cerrar sesión" would so the
            // countdown reaching zero actually logs out, matching what the
            // dialog tells the user will happen.
            if (msLeft[0] == 0) { dlg.setResult(btnLogout); dlg.close(); }
        }));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
        activeInactivityDialog = dlg;

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
                // Same cleanup onLogout() does — without stopping sessionGuard
                // and clearing instance, the old Timeline keeps ticking every
                // minute after this, sees the same stale (>30 min) idle time
                // forever, and forces MainApp.showLogin() again on whatever
                // screen the user is on next (e.g. reloading the login form
                // while they're mid-typing), which looks like "salir" did
                // nothing useful.
                stopTimers();
                instance = null;
                SessionManager.logout();
                try { MainApp.showLogin(); } catch (Exception ex) { log.error("Error al cerrar sesión", ex); }
            }
        });
        countdown.stop();
        activeInactivityDialog = null;
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
        a.put(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.CONTROL_DOWN), () -> { if (SessionManager.isAdmin()) onAuditoria(); });
        a.put(new KeyCodeCombination(KeyCode.G, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN), () -> onResguardos());
        a.put(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN), () -> onPrestamos());
        a.put(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN), () -> onActas());
        a.put(new KeyCodeCombination(KeyCode.F5),                                   () -> refreshCurrentView());
        a.put(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),      () -> refreshCurrentView());
        a.put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),      () -> focusCurrentSearch(scene));
        a.put(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),      this::onCommandPalette);
        a.put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), () -> {
            com.sibim.session.NavigationContext.setPendingNuevoBien();
            onProductos();
        });
        a.put(new KeyCodeCombination(KeyCode.F1), () -> MainShortcutHelpDialog.show());
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
        java.util.List<SearchPaletteDialog.NavEntry> baseEntries = new java.util.ArrayList<>(java.util.List.of(
            new SearchPaletteDialog.NavEntry("mdi2v-view-dashboard",          "Dashboard",           "Ctrl+1", this::onDashboard),
            new SearchPaletteDialog.NavEntry("mdi2s-sitemap",                 "Organigrama",         "Ctrl+2", this::onOrganigrama),
            new SearchPaletteDialog.NavEntry("mdi2p-package-variant",         "Bienes / Inventario", "Ctrl+3", this::onProductos),
            new SearchPaletteDialog.NavEntry("mdi2t-tag-multiple",            "Categorías",          "Ctrl+4", this::onCategorias),
            new SearchPaletteDialog.NavEntry("mdi2s-swap-vertical",           "Movimientos",         "Ctrl+5", this::onMovimientos),
            new SearchPaletteDialog.NavEntry("mdi2b-bell-alert",              "Alertas",             "Ctrl+6", this::onAlertas),
            new SearchPaletteDialog.NavEntry("mdi2f-file-chart",              "Reportes",            "Ctrl+7", this::onReportes),
            new SearchPaletteDialog.NavEntry("mdi2c-chart-line",              "Depreciación",        "Ctrl+8", this::onDepreciacion),
            new SearchPaletteDialog.NavEntry("mdi2c-clipboard-account-outline","Resguardos",         "Ctrl+Alt+G", this::onResguardos),
            new SearchPaletteDialog.NavEntry("mdi2s-swap-horizontal",         "Préstamos",          "Ctrl+Alt+P", this::onPrestamos),
            new SearchPaletteDialog.NavEntry("mdi2s-swap-horizontal-bold",    "Actas E/R",          "Ctrl+Alt+A", this::onActas),
            new SearchPaletteDialog.NavEntry("mdi2c-cog-outline",             "Configuración",       "Ctrl+9", this::onConfiguracion)
        ));
        if (com.sibim.session.SessionManager.isAdmin())
            baseEntries.add(new SearchPaletteDialog.NavEntry("mdi2h-history", "Auditoría", "Ctrl+0", this::onAuditoria));
        java.util.List<SearchPaletteDialog.NavEntry> navEntries = java.util.List.copyOf(baseEntries);
        com.sibim.util.AppExecutor.submit(() -> {
            java.util.List<com.sibim.model.Producto>  productos;
            java.util.List<com.sibim.model.Resguardo> resguardos;
            java.util.List<com.sibim.model.Prestamo>  prestamos;
            try { productos  = alertProductoService.getAll(); }
            catch (Exception e) { log.warn("Palette: no se pudieron cargar bienes", e);      productos  = java.util.List.of(); }
            try { resguardos = new com.sibim.service.ResguardoService().getAll(); }
            catch (Exception e) { log.warn("Palette: no se pudieron cargar resguardos", e);  resguardos = java.util.List.of(); }
            try { prestamos  = prestamoService.getAll(); }
            catch (Exception e) { log.warn("Palette: no se pudieron cargar préstamos", e);   prestamos  = java.util.List.of(); }
            final var fProductos  = productos;
            final var fResguardos = resguardos;
            final var fPrestamos  = prestamos;
            javafx.application.Platform.runLater(() ->
                SearchPaletteDialog.show(stage, fProductos,
                    producto -> {
                        NavigationContext.setPendingProductId(producto.getId());
                        navigateTo("productos", btnProductos);
                    },
                    fResguardos,
                    rsg -> navigateTo("resguardos", btnResguardos),
                    fPrestamos,
                    prs -> navigateTo("prestamos", btnPrestamos),
                    navEntries)
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
        addNavTooltip(btnConteoFisico,  "Conteo físico del inventario");
        addNavTooltip(btnResguardos,    "Resguardos  (Ctrl+Alt+G)");
        addNavTooltip(btnPrestamos,     "Préstamos  (Ctrl+Alt+P)");
        addNavTooltip(btnActas,         "Actas E/R  (Ctrl+Alt+A)");
        addNavTooltip(btnConfiguracion, "Configuración  (Ctrl+9)");
        addNavTooltip(btnAuditoria,     "Auditoría  (Ctrl+0)");
    }

    private void setupNavHover(Button... buttons) {
        for (Button btn : buttons) {
            if (btn == null) continue;
            btn.setOnMouseEntered(e -> {
                if (btn == sidebarManager.getActive()) return;
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
                if (btn == sidebarManager.getActive()) return;
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
        if (sidebarManager.getActive() != null) sidebarManager.getActive().fire();
    }

    @FXML
    private void onShowShortcuts() { MainShortcutHelpDialog.show(); }

    private void refreshNotifBadge() {
        NotificationCenter.refreshBadge(notifBadge, alertProductoService, prestamoService);
    }

    private void loadLoanBadge() {
        DialogUtil.runAsync(
            () -> prestamoService.countVencidos(),
            count -> {
                if (loanBadge == null) return;
                if (count > 0) {
                    loanBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                    boolean wasHidden = !loanBadge.isVisible();
                    loanBadge.setVisible(true);
                    loanBadge.setManaged(true);
                    if (wasHidden) {
                        ScaleTransition pop = new ScaleTransition(Duration.millis(320), loanBadge);
                        pop.setFromX(0.3); pop.setFromY(0.3);
                        pop.setToX(1.0);   pop.setToY(1.0);
                        pop.setInterpolator(Interpolator.EASE_OUT);
                        pop.play();
                    } else {
                        AnimationUtils.pulse(loanBadge, 2);
                    }
                } else {
                    loanBadge.setVisible(false);
                    loanBadge.setManaged(false);
                }
            },
            e -> { /* badge is decorative */ }
        );
    }

    private void checkPrestamosOnStart(javafx.scene.Scene scene) {
        DialogUtil.runAsync(
            () -> {
                int vencidos = prestamoService.getVencidos().size();
                int proximos = prestamoService.getProximosAVencer(3).size();
                return new int[]{vencidos, proximos};
            },
            counts -> {
                int vencidos = counts[0], proximos = counts[1];
                if (vencidos > 0)
                    NotificacionUtil.advertencia(scene,
                        vencidos + " préstamo(s) vencido(s) — revisa la sección Préstamos");
                else if (proximos > 0)
                    NotificacionUtil.advertencia(scene,
                        proximos + " préstamo(s) vencen en los próximos 3 días");
            },
            e -> log.debug("Startup préstamos check failed", e)
        );
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
