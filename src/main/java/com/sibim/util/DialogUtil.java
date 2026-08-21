package com.sibim.util;

import com.sibim.MainApp;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.util.Callback;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared utilities for styled dialogs across the application.
 * Eliminates repeated gradient-header, grid-setup, and async-save patterns.
 */
public final class DialogUtil {

    private DialogUtil() {}

    // ── Table cell factories ─────────────────────────────────────────────

    /** Builds a TableCell factory that renders a String column value as a
     *  "cell-badge" pill, colored by {@code cssClassOf(value)} (e.g.
     *  "cell-badge-success"). Shared by every status/type/role column across
     *  the app so the badge cell markup and CSS-class swap logic live in one
     *  place instead of being copy-pasted per controller. */
    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> badgeCellFactory(
            Function<String, String> cssClassOf) {
        return col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.getStyleClass().add("cell-badge"); }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null) return;
                badge.setText(item);
                badge.getStyleClass().removeIf(c -> c.startsWith("cell-badge-"));
                badge.getStyleClass().add(cssClassOf.apply(item));
                setGraphic(badge);
            }
        };
    }

    // ── Dialog creation ──────────────────────────────────────────────────

    /** Create a dialog with CSS applied and OK+Cancel buttons. */
    public static <T> Dialog<T> create(double prefWidth) {
        Dialog<T> dialog = new Dialog<>();
        if (MainApp.getPrimaryStage() != null) dialog.initOwner(MainApp.getPrimaryStage());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(prefWidth);
        applyStylesheet(dialog.getDialogPane());
        return dialog;
    }

    /** Create a button-type dialog (used when result is just OK/Cancel, not a typed value). */
    public static Dialog<ButtonType> createButtonDialog(double prefWidth) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (MainApp.getPrimaryStage() != null) dialog.initOwner(MainApp.getPrimaryStage());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(prefWidth);
        applyStylesheet(dialog.getDialogPane());
        return dialog;
    }

    /** Set owner + APPLICATION_MODAL on any dialog not created via create()/createButtonDialog(). */
    public static void applyOwner(Dialog<?> d) {
        if (MainApp.getPrimaryStage() != null) d.initOwner(MainApp.getPrimaryStage());
        d.initModality(Modality.APPLICATION_MODAL);
    }

    /** Apply the app stylesheet to any dialog pane. */
    public static void applyStylesheet(DialogPane pane) {
        var css = DialogUtil.class.getResource("/css/styles.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }

    // ── OK button ────────────────────────────────────────────────────────

    /** Color the default OK button with the given hex color.
     *  Uses a disabledProperty listener + inline styles so the button stays
     *  visible regardless of Modena's opacity:0.4 rule for :disabled buttons. */
    public static void styleOkButton(DialogPane pane, String hexColor) {
        Node btn = pane.lookupButton(ButtonType.OK);
        if (btn == null) return;
        btn.getStyleClass().add("dialog-ok-btn");
        String on  = "-fx-background-color:" + hexColor + ";-fx-text-fill:white;"
                   + "-fx-font-weight:bold;-fx-padding:8 22;-fx-background-radius:8;"
                   + "-fx-opacity:1;-fx-cursor:hand;";
        String off = "-fx-background-color:#DDE3EF;-fx-text-fill:#8896AA;"
                   + "-fx-font-weight:bold;-fx-padding:8 22;-fx-background-radius:8;"
                   + "-fx-opacity:1;-fx-cursor:default;";
        Runnable sync = () -> btn.setStyle(btn.isDisabled() ? off : on);
        sync.run();
        btn.disabledProperty().addListener((obs, o, n) -> sync.run());
    }

    /** Return the OK button node, or null if not found. */
    public static Node getOkButton(DialogPane pane) {
        return pane.lookupButton(ButtonType.OK);
    }

    // ── Gradient header ──────────────────────────────────────────────────

    /** Build the standard gradient header used in all app dialogs. */
    public static HBox gradientHeader(String icon, String title, String subtitle,
                                      String color1, String color2) {
        FontIcon iconNode = new FontIcon(icon);
        iconNode.setIconSize(22);
        iconNode.getStyleClass().add("dlg-header-icon");

        StackPane badge = new StackPane(iconNode);
        badge.getStyleClass().add("dlg-header-icon-badge");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("dlg-header-title");

        Label subLbl = new Label(subtitle);
        subLbl.getStyleClass().add("dlg-header-subtitle");
        subLbl.setWrapText(true);

        VBox text = new VBox(3, titleLbl, subLbl);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox header = new HBox(16, badge, text);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(22, 24, 22, 24));
        // Gradient background is dynamic (per-dialog colors) — irreducible inline style
        header.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%,"
            + color1 + "," + color2 + "); -fx-background-radius: 8 8 0 0;");
        return header;
    }

    // ── Form grid ────────────────────────────────────────────────────────

    /** Standard two-column form grid with label column + expanding field column. */
    public static GridPane formGrid(double labelColWidth) {
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        g.setPadding(new Insets(18, 22, 20, 22));
        ColumnConstraints c0 = new ColumnConstraints(labelColWidth);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c0, c1);
        return g;
    }

    /** Bold form field label using the dialog-field-label CSS class. */
    public static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("dialog-field-label");
        return l;
    }

    /** Field label with a small `?` badge that shows helpText in a tooltip. */
    public static HBox fieldLabelWithHelp(String text, String helpText) {
        Label badge = new Label("?");
        badge.getStyleClass().add("help-badge");
        Tooltip tip = new Tooltip(helpText);
        tip.setWrapText(true);
        tip.setMaxWidth(270);
        Tooltip.install(badge, tip);
        HBox row = new HBox(5, fieldLabel(text), badge);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ── Spinner ──────────────────────────────────────────────────────────

    /**
     * Works around a known JavaFX bug (JDK-8150946): an editable Spinner does not
     * commit its editor text unless Enter or the arrow buttons are used. This commits
     * the typed value when the spinner loses focus (e.g. clicking a dialog's OK button).
     */
    public static void commitOnFocusLoss(Spinner<Integer> spinner) {
        spinner.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) commitSpinner(spinner);
        });
    }

    /** Force-commits the spinner's editor text into its value factory right now. */
    public static void commitSpinner(Spinner<Integer> spinner) {
        var vf = spinner.getValueFactory();
        if (vf == null) return;
        try {
            Integer value = vf.getConverter().fromString(spinner.getEditor().getText());
            if (value != null) vf.setValue(value);
        } catch (NumberFormatException ignored) {}
    }

    // ── Async pattern ────────────────────────────────────────────────────

    /** Runnable variant that allows checked exceptions — usable as a lambda. */
    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    /**
     * Run a background task and deliver success/failure back on the FX thread.
     * Accepts checked-exception-throwing lambdas (e.g. repository calls).
     */
    public static void runAsync(CheckedRunnable background, Runnable onSuccess, Consumer<Exception> onError) {
        AppExecutor.submit(() -> {
            try {
                background.run();
                if (onSuccess != null) Platform.runLater(onSuccess);
            } catch (Exception ex) {
                if (onError != null) Platform.runLater(() -> onError.accept(ex));
            }
        });
    }

    /** Async with a typed result returned from the background thread. */
    public static <R> void runAsync(java.util.concurrent.Callable<R> task,
                                    Consumer<R> onSuccess, Consumer<Exception> onError) {
        AppExecutor.submit(() -> {
            try {
                R result = task.call();
                if (onSuccess != null) Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception ex) {
                if (onError != null) Platform.runLater(() -> onError.accept(ex));
            }
        });
    }
}
