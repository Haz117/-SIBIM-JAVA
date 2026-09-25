package com.sibim.controller;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

class SidebarManager {

    static final double SIDEBAR_WIDTH           = 220;
    static final double SIDEBAR_COLLAPSED_WIDTH = 76;
    private static final double TAB_OVERLAP     = 6;
    private static final double TAB_EXTENSION   = 40;

    private final VBox      sidebar;
    private final Region    sidebarBackdrop;
    private final StackPane outerStack;
    private final VBox      logoTextBox;
    private final VBox      userInfoVBox;
    private final Button    btnToggleSidebar;
    private final List<Button> navButtons;

    private boolean sidebarCollapsed = false;
    private Button  activeButton;
    private Pane    tabOverlay;
    private Region  tabProtrusion;
    private boolean tabPositioned = false;
    private SidebarSections sections;
    // Padding/spacing as they really are while expanded (the CSS overrides what the FXML declares),
    // remembered when collapsing so expanding again puts back exactly the same layout.
    private Insets expandedLogoPadding;
    private double expandedLogoSpacing;
    private Insets expandedNavPadding;

    SidebarManager(VBox sidebar, Region sidebarBackdrop, StackPane outerStack,
                   VBox logoTextBox, VBox userInfoVBox, Button btnToggleSidebar,
                   List<Button> navButtons) {
        this.sidebar          = sidebar;
        this.sidebarBackdrop  = sidebarBackdrop;
        this.outerStack       = outerStack;
        this.logoTextBox      = logoTextBox;
        this.userInfoVBox     = userInfoVBox;
        this.btnToggleSidebar = btnToggleSidebar;
        this.navButtons       = navButtons;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    void setup() {
        if (outerStack == null || tabOverlay != null) return;

        tabProtrusion = new Region();
        tabProtrusion.setPrefWidth(TAB_OVERLAP + TAB_EXTENSION);
        tabProtrusion.getStyleClass().add("nav-tab-extension");

        tabOverlay = new Pane(tabProtrusion);
        tabOverlay.setMouseTransparent(true);
        tabOverlay.setPickOnBounds(false);
        outerStack.getChildren().add(tabOverlay);

        javafx.application.Platform.runLater(() -> {
            if (activeButton != null) updateTabProtrusion(activeButton);
        });
    }

    void setActive(Button btn) {
        if (activeButton != null) activeButton.getStyleClass().remove("nav-active");
        btn.getStyleClass().add("nav-active");
        activeButton = btn;
        updateTabProtrusion(btn);
    }

    /** Accordion sections (null until wired). Their folding changes where the active button sits. */
    void setSections(SidebarSections sections) {
        this.sections = sections;
        if (sections != null) sections.setOnLayoutChanged(this::refreshTab);
    }

    /** Re-positions the active-tab marker after the nav layout changed (section folded/unfolded…). */
    void refreshTab() {
        if (activeButton != null) updateTabProtrusion(activeButton);
    }

    Button getActive() { return activeButton; }
    boolean isCollapsed() { return sidebarCollapsed; }

    void toggle() {
        sidebarCollapsed = !sidebarCollapsed;
        double targetW = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_WIDTH;

        sidebar.setMinWidth(Region.USE_PREF_SIZE);
        sidebar.setMaxWidth(Region.USE_PREF_SIZE);
        sidebarBackdrop.setMinWidth(Region.USE_PREF_SIZE);
        sidebarBackdrop.setMaxWidth(Region.USE_PREF_SIZE);


        javafx.scene.Node logoBadge = sidebar.lookup(".sidebar-logo-badge");
        HBox logoHBox = (logoTextBox != null && logoTextBox.getParent() instanceof HBox h) ? h : null;
        // Only the account TEXT is hidden in the rail: the avatar stays (and still opens the account menu).
        javafx.scene.Node userText = userInfoVBox;

        ScrollPane navScroll = sidebar.lookup(".sidebar-scroll") instanceof ScrollPane sp ? sp : null;
        VBox navVBox = (navScroll != null && navScroll.getContent() instanceof VBox v) ? v : null;

        if (sidebarCollapsed) {
            if (logoHBox != null) { expandedLogoPadding = logoHBox.getPadding(); expandedLogoSpacing = logoHBox.getSpacing(); }
            if (navVBox != null)  expandedNavPadding = navVBox.getPadding();
        }

        if (sidebarCollapsed) sidebar.getStyleClass().add("sidebar-collapsed");
        else                  sidebar.getStyleClass().remove("sidebar-collapsed");

        if (sidebarCollapsed) {
            if (logoBadge != null) { logoBadge.setVisible(false); logoBadge.setManaged(false); }
            logoTextBox.setVisible(false); logoTextBox.setManaged(false);
            if (logoHBox != null) { logoHBox.setPadding(new Insets(10, 4, 0, 4)); logoHBox.setSpacing(0); }
            if (userText != null) { userText.setVisible(false); userText.setManaged(false); }
            if (sections != null) sections.setRailMode(true);
            navButtons.stream().filter(b -> b != null).forEach(b -> b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY));
            if (navVBox != null) navVBox.setPadding(new Insets(6, 12, 6, 12));
            ((FontIcon) btnToggleSidebar.getGraphic()).setIconLiteral("mdi2c-chevron-right");
        } else {
            ((FontIcon) btnToggleSidebar.getGraphic()).setIconLiteral("mdi2c-chevron-left");
        }

        Timeline t = new Timeline(
            new KeyFrame(Duration.millis(220),
                new KeyValue(sidebar.prefWidthProperty(),         targetW, Interpolator.EASE_BOTH),
                new KeyValue(sidebarBackdrop.prefWidthProperty(), targetW, Interpolator.EASE_BOTH))
        );
        t.setOnFinished(ev -> {
            if (!sidebarCollapsed) {
                if (logoBadge != null) { logoBadge.setVisible(true); logoBadge.setManaged(true); }
                logoTextBox.setVisible(true); logoTextBox.setManaged(true);
                if (logoHBox != null && expandedLogoPadding != null) {
                    logoHBox.setPadding(expandedLogoPadding);
                    logoHBox.setSpacing(expandedLogoSpacing);
                }
                if (userText != null) { userText.setVisible(true); userText.setManaged(true); }
                if (sections != null) sections.setRailMode(false);
                navButtons.stream().filter(b -> b != null).forEach(b -> b.setContentDisplay(ContentDisplay.LEFT));
                if (navVBox != null && expandedNavPadding != null) navVBox.setPadding(expandedNavPadding);
            }
            if (activeButton != null) updateTabProtrusion(activeButton);
        });
        t.play();
    }

