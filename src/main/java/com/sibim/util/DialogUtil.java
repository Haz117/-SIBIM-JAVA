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

import javafx.animation.PauseTransition;
import javafx.util.Callback;
import javafx.util.Duration;

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
        enableClickToShowTooltip(badge, tip);
        HBox row = new HBox(5, fieldLabel(text), badge);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Makes a "?" help icon respond to a click, not just JavaFX's default
     *  ~1s hover delay — users click these expecting instant feedback rather
     *  than hovering and waiting, and on a small icon a hover can easily miss
     *  anyway. Shortens the hover delay too, forces wrapping so long help
     *  text can't run past the window edge, and anchors the popup just below
     *  the icon (not at the raw click point, which can land the tooltip in
     *  an inconsistent spot depending on exactly where inside the icon was
     *  clicked) before auto-hiding it a few seconds later. */
    public static void enableClickToShowTooltip(Node node, Tooltip tip) {
        tip.setShowDelay(Duration.millis(150));
        tip.setWrapText(true);
        if (tip.getMaxWidth() <= 0 || tip.getMaxWidth() == Double.MAX_VALUE) tip.setMaxWidth(280);
        node.setOnMouseClicked(e -> {
            var b = node.localToScreen(node.getBoundsInLocal());
            tip.show(node, b.getMinX(), b.getMaxY() + 6);
            PauseTransition hide = new PauseTransition(Duration.seconds(5));
            hide.setOnFinished(ev -> tip.hide());
            hide.play();
            // Consumed so a badge sitting inside a clickable container (e.g.
            // a whole dashboard stat card wired to navigate on click) shows
            // the tooltip instead of also firing the container's own handler.
            e.consume();
        });
    }

    /** Same as {@link #enableClickToShowTooltip(Node, Tooltip)} but for a
     *  node whose Tooltip was already attached elsewhere (e.g. via FXML's
     *  &lt;tooltip&gt; element) — reads it back off the control instead of
     *  taking it as a parameter. */
    public static void enableClickToShowTooltip(Control node) {
        Tooltip tip = node.getTooltip();
        if (tip != null) enableClickToShowTooltip(node, tip);
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

    // ── Export result dialog ─────────────────────────────────────────────

    /** Shows a "file generated" dialog with Abrir/Carpeta/Copiar ruta/Imprimir
     *  actions for a just-exported report file. Shared by every screen that
     *  exports a PDF/Excel/CSV (Reportes, Depreciación) so this ~80-line
     *  dialog isn't duplicated per controller. */
    public static void showExportResultDialog(javafx.scene.Scene scene, java.io.File file) {
        Dialog<ButtonType> dialog = new Dialog<>();
        applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(460);
        applyStylesheet(dialog.getDialogPane());

        HBox header = gradientHeader("mdi2c-check-circle-outline", "Reporte generado",
            "El archivo fue exportado exitosamente.", "#047857", "#065F46");

        String name  = file.getName();
        String ext   = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toUpperCase() : "";
        long   bytes = file.length();
        String size  = bytes < 1024 ? bytes + " B"
            : bytes < 1024 * 1024 ? (bytes / 1024) + " KB"
            : String.format("%.1f MB", bytes / (1024.0 * 1024));

        FontIcon iconLbl = new FontIcon("PDF".equals(ext) ? "mdi2f-file-pdf-box"
            : "XLSX".equals(ext) ? "mdi2f-file-excel-outline" : "mdi2f-file-delimited-outline");
        iconLbl.setIconSize(22);
        Label nameLbl  = new Label(name);
        nameLbl.getStyleClass().add("dlg-detail-value");
        nameLbl.setWrapText(true);
        Label sizeLbl  = new Label(size + " · " + file.getParent());
        sizeLbl.getStyleClass().add("muted-sm");
        sizeLbl.setWrapText(true);

        VBox info = new VBox(3, nameLbl, sizeLbl);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox fileCard = new HBox(14, iconLbl, info);
        fileCard.setAlignment(Pos.CENTER_LEFT);
        fileCard.setPadding(new Insets(14, 18, 14, 18));
        fileCard.getStyleClass().add("dlg-detail-header");

        Button btnAbrir   = new Button("Abrir");
        Button btnCarpeta = new Button("Carpeta");
        Button btnCopiar  = new Button("Copiar ruta");
        Button btnImpr    = new Button("Imprimir");
        btnAbrir.setGraphic(new FontIcon("mdi2f-folder-open-outline"));
        btnCarpeta.setGraphic(new FontIcon("mdi2f-folder-outline"));
        btnCopiar.setGraphic(new FontIcon("mdi2c-content-copy"));
        btnImpr.setGraphic(new FontIcon("mdi2p-printer"));
        btnAbrir.setContentDisplay(ContentDisplay.LEFT);
        btnCarpeta.setContentDisplay(ContentDisplay.LEFT);
        btnCopiar.setContentDisplay(ContentDisplay.LEFT);
        btnImpr.setContentDisplay(ContentDisplay.LEFT);
        btnAbrir.getStyleClass().add("btn-primary");
        btnCarpeta.getStyleClass().add("btn-secondary");
        btnCopiar.getStyleClass().add("btn-secondary");
        btnImpr.getStyleClass().add("btn-secondary");
        btnImpr.setVisible("PDF".equals(ext));
        btnImpr.setManaged("PDF".equals(ext));

        btnAbrir.setOnAction(e -> {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN))
                    java.awt.Desktop.getDesktop().open(file);
                else
                    NotificacionUtil.advertencia(scene, "No se puede abrir el archivo en este entorno");
            } catch (Exception ex) { /* best-effort */ }
        });
        btnCarpeta.setOnAction(e -> {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN))
                    java.awt.Desktop.getDesktop().open(file.getParentFile());
                else
                    NotificacionUtil.advertencia(scene, "No se puede abrir la carpeta en este entorno");
            } catch (Exception ex) { /* best-effort */ }
        });
        btnCopiar.setOnAction(e -> {
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(file.getAbsolutePath());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            NotificacionUtil.info(scene, "Ruta copiada al portapapeles");
        });
        btnImpr.setOnAction(e -> {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.PRINT))
                    java.awt.Desktop.getDesktop().print(file);
                else
                    NotificacionUtil.advertencia(scene, "La impresión directa no está disponible en este entorno");
            } catch (Exception ex) { NotificacionUtil.error(scene, "No se pudo enviar a la impresora"); }
        });

        HBox actions = new HBox(8, btnAbrir, btnImpr, btnCarpeta, btnCopiar);
        actions.setPadding(new Insets(10, 18, 8, 18));
        actions.setAlignment(Pos.CENTER_LEFT);

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, fileCard, actions), 260, 60);
        dialog.getDialogPane().setContent(new VBox(0, header, fileCard, actions));
        dialog.showAndWait();
    }
}
