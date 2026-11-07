package com.sibim.controller.dialogs;

import com.sibim.model.Resguardo;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Resguardos › Pasar a otra persona, after choosing who receives: why, and the cargo
 *  of whoever receives, with what is going to happen said before it happens. */
public final class PasarResguardoDialog {

    public record Datos(String motivo, String cargo) {}

    static final List<String> MOTIVOS = List.of("Se dio de baja", "Renuncia", "Cambio de adscripción",
        "Jubilación", "Licencia");

    private PasarResguardoDialog() {}

    /**
     * @param suyos         the resguardos in force of whoever hands over the bienes
     * @param aQuien        who receives them
     * @param delQueRecibe  the resguardos in force that person already has in the área
     */
    public static Optional<Datos> pedir(List<Resguardo> suyos, String aQuien, List<Resguardo> delQueRecibe) {
        Resguardo uno = suyos.get(0);
        int bienes = suyos.stream().mapToInt(r -> r.getItems() == null ? 0 : r.getItems().size()).sum();
        String area = Objects.requireNonNullElse(uno.getResguardanteArea(), "su área");
        String folios = suyos.stream().map(Resguardo::getNumero).collect(Collectors.joining(", "));

        Dialog<Datos> dlg = new Dialog<>();
        dlg.setTitle("Pasar a otra persona");
        ButtonType pasar = new ButtonType("Pasar los bienes", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(pasar, ButtonType.CANCEL);
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2a-account-switch-outline", "Pasar a otra persona",
            uno.getResguardanteNombre() + "  →  " + aQuien + "\n"
                + FormatUtils.plural(bienes, "bien", "bienes") + " que se quedan en " + area,
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        Label queSucede = new Label("Se cancela" + (suyos.size() == 1 ? " el resguardo " : "n los resguardos ") + folios
            + " (quedan como se firmaron, con una nota de a quién pasaron) y se hace uno nuevo a nombre de "
            + aQuien + " para que lo firme."
            + (delQueRecibe.isEmpty() ? "" : "\n\n" + aQuien + " ya tiene "
                + delQueRecibe.stream().map(Resguardo::getNumero).collect(Collectors.joining(", "))
                + " en esta área: sus bienes también van en el nuevo, para que tenga uno solo."));
        queSucede.setWrapText(true);
        queSucede.getStyleClass().add("muted-sm");

        ComboBox<String> motivo = new ComboBox<>();
        motivo.getItems().setAll(MOTIVOS);
        motivo.setEditable(true);
        motivo.setPromptText("Por qué cambia quién los tiene");
        motivo.setMaxWidth(Double.MAX_VALUE);
        motivo.setAccessibleText("Motivo");

        TextField cargo = new TextField(delQueRecibe.stream().map(Resguardo::getResguardanteCargo)
            .filter(c -> c != null && !c.isBlank()).reduce((a, b) -> b).orElse(""));
        cargo.setPromptText("Cargo de " + aQuien + " (opcional)");
        cargo.setAccessibleText("Cargo de quien recibe");

        Label lMotivo = new Label("Motivo *");
        lMotivo.setLabelFor(motivo);
        Label lCargo = new Label("Cargo de quien recibe");
        lCargo.setLabelFor(cargo);

        Node ok = dlg.getDialogPane().lookupButton(pasar);
        ok.getStyleClass().add("dialog-ok-btn");
        Runnable validar = () -> ok.setDisable(texto(motivo).isBlank());
        motivo.getEditor().textProperty().addListener((o, a, b) -> validar.run());
        motivo.valueProperty().addListener((o, a, b) -> validar.run());
        validar.run();

        VBox form = new VBox(8, queSucede, lMotivo, motivo, lCargo, cargo);
        form.setPadding(new Insets(16, 24, 8, 24));
        dlg.getDialogPane().setContent(new VBox(header, form));
        dlg.getDialogPane().setPrefWidth(460);
        javafx.application.Platform.runLater(motivo::requestFocus);
        dlg.setResultConverter(bt -> bt != pasar ? null : new Datos(texto(motivo).trim(), cargo.getText().trim()));
        return dlg.showAndWait();
    }

    /** What is written in the motivo, chosen from the list or typed. */
    private static String texto(ComboBox<String> c) {
        String t = c.getEditor().getText();
        if (t == null || t.isBlank()) t = c.getValue();
        return t == null ? "" : t;
    }
}