    // ── Tab protrusion ────────────────────────────────────────────────────────

    /** A node is on screen only if it and every ancestor are visible and managed. */
    private static boolean isShowing(javafx.scene.Node n) {
        for (javafx.scene.Node p = n; p != null; p = p.getParent()) {
            if (!p.isVisible() || !p.isManaged()) return false;
        }
        return true;
    }

    void updateTabProtrusion(Button btn) {
        if (tabProtrusion == null || outerStack == null || outerStack.getScene() == null) return;
        javafx.application.Platform.runLater(() -> {
            boolean showing = isShowing(btn);
            tabProtrusion.setVisible(showing);
            if (!showing) return;     // active page lives in a folded section: its header is highlighted instead
            javafx.geometry.Bounds b   = btn.localToScene(btn.getBoundsInLocal());
            javafx.geometry.Point2D org = outerStack.sceneToLocal(0, 0);
            double btnTop = b.getMinY() + org.getY();
            double btnBot = b.getMaxY() + org.getY();
            double btnH   = btnBot - btnTop;
            double extX   = currentSidebarWidth() - TAB_OVERLAP;

            if (!tabPositioned) {
                tabProtrusion.setLayoutX(extX);
                tabProtrusion.setLayoutY(btnTop);
                tabPositioned = true;
            } else {
                glideTab(tabProtrusion, extX, btnTop);
            }
            tabProtrusion.setPrefHeight(btnH);
        });
    }

    private double currentSidebarWidth() {
        return (sidebar != null && sidebar.getWidth() > 0) ? sidebar.getWidth() : SIDEBAR_WIDTH;
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
}
