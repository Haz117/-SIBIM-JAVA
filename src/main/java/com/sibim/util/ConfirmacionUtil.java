package com.sibim.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

public class ConfirmacionUtil {

    public static boolean confirmar(String titulo, String mensaje) {
        return showDialog("mdi2h-help-circle-outline", titulo, mensaje, "Confirmar", false);
    }

    public static boolean confirmarEliminar(String nombreElemento) {
        return showDialog(
            "mdi2d-delete-outline",
            "Confirmar eliminación",
            "¿Eliminar \"" + nombreElemento + "\"?\nEsta acción no se puede deshacer.",
            "Eliminar", true
        );
    }

    /** Prompts for a required motivo before an irreversible-in-spirit action
     *  (e.g. baja patrimonial) — returns empty if the user cancels or leaves
     *  it blank. Kept as a separate method from {@link #confirmarEliminar}
     *  since a baja needs a recorded reason (it's a soft-delete, not a
     *  physical one, but the reason is what makes it auditable). */
    public static Optional<String> confirmarConMotivo(String icon, String title, String message,
            String confirmLabel, boolean danger, String promptText) {
        ButtonType btnConfirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirm, btnCancel);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.getDialogPane().getStyleClass().add("confirm-pane");

        dialog.getDialogPane().getStylesheets().addAll(EscalaTexto.hojas());
        AccessibilityUtils.applyCurrentTextScaleClass(dialog.getDialogPane());

        FontIcon iconLbl = new FontIcon(icon);
        iconLbl.setIconSize(20);
        iconLbl.getStyleClass().add("confirm-icon-text");
        StackPane iconBadge = new StackPane(iconLbl);
        iconBadge.getStyleClass().addAll("confirm-icon-badge",
            danger ? "confirm-icon-badge-danger" : "confirm-icon-badge-default");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("confirm-title");
        Label msgLbl = new Label(message);
        msgLbl.getStyleClass().add("confirm-msg");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(290);

        VBox textBox = new VBox(5, titleLbl, msgLbl);
        textBox.setAlignment(Pos.CENTER_LEFT);
        HBox content = new HBox(16, iconBadge, textBox);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(22, 22, 12, 22));

        TextField motivoField = new TextField();
        motivoField.setPromptText(promptText);
        motivoField.getStyleClass().add("form-input");
        VBox motivoBox = new VBox(4, motivoField);
        motivoBox.setPadding(new Insets(0, 22, 20, 22));

        VBox root = new VBox(content, motivoBox);
        dialog.getDialogPane().setContent(root);
        AnimationUtils.springIn(root);

        Node confirmBtn = dialog.getDialogPane().lookupButton(btnConfirm);
        if (confirmBtn != null) {
            confirmBtn.getStyleClass().add("confirm-ok-btn");
            if (danger) confirmBtn.getStyleClass().add("confirm-ok-btn-danger");
            confirmBtn.setDisable(true);
            motivoField.textProperty().addListener((o, a, b) -> confirmBtn.setDisable(b.isBlank()));
        }
        Node cancelBtn = dialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) cancelBtn.getStyleClass().add("confirm-cancel-btn");

        javafx.application.Platform.runLater(motivoField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnConfirm && !motivoField.getText().isBlank())
            return Optional.of(motivoField.getText().trim());
        return Optional.empty();
    }

    /** {@link #confirmar(String, String)} with the button saying what it does
     *  ("Reactivar", "Juntar en uno"): "Confirmar" makes the person reread
     *  the question to know what the button will do. */
    public static boolean confirmar(String titulo, String mensaje, String accion) {
        return showDialog("mdi2h-help-circle-outline", titulo, mensaje, accion, "Cancelar", false);
    }

    /** For what can't be undone or loses work (restore, delete, discard): the
     *  button says it, in red, and Enter doesn't press it. */
    public static boolean confirmarPeligro(String titulo, String mensaje, String accion) {
        return showDialog("mdi2a-alert-outline", titulo, mensaje, accion, "Cancelar", true);
    }

    /** Two named ways out, when "Cancelar" would be ambiguous — "¿Cancelar
     *  el resguardo?" with a "Cancelar" button, or a draft that "Cancelar"
     *  threw away. True for {@code accion}; closing the window counts as
     *  {@code alternativa}. */
    public static boolean elegir(String titulo, String mensaje, String accion, String alternativa) {
        return showDialog("mdi2h-help-circle-outline", titulo, mensaje, accion, alternativa, false);
    }

    /** {@link #elegir} for an action that can't be undone. */
    public static boolean elegirPeligro(String titulo, String mensaje, String accion, String alternativa) {
        return showDialog("mdi2a-alert-outline", titulo, mensaje, accion, alternativa, true);
    }

    private static boolean showDialog(String icon, String title, String message,
            String confirmLabel, boolean danger) {
        return showDialog(icon, title, message, confirmLabel, "Cancelar", danger);
    }

    private static boolean showDialog(String icon, String title, String message,
            String confirmLabel, String cancelLabel, boolean danger) {

        ButtonType btnConfirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType(cancelLabel,  ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirm, btnCancel);
        dialog.getDialogPane().setPrefWidth(400);
        dialog.getDialogPane().getStyleClass().add("confirm-pane");

        dialog.getDialogPane().getStylesheets().addAll(EscalaTexto.hojas());
        AccessibilityUtils.applyCurrentTextScaleClass(dialog.getDialogPane());

        FontIcon iconLbl = new FontIcon(icon);
        iconLbl.setIconSize(20);
        iconLbl.getStyleClass().add("confirm-icon-text");

        StackPane iconBadge = new StackPane(iconLbl);
        iconBadge.getStyleClass().addAll("confirm-icon-badge",
            danger ? "confirm-icon-badge-danger" : "confirm-icon-badge-default");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("confirm-title");

        Label msgLbl = new Label(message);
        msgLbl.getStyleClass().add("confirm-msg");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(290);

        VBox textBox = new VBox(5, titleLbl, msgLbl);
        textBox.setAlignment(Pos.CENTER_LEFT);

        HBox content = new HBox(16, iconBadge, textBox);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(22, 22, 16, 22));

        // Wrapped, like confirmarConMotivo: the dialog stylesheet sets the content's
        // own padding to 0, and the icon ended up against the left border.
        VBox root = new VBox(content);
        dialog.getDialogPane().setContent(root);
        AnimationUtils.springIn(root);

        Node confirmBtn = dialog.getDialogPane().lookupButton(btnConfirm);
        if (confirmBtn != null) {
            confirmBtn.getStyleClass().add("confirm-ok-btn");
            if (danger) confirmBtn.getStyleClass().add("confirm-ok-btn-danger");
        }
        Node cancelBtn = dialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) cancelBtn.getStyleClass().add("confirm-cancel-btn");
        // Buttons that say what they do ("Cancelar el resguardo") are longer than
        // "Confirmar": each one as wide as its words, never cut with "…".
        for (Node n : new Node[]{confirmBtn, cancelBtn})
            if (n instanceof javafx.scene.control.Button b) {
                ButtonBar.setButtonUniformSize(b, false);
                b.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            }
        // A red action is never the one an Enter pressed out of habit triggers.
        if (danger && confirmBtn instanceof javafx.scene.control.Button b && cancelBtn instanceof javafx.scene.control.Button c) {
            b.setDefaultButton(false);
            c.setDefaultButton(true);
            dialog.setOnShown(e -> c.requestFocus());
        }

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == btnConfirm;
    }
}
