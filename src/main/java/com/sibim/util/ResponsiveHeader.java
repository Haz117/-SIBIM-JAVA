package com.sibim.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Keeps every screen's page header (and bottom action bar) readable at narrow widths.
 *
 *  Each header is an HBox of title + 4–7 labelled buttons. At the default
 *  1280 px window JavaFX shrinks every button to its ellipsis ("Da…", "Exp…")
 *  — all of them unreadable at once. Instead, as soon as the row no longer
 *  fits at full width, this drops the secondary buttons to icon-only (text stays set, so tooltips and screen readers still
 *  name them). The primary action keeps its label until even that won't fit.
 *  Installed once per screen from the navigation point, like
 *  {@link AccessibilityUtils}. */
public final class ResponsiveHeader {

    private ResponsiveHeader() {}

    private static final String MARK = "sibim.responsiveHeader";
    private static final String ORIGINAL_DISPLAY = "sibim.responsiveHeader.display";
    private static final String FLEX_MIN = "sibim.responsiveHeader.flexMin";
    /** Room the header title/subtitle keeps before buttons start collapsing. */
    private static final double TITLE_MIN = 240;
    /** Same for a filter bar's growing search field. */
    private static final double SEARCH_MIN = 200;
    /** Extra width needed to leave compact mode — avoids flicker at the edge. */
    private static final double HYSTERESIS = 24;

    public static void installAll(Node root) {
        // Bottom bars have a plain spacer where headers have a title — it may
        // shrink to nothing before any button gives up its label.
        AccessibilityUtils.forEachNode(root, n -> {
            if (!(n instanceof HBox bar)) return;
            if (bar.getStyleClass().contains("page-header")) install(bar, TITLE_MIN);
            else if (bar.getStyleClass().contains("bottom-action-bar")) install(bar, 0);
            else if (bar.getStyleClass().contains("filter-bar")) install(bar, SEARCH_MIN);
        });
    }

    public static void install(HBox header, double flexMin) {
        if (header.getProperties().putIfAbsent(MARK, Boolean.TRUE) != null) return;
        header.getProperties().put(FLEX_MIN, flexMin);

        List<ButtonBase> secondary = new ArrayList<>();
        List<ButtonBase> primary = new ArrayList<>();
        for (Node c : header.getChildren()) {
            if (c instanceof ButtonBase b && b.getGraphic() != null
                    && b.getContentDisplay() != ContentDisplay.GRAPHIC_ONLY) {
                // No min-width pin: the bar must be allowed to get narrower than
                // it needs, otherwise its width never reveals the shortfall and
                // the whole page grows past the window instead.
                b.getProperties().put(ORIGINAL_DISPLAY, b.getContentDisplay());
                (b.getStyleClass().contains("btn-primary") ? primary : secondary).add(b);
                b.textProperty().addListener(o -> Platform.runLater(() -> update(header, secondary, primary)));
            } else if (HBox.getHgrow(c) == Priority.ALWAYS && c instanceof Region r) {
                // The title block / spacer is what yields first. HBox shrinks every
                // child when short on room, so a title that asks for its full text
                // width would squeeze the buttons too — ask for flexMin and let
                // hgrow hand it whatever is left over instead.
                r.setMinWidth(0);
                r.setPrefWidth(isSpacer(r) ? 0 : flexMin);
            } else if (c instanceof javafx.scene.control.Label l) {
                l.setMinWidth(Region.USE_PREF_SIZE);   // counters like "36 resultados"
            }
        }
        header.widthProperty().addListener(o -> update(header, secondary, primary));
        Platform.runLater(() -> update(header, secondary, primary));
    }

    private static void update(HBox header, List<ButtonBase> secondary, List<ButtonBase> primary) {
        double available = header.getWidth();
        if (available <= 0) return;

        double full = requiredWidth(header, secondary, primary, false, false);
        double noSecondary = requiredWidth(header, secondary, primary, true, false);
        boolean wasCompact = secondary.stream().anyMatch(ResponsiveHeader::isCollapsed);
        boolean wasPrimaryCompact = primary.stream().anyMatch(ResponsiveHeader::isCollapsed);

        boolean compactSecondary = available < full + (wasCompact ? HYSTERESIS : 0);
        boolean compactPrimary = compactSecondary
            && available < noSecondary + (wasPrimaryCompact ? HYSTERESIS : 0);

        secondary.forEach(b -> setCollapsed(b, compactSecondary));
        primary.forEach(b -> setCollapsed(b, compactPrimary));
    }

    private static double requiredWidth(HBox header, List<ButtonBase> secondary, List<ButtonBase> primary,
                                        boolean secondaryCompact, boolean primaryCompact) {
        Insets in = header.getInsets();
        double total = in.getLeft() + in.getRight();
        int managed = 0;
        for (Node c : header.getChildren()) {
            if (!c.isManaged()) continue;
            managed++;
            if (c instanceof ButtonBase b && secondary.contains(b)) total += buttonWidth(b, secondaryCompact);
            else if (c instanceof ButtonBase b && primary.contains(b)) total += buttonWidth(b, primaryCompact);
            else if (HBox.getHgrow(c) == Priority.ALWAYS)
                total += isSpacer(c) ? 0 : (double) header.getProperties().getOrDefault(FLEX_MIN, TITLE_MIN);
            else total += c.prefWidth(-1);
        }
        return total + header.getSpacing() * Math.max(0, managed - 1);
    }

    /** Width of the button in either mode, computed from its parts so it is
     *  right regardless of which mode it is currently showing. */
    private static double buttonWidth(ButtonBase b, boolean compact) {
        if (isCollapsed(b) == compact) return b.prefWidth(-1);   // showing that mode now: exact
        Insets in = b.getInsets();
        double graphic = b.getGraphic() != null ? b.getGraphic().prefWidth(-1) : 0;
        double w = in.getLeft() + in.getRight() + graphic;
        String text = b.getText();
        if (!compact && text != null && !text.isBlank()) {
            Text probe = new Text(text);
            probe.setFont(b.getFont());
            w += b.getGraphicTextGap() + Math.ceil(probe.getLayoutBounds().getWidth()) + 2;
        }
        return w;
    }

    /** A bare Region used only to push the rest of the row to the right. */
    private static boolean isSpacer(Node n) {
        return n.getClass() == Region.class;
    }

    private static boolean isCollapsed(ButtonBase b) {
        return b.getContentDisplay() == ContentDisplay.GRAPHIC_ONLY;
    }

    private static void setCollapsed(ButtonBase b, boolean collapsed) {
        ContentDisplay target = collapsed ? ContentDisplay.GRAPHIC_ONLY
            : (ContentDisplay) b.getProperties().getOrDefault(ORIGINAL_DISPLAY, ContentDisplay.LEFT);
        if (b.getContentDisplay() != target) b.setContentDisplay(target);
    }
}
