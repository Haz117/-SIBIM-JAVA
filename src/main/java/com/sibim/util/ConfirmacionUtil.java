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

        var css = ConfirmacionUtil.class.getResource("/css/styles.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());

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

    private static boolean showDialog(String icon, String title, String message,
            String confirmLabel, boolean danger) {

        ButtonType btnConfirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirm, btnCancel);
        dialog.getDialogPane().setPrefWidth(400);
        dialog.getDialogPane().getStyleClass().add("confirm-pane");

        var css = ConfirmacionUtil.class.getResource("/css/styles.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());

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

        dialog.getDialogPane().setContent(content);

        Node confirmBtn = dialog.getDialogPane().lookupButton(btnConfirm);
        if (confirmBtn != null) {
            confirmBtn.getStyleClass().add("confirm-ok-btn");
            if (danger) confirmBtn.getStyleClass().add("confirm-ok-btn-danger");
        }
        Node cancelBtn = dialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) cancelBtn.getStyleClass().add("confirm-cancel-btn");

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == btnConfirm;
    }
}
