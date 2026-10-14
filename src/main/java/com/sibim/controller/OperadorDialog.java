package com.sibim.controller;

import java.util.Optional;

/** "¿Quién usa la cuenta?" for a shared área account: at its first sign-in on
 *  a PC, and from the user menu to change it. */
final class OperadorDialog {

    private OperadorDialog() {}

    /** @param actual the name in use now, or null the first time on this PC */
    static Optional<String> preguntar(String nombreCuenta, String actual) {
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(actual != null ? actual : "");
        dlg.setTitle("¿Quién usa la cuenta?");
        dlg.setHeaderText("Esta cuenta la comparte todo el personal de " + nombreCuenta + ".\n"
            + "Escribe tu nombre para que quede registrado quién hace cada cambio.\n"
            + "En esta computadora ya no se volverá a preguntar; se cambia desde el menú de tu cuenta.");
        dlg.setContentText("Tu nombre:");
        com.sibim.util.DialogUtil.applyOwner(dlg);
        com.sibim.util.DialogUtil.conEncabezado(dlg, "mdi2a-account-edit-outline");
        javafx.scene.Node ok = dlg.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
        ok.disableProperty().bind(dlg.getEditor().textProperty().map(t -> t == null || t.trim().length() < 3));
        return dlg.showAndWait().map(String::trim).filter(t -> t.length() >= 3);
    }
}
