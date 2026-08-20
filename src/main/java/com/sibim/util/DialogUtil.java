package com.sibim.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/**
 * Shared utilities for styled dialogs across the application.
 * Eliminates repeated gradient-header, grid-setup, and async-save patterns.
 */
public final class DialogUtil {

    private DialogUtil() {}

    // ── Dialog creation ──────────────────────────────────────────────────

    /** Create a dialog with CSS applied and OK+Cancel buttons. */
    public static <T> Dialog<T> create(double prefWidth) {
        Dialog<T> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(prefWidth);
        applyStylesheet(dialog.getDialogPane());
        return dialog;
    }

    /** Create a button-type dialog (used when result is just OK/Cancel, not a typed value). */
    public static Dialog<ButtonType> createButtonDialog(double prefWidth) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(prefWidth);
        applyStylesheet(dialog.getDialogPane());
        return dialog;
    }

    /** Apply the app stylesheet to any dialog pane. */
    public static void applyStylesheet(DialogPane pane) {
        var css = DialogUtil.class.getResource("/css/styles.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
    }

    // ── OK button ────────────────────────────────────────────────────────

    /** Color the default OK button with the given hex color. */
    public static void styleOkButton(DialogPane pane, String hexColor) {
        Node btn = pane.lookupButton(ButtonType.OK);
        if (btn == null) return;
        btn.getStyleClass().add("dialog-ok-btn");
        btn.setStyle("-fx-background-color: " + hexColor + ";");
    }

    /** Return the OK button node, or null if not found. */
    public static Node getOkButton(DialogPane pane) {
        return pane.lookupButton(ButtonType.OK);
    }

    // ── Gradient header ──────────────────────────────────────────────────

    /** Build the standard gradient header used in all app dialogs. */
    public static HBox gradientHeader(String icon, String title, String subtitle,
                                      String color1, String color2) {
        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("dlg-header-icon-lbl");

        StackPane badge = new StackPane(iconLbl);
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
