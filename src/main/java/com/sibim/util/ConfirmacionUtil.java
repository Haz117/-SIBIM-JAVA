package com.sibim.util;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Optional;

public class ConfirmacionUtil {

    public static boolean confirmar(String titulo, String mensaje) {
        return showDialog("❓", "#6366F1", "#EEF2FF", titulo, mensaje, "Confirmar", "#6366F1");
    }

    public static boolean confirmarEliminar(String nombreElemento) {
        return showDialog(
            "🗑", "#EF4444", "#FEF2F2",
            "Confirmar eliminación",
            "¿Eliminar \"" + nombreElemento + "\"?\nEsta acción no se puede deshacer.",
            "Eliminar", "#EF4444"
        );
    }

    private static boolean showDialog(String icon, String accent, String accentLight,
            String title, String message, String confirmLabel, String confirmColor) {

        ButtonType btnConfirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel  = new ButtonType("Cancelar",   ButtonBar.ButtonData.CANCEL_CLOSE);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(btnConfirm, btnCancel);
        dialog.getDialogPane().setPrefWidth(400);
        dialog.getDialogPane().getStyleClass().add("confirm-pane");

        var css = ConfirmacionUtil.class.getResource("/css/styles.css");
        if (css != null) dialog.getDialogPane().getStylesheets().add(css.toExternalForm());

        Label iconLbl = new Label(icon);
        iconLbl.getStyleClass().add("confirm-icon-text");

        StackPane iconBadge = new StackPane(iconLbl);
        iconBadge.getStyleClass().add("confirm-icon-badge");
        // Dynamic colors only — static properties are in CSS
        iconBadge.setStyle("-fx-background-color: " + accentLight + "; -fx-border-color: " + accent + "33;");

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
            confirmBtn.setStyle("-fx-background-color: " + confirmColor + ";");
        }
        Node cancelBtn = dialog.getDialogPane().lookupButton(btnCancel);
        if (cancelBtn != null) cancelBtn.getStyleClass().add("confirm-cancel-btn");

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == btnConfirm;
    }
}
