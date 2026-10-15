package com.sibim.controller;

import java.util.Optional;

/** "¿Quién usa la cuenta?" for a shared área account: at every sign-in, with
 *  the last name used on this PC already typed in, and from the user menu to
 *  change it. */
final class OperadorDialog {

    private OperadorDialog() {}

    /** @param actual the last name used on this PC, or null if none */
    static Optional<String> preguntar(String nombreCuenta, String actual) {
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(actual != null ? actual : "");
        dlg.setTitle("¿Quién usa la cuenta?");
        // Remembered for good, the name went on every change of whoever sat
        // down next at this PC: now it is confirmed each time, with one key.
        dlg.setHeaderText("Esta cuenta la comparte todo el personal de " + nombreCuenta + ".\n"
            + "Escribe tu nombre para que quede registrado quién hace cada cambio."
            + (actual != null ? "\nSi eres " + actual + ", solo presiona Aceptar." : ""));
        dlg.setContentText("Tu nombre:");
        com.sibim.util.DialogUtil.applyOwner(dlg);
        com.sibim.util.DialogUtil.conEncabezado(dlg, "mdi2a-account-edit-outline");
        javafx.scene.Node ok = dlg.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        ok.disableProperty().bind(dlg.getEditor().textProperty().map(t -> t == null || t.trim().length() < 3));
        // Selected: someone else just types over it.
        dlg.setOnShown(e -> javafx.application.Platform.runLater(() -> dlg.getEditor().selectAll()));
        return dlg.showAndWait().map(String::trim).filter(t -> t.length() >= 3);
    }

    /** The bitácora says who took the shared account from now on: the sign-in
     *  itself is recorded before anyone has said who they are. */
    static void registrar(String cuentaId, String persona) {
        var u = com.sibim.session.SessionManager.getCurrentUser();
        new com.sibim.repository.AuditLogRepository().logEnFondo("sesion", cuentaId,
            u != null ? u.getNombre() : persona, "operador", "Usa la cuenta compartida: " + persona);
    }
}
