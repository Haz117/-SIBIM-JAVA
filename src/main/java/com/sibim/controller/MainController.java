package com.sibim.controller;

import com.sibim.MainApp;
import com.sibim.controller.dialogs.ConteoFisicoDialog;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import com.sibim.model.Prestamo;
import com.sibim.model.Usuario;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ResguardoService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.AppExecutor;
import com.sibim.util.BarcodeScanner;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.TutorialOverlay;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Dialog;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;

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
    @FXML private Button btnComodatos;
    @FXML private Button btnActas;
    @FXML private Button btnConfiguracion;
    @FXML private Button btnAuditoria;
    @FXML private Button btnGlobalSearch;
    @FXML private VBox secNavegacion;
    @FXML private VBox secOperaciones;
    @FXML private VBox secControl;
    @FXML private VBox secSistema;
    @FXML private Label alertBadge;
    @FXML private Label loanBadge;
    @FXML private Button btnNotificaciones;
    @FXML private Label notifBadge;
    @FXML private HBox offlineBanner;
    @FXML private Label offlineBannerLabel;
    @FXML private Button offlineBannerSyncBtn;
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
    private SidebarSections     sidebarSections;
    private NavRegistry         navRegistry;
    private MainStatusBarManager statusBarManager;
    private Object   currentController;
    private Timeline badgeRefresh;
    private final Map<Button, Timeline> navHoverAnims = new HashMap<>();
    private Timeline clock;
    private Timeline sessionGuard;
    private final ProductoService alertProductoService = new ProductoService();
    private final PrestamoService prestamoService = new PrestamoService();
    private final AuditLogRepository auditRepo = new AuditLogRepository();

    private static final long INACTIVITY_WARN_WINDOW_MS = 5 * 60_000L; // 5-min countdown before logout
    private long    lastActivityMs    = System.currentTimeMillis();
    private boolean inactivityWarned  = false;
    private Dialog<javafx.scene.control.ButtonType> activeInactivityDialog;
    private boolean startupTasksScheduled = false;
    private MainBadgeManager badgeManager;
    private MainStartupChecks startupChecks;

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
        navRegistry = buildNavRegistry();
        sidebarSections = new SidebarSections(Map.of(
            NavSection.NAVEGACION,  secNavegacion,
            NavSection.OPERACIONES, secOperaciones,
            NavSection.CONTROL,     secControl,
            NavSection.SISTEMA,     secSistema),
            SidebarSections.defaultPrefs(), true);
        // A folded section shows the sum of its items' badges (alerts / overdue loans) on its header.
        sidebarSections.bindBadge(NavSection.OPERACIONES, alertBadge);
        sidebarSections.bindBadge(NavSection.CONTROL, loanBadge);
        List<Button> navButtons = new ArrayList<>(navRegistry.buttons());
        navButtons.add(btnGlobalSearch);
        sidebarManager = new SidebarManager(
            sidebar, sidebarBackdrop, outerStack,
            logoTextBox, userInfoVBox, btnToggleSidebar, navButtons);
        sidebarManager.setSections(sidebarSections);
        statusBarManager = new MainStatusBarManager(
            offlineBanner, offlineBannerLabel, offlineBannerSyncBtn,
            statusDbLabel, statusDbTooltip, statusUserLabel, statusTimeLabel, statusDotIcon);
        badgeManager   = new MainBadgeManager(alertBadge, loanBadge, alertProductoService, prestamoService);
        startupChecks  = new MainStartupChecks(alertProductoService, prestamoService);
        startupChecks.cleanupStaleTempFiles();
        if (SessionManager.getCurrentUser() != null) {
            String nombre = SessionManager.getCurrentUser().getNombre();
            userNameLabel.setText(nombre);
            userRolLabel.setText(SessionManager.getCurrentUser().getRol().getEtiqueta());
            // the name label ellipsizes long names: keep the full text one hover away
            userNameLabel.setTooltip(new javafx.scene.control.Tooltip(nombre + " — " + userRolLabel.getText()));
            if (userAvatarLabel != null && nombre != null && !nombre.isBlank())
                userAvatarLabel.setText(String.valueOf(nombre.charAt(0)).toUpperCase());
        }
        setupUserCardMenu();
        NotificationCenter.setup(btnNotificaciones, notifBadge, alertProductoService, prestamoService, this::navigateTo);
        navigateTo("dashboard", btnDashboard);
        densityIndex = DENSITY_PREFS.getInt("index", 1);
        applyDensityClass();
        applyTextScaleClass();
        navRegistry.tuneTooltips(Duration.millis(700), Duration.millis(200));
        setupNavHover(navRegistry.buttons().toArray(new Button[0]));

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
                BarcodeScanner.attach(scene, this::handleBarcodeScan);
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
                    badgeDelay.setOnFinished(e -> { badgeManager.loadAlertBadge(); badgeManager.loadLoanBadge(); refreshNotifBadge(); });
                    badgeDelay.play();
                    // Vencidos check: 1.5 s (informational toast, not blocking)
                    javafx.animation.PauseTransition vencidosDelay =
                        new javafx.animation.PauseTransition(Duration.millis(1500));
                    vencidosDelay.setOnFinished(e -> startupChecks.checkVencidosOnStart(scene));
                    vencidosDelay.play();
                    // Préstamos check: 2 s (vencidos + próximos a vencer)
                    javafx.animation.PauseTransition prestamosDelay =
                        new javafx.animation.PauseTransition(Duration.millis(2000));
                    prestamosDelay.setOnFinished(e -> startupChecks.checkPrestamosOnStart(scene));
                    prestamosDelay.play();
                    // Update check: 3 s (network request, lowest priority)
                    javafx.animation.PauseTransition updateDelay =
                        new javafx.animation.PauseTransition(Duration.millis(3000));
                    updateDelay.setOnFinished(e -> startupChecks.checkForUpdate(scene));
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
            e -> { badgeManager.loadAlertBadge(); badgeManager.loadLoanBadge(); refreshNotifBadge(); statusBarManager.update(); }));
        badgeRefresh.setCycleCount(Timeline.INDEFINITE);
        badgeRefresh.play();

        statusBarManager.update();
        clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> statusBarManager.updateTime()));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();

        sessionGuard = new Timeline(new KeyFrame(Duration.minutes(1), e -> tickSessionGuard()));
        sessionGuard.setCycleCount(Timeline.INDEFINITE);
        sessionGuard.play();
    }


    /** One handler for every sidebar item — the registry knows what each button does. */
    @FXML
    private void onNavItem(javafx.event.ActionEvent e) {
        if (e.getSource() instanceof Button b) navRegistry.byButton(b).ifPresent(NavItem::run);
    }

    /** Action item (opens a dialog instead of navigating) — registered in buildNavRegistry(). */
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

    @FXML
    private void onAcercaDe() {
        MainAcercaDe.show();
    }

    @FXML
    private void onShowTutorial() {
        TutorialOverlay.show(outerStack);
    }

    private void setupUserCardMenu() {
        MainUserMenu.setup(userInfoVBox, contentArea, this::onLogout, this::onShowTutorial, this::onAcercaDe);
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

    void stopTimers() {
        if (badgeRefresh != null) badgeRefresh.stop();
        if (clock != null) clock.stop();
        if (sessionGuard != null) sessionGuard.stop();
        if (badgeManager != null) badgeManager.stopBadgePulse();
        if (currentController instanceof AlertasController ac) ac.stopAutoRefresh();
        if (currentController instanceof DashboardController dc) dc.stopAutoRefresh();
    }

    /** Every sidebar destination, described once: view, section, shortcut, palette entry, admin-only. */
    private NavRegistry buildNavRegistry() {
        KeyCombination.Modifier[] ctrlAlt = { KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN };
        return new NavRegistry(List.of(
            page("dashboard",     NavSection.NAVEGACION,  btnDashboard,     KeyCode.DIGIT1),
            page("organigrama",   NavSection.NAVEGACION,  btnOrganigrama,   KeyCode.DIGIT2),
            page("productos",     NavSection.NAVEGACION,  btnProductos,     KeyCode.DIGIT3).withPaletteLabel("Bienes / Inventario"),
            page("categorias",    NavSection.NAVEGACION,  btnCategorias,    KeyCode.DIGIT4),
            page("movimientos",   NavSection.OPERACIONES, btnMovimientos,   KeyCode.DIGIT5),
            page("alertas",       NavSection.OPERACIONES, btnAlertas,       KeyCode.DIGIT6),
            page("reportes",      NavSection.OPERACIONES, btnReportes,      KeyCode.DIGIT7),
            page("depreciacion",  NavSection.OPERACIONES, btnDepreciacion,  KeyCode.DIGIT8),
            action("conteo",      NavSection.OPERACIONES, btnConteoFisico,  this::onConteoFisico, KeyCode.C, ctrlAlt).hiddenFromPalette(),
            page("resguardos",    NavSection.CONTROL,     btnResguardos,    KeyCode.G, ctrlAlt),
            page("prestamos",     NavSection.CONTROL,     btnPrestamos,     KeyCode.P, ctrlAlt),
            page("comodatos",     NavSection.CONTROL,     btnComodatos,     KeyCode.O, ctrlAlt),
            page("actas",         NavSection.CONTROL,     btnActas,         KeyCode.A, ctrlAlt),
            page("configuracion", NavSection.SISTEMA,     btnConfiguracion, KeyCode.DIGIT9),
            page("auditoria",     NavSection.SISTEMA,     btnAuditoria,     KeyCode.DIGIT0).restrictedToAdmin()));
    }

    /** A destination that loads {@code /fxml/<view>.fxml}. Ctrl+key unless other modifiers are given. */
    private NavItem page(String view, NavSection section, Button button, KeyCode key, KeyCombination.Modifier... mods) {
        return NavItem.of(view, section, button, () -> navigateTo(view, button), accelerator(key, mods));
    }

    /** A destination that runs something else (e.g. opens a dialog) instead of loading a view. */
    private NavItem action(String view, NavSection section, Button button, Runnable action,
                           KeyCode key, KeyCombination.Modifier... mods) {
        return NavItem.of(view, section, button, action, accelerator(key, mods));
    }

    private static KeyCombination accelerator(KeyCode key, KeyCombination.Modifier... mods) {
        return new KeyCodeCombination(key,
            mods.length == 0 ? new KeyCombination.Modifier[] { KeyCombination.CONTROL_DOWN } : mods);
    }

    /** Navigate programmatically by view name — used by TutorialOverlay and shortcuts. */
    public void navigateToView(String view) {
        navRegistry.byView(view).ifPresentOrElse(NavItem::run, () -> navigateTo(view, btnDashboard));
    }

    /** Return the sidebar Button for a given view — used by TutorialOverlay for ring positioning. */
    public Button getNavButton(String view) {
        return navRegistry.byView(view).map(NavItem::button).orElse(null);
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
            sidebarSections.reveal(button);      // unfold its section first so the active-tab marker lands right
            sidebarManager.setActive(button);
            sidebarSections.markActive(button);

            FXMLLoader loader = new FXMLLoader(Objects.requireNonNull(
                getClass().getResource("/fxml/" + view + ".fxml")));
            Node node = loader.load();
            AccessibilityUtils.applyAccessibleTextFromTooltips(node);
            com.sibim.util.ResponsiveHeader.installAll(node);

            if (currentController instanceof Refreshable r) r.stopAutoRefresh();
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

    @FXML
    private void onSyncNow() {
        if (offlineBannerSyncBtn != null) {
            offlineBannerSyncBtn.setDisable(true);
            offlineBannerSyncBtn.setText("Sincronizando…");
        }
        if (offlineBannerLabel != null) {
            int pending = SyncService.pendingCount();
            offlineBannerLabel.setText(pending > 0
                ? "Sincronizando " + pending + " cambio(s) pendiente(s)…"
                : "Conectando con el servidor…");
        }
        statusBarManager.setConnecting(true);
        AppExecutor.submit(() -> {
            SyncService.syncNow();
            javafx.application.Platform.runLater(() -> {
                statusBarManager.setConnecting(false);
                if (offlineBannerSyncBtn != null) {
                    offlineBannerSyncBtn.setDisable(false);
                    offlineBannerSyncBtn.setText("Sincronizar ahora");
                }
            });
        });
    }

    // ── Table density ─────────────────────────────────────────────────────────
    private static final Preferences DENSITY_PREFS =
        Preferences.userRoot().node("sibim/ui/density");
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
        AccessibilityUtils.cycleTextScaleIndex();
        applyTextScaleClass();
    }

    private void applyTextScaleClass() {
        AccessibilityUtils.applyCurrentTextScaleClass(contentArea);
        if (btnTextSize != null) {
            int idx = AccessibilityUtils.getTextScaleIndex();
            btnTextSize.setText(AccessibilityUtils.TEXT_SCALE_LABELS[idx]);
        }
    }

    @FXML
    private void onToggleSidebar() {
        sidebarManager.toggle();
    }

    private long inactivityTimeoutMs() {
        try {
            int minutes = Integer.parseInt(
                new ConfiguracionRepository()
                    .get("inactividad_timeout_minutos", "30"));
            return Math.max(6, minutes) * 60_000L;
        } catch (Exception e) {
            return 30 * 60_000L;
        }
    }

    /** Every minute: reads the configured timeout and re-checks the signed-in
     *  account off the FX thread (both are database reads — a slow connection
     *  must not freeze the window), then acts on the FX thread. */
    private void tickSessionGuard() {
        Usuario me = SessionManager.getCurrentUser();
        AppExecutor.submit(() -> {
            long timeoutMs = inactivityTimeoutMs();
            String motivo = motivoCierreCuenta(me);
            javafx.application.Platform.runLater(() -> {
                if (SessionManager.getCurrentUser() != me) return; // already logged out
                if (motivo != null) cerrarSesionPorCambioDeCuenta(me, motivo);
                else checkInactivity(timeoutMs);
            });
        });
    }

    /** Only online: offline there is no authoritative copy of the account,
     *  and a failed read (connection trouble) is not a reason to log out. */
    private static String motivoCierreCuenta(Usuario me) {
        if (me == null || me.getId() == null || DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return null;
        try {
            return SessionManager.motivoCierre(me, new com.sibim.repository.UsuarioRepository().findById(me.getId()));
        } catch (Exception e) {
            log.debug("No se pudo verificar la cuenta en sesión; se reintenta en el próximo ciclo", e);
            return null;
        }
    }

    private void cerrarSesionPorCambioDeCuenta(Usuario me, String motivo) {
        log.info("Sesión de {} cerrada: {}", me.getUsername(), motivo);
        if (activeInactivityDialog != null) { activeInactivityDialog.close(); activeInactivityDialog = null; }
        stopTimers();
        instance = null;
        auditRepo.log("sesion", me.getId(), me.getNombre(), "logout", "Cierre automático: " + motivo);
        SessionManager.logout();
        try { MainApp.showLogin(); }
        catch (Exception e) { log.error("No se pudo volver a la pantalla de login", e); }
        javafx.scene.control.Alert aviso = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.INFORMATION, motivo);
        aviso.setTitle("Sesión cerrada");
        aviso.setHeaderText("Tu sesión se cerró");
        DialogUtil.applyOwner(aviso);
        DialogUtil.applyStylesheet(aviso.getDialogPane());
        aviso.show();
    }

    private void checkInactivity(long timeoutMs) {
        long idle = System.currentTimeMillis() - lastActivityMs;
        if (idle > timeoutMs) {
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
            Usuario me = SessionManager.getCurrentUser();
            if (me != null)
                auditRepo.log("sesion", me.getId(), me.getNombre(),
                    "logout", "Cierre automático por inactividad");
            SessionManager.logout();
            try { MainApp.showLogin(); }
            catch (Exception e) { log.error("Error al cerrar sesión por inactividad", e); }
        } else if (!inactivityWarned && idle > timeoutMs - INACTIVITY_WARN_WINDOW_MS) {
            inactivityWarned = true;
            javafx.application.Platform.runLater(this::showInactivityWarning);
        }
    }

    private void showInactivityWarning() {
        var btnContinuar = new javafx.scene.control.ButtonType("Continuar sesión", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        var btnLogout    = new javafx.scene.control.ButtonType("Cerrar sesión",    javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<javafx.scene.control.ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Sesión por expirar");
        dlg.getDialogPane().getButtonTypes().addAll(btnContinuar, btnLogout);
        dlg.getDialogPane().setPrefWidth(400);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.setOnCloseRequest(javafx.event.Event::consume);

        HBox header = DialogUtil.gradientHeader("mdi2t-timer-outline", "Sesión inactiva",
            "Tu sesión cerrará automáticamente por inactividad.", AppColors.WARNING_D, AppColors.WARNING_DD);
        Label lblCountdown = new Label("5:00");
        lblCountdown.getStyleClass().add("inactivity-countdown");
        VBox body = buildInactivityBody(lblCountdown);

        AnimationUtils.staggeredFadeInUp(List.of(header, body), 260, 70);
        dlg.getDialogPane().setContent(new VBox(header, body));

        long[] msLeft = { INACTIVITY_WARN_WINDOW_MS };
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            msLeft[0] = Math.max(0, msLeft[0] - 1000);
            long mins = msLeft[0] / 60_000;
            long secs = (msLeft[0] % 60_000) / 1000;
            lblCountdown.setText(String.format("%d:%02d", mins, secs));
            // Force logout result so countdown reaching zero actually logs out
            // instead of leaving the session dangling (showAndWait returning empty).
            if (msLeft[0] == 0) { dlg.setResult(btnLogout); dlg.close(); }
        }));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
        activeInactivityDialog = dlg;

        dlg.showAndWait().ifPresent(r -> handleInactivityResult(r, btnContinuar, countdown));
        countdown.stop();
        activeInactivityDialog = null;
    }

    private VBox buildInactivityBody(Label lblCountdown) {
        Label lblHint = new Label("Presiona \"Continuar sesión\" para seguir trabajando.");
        lblHint.getStyleClass().add("muted-sm");
        lblHint.setWrapText(true);
        VBox body = new VBox(14, lblCountdown, lblHint);
        body.setPadding(new Insets(24, 24, 20, 24));
        body.setAlignment(javafx.geometry.Pos.CENTER);
        return body;
    }

    private void handleInactivityResult(javafx.scene.control.ButtonType r,
                                        javafx.scene.control.ButtonType btnContinuar,
                                        Timeline countdown) {
        countdown.stop();
        if (r == btnContinuar) {
            lastActivityMs = System.currentTimeMillis();
            inactivityWarned = false;
            NotificacionUtil.info(contentArea.getScene(), "Sesión extendida — bienvenido de vuelta");
        } else {
            log.info("Usuario cerró sesión desde el aviso de inactividad");
            auditRepo.log("sesion",
                SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getId() : null,
                SessionManager.getCurrentUser() != null ? SessionManager.getCurrentUser().getNombre() : null,
                "logout", "Cierre de sesión desde aviso de inactividad");
            // Stop timers + clear instance before showLogin() so the old sessionGuard
            // timeline doesn't keep firing after the user is on the login screen.
            stopTimers();
            instance = null;
            SessionManager.logout();
            try { MainApp.showLogin(); } catch (Exception ex) { log.error("Error al cerrar sesión", ex); }
        }
    }

    private void setupKeyboardShortcuts(javafx.scene.Scene scene) {
        var a = scene.getAccelerators();
        navRegistry.installAccelerators(scene, SessionManager::isAdmin);
        a.put(new KeyCodeCombination(KeyCode.F5),                                   () -> refreshCurrentView());
        a.put(new KeyCodeCombination(KeyCode.R, KeyCombination.CONTROL_DOWN),      () -> refreshCurrentView());
        a.put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),      () -> focusCurrentSearch(scene));
        a.put(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),      this::onCommandPalette);
        a.put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), () -> {
            NavigationContext.setPendingNuevoBien();
            navigateToView("productos");
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

    @FXML
    private void onGlobalSearch() { onCommandPalette(); }

    private void onCommandPalette() {
        javafx.stage.Stage stage = (javafx.stage.Stage) contentArea.getScene().getWindow();
        List<SearchPaletteDialog.NavEntry> navEntries = navRegistry.paletteEntries(SessionManager.isAdmin());

        // Mutable lists: start empty so the dialog opens instantly showing nav entries,
        // then data is appended via Platform.runLater while the dialog is open.
        // rebuildList in SearchPaletteDialog reads these lazily on each keystroke.
        List<Producto>  mProductos  = new ArrayList<>();
        List<Resguardo> mResguardos = new ArrayList<>();
        List<Prestamo>  mPrestamos  = new ArrayList<>();

        AppExecutor.submit(() -> {
            try { var d = alertProductoService.getAll();
                  javafx.application.Platform.runLater(() -> mProductos.addAll(d)); }
            catch (Exception e) { log.warn("Palette: no se pudieron cargar bienes", e); }
            try { var d = new ResguardoService().getAll();
                  javafx.application.Platform.runLater(() -> mResguardos.addAll(d)); }
            catch (Exception e) { log.warn("Palette: no se pudieron cargar resguardos", e); }
            try { var d = prestamoService.getAll();
                  javafx.application.Platform.runLater(() -> mPrestamos.addAll(d)); }
            catch (Exception e) { log.warn("Palette: no se pudieron cargar préstamos", e); }
        });

        SearchPaletteDialog.show(stage, mProductos,
            producto -> {
                NavigationContext.setPendingProductId(producto.getId());
                navigateTo("productos", btnProductos);
            },
            mResguardos,
            rsg -> navigateTo("resguardos", btnResguardos),
            mPrestamos,
            prs -> navigateTo("prestamos", btnPrestamos),
            navEntries);
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

    private void refreshCurrentView() {
        // Re-navigate to the current active view to trigger a refresh
        if (sidebarManager.getActive() != null) sidebarManager.getActive().fire();
    }

    @FXML
    private void onShowShortcuts() { MainShortcutHelpDialog.show(); }

    private void refreshNotifBadge() {
        NotificationCenter.refreshBadge(notifBadge, alertProductoService, prestamoService);
    }

    /** Called from child controllers (e.g. Alertas → Movimientos). */
    public void navigateTo(String view) { navigateToView(view); }

    /**
     * Called by the {@link com.sibim.util.BarcodeScanner} when a USB HID scanner
     * fires a barcode or QR code. Navigates to the Productos screen and triggers
     * a search for the scanned code. Shows a brief toast so the user has visual
     * confirmation the scan was registered.
     */
    private void handleBarcodeScan(String codigo) {
        javafx.scene.Scene scene = contentArea.getScene();
        NotificacionUtil.info(scene, "Escaneado: " + codigo);
        navigateToView("productos");
        javafx.application.Platform.runLater(() -> {
            if (currentController instanceof ProductosController productosCtrl) {
                productosCtrl.buscarPorCodigo(codigo);
            }
        });
    }
}
