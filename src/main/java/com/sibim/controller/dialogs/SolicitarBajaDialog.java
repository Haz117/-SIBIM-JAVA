package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.Solicitud;
import com.sibim.service.ReporteService;
import com.sibim.service.SolicitudService;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Bienes › "Solicitar baja", for an área (5-nov-2026): why the bien should be
 * written off and how it is. The request goes to Patrimonio's inbox — the
 * administrator gets the notice, looks at the bien and decides — and the
 * formato to sign always comes out with the same data: the área hands it in,
 * signed, at Recursos Materiales y Patrimonio; without it no bien is given de
 * baja. Without a connection (or in the demo) only the formato is printed.
 */
public final class SolicitarBajaDialog {

    /** What the área chose. */
    public record Datos(String motivo, String detalle) {}

    /** "… en Recursos Materiales y Patrimonio": where the signed formato goes. */
    static final String ENTREGALO = "Fírmalo y entrégalo en " + Solicitud.ENTREGAR_FORMATO_EN;

    private static final SolicitudService SERVICE = new SolicitudService();
    private static final ReporteService REPORTES = ReporteService.getInstance();

    private SolicitarBajaDialog() {}

    /** Asks, sends and prints. {@code alEnviar} runs once the requests went out. */
    public static void mostrar(Scene scene, List<Producto> bienes, Runnable alEnviar) {
        if (bienes == null || bienes.isEmpty()) {
            NotificacionUtil.advertencia(scene, "Selecciona el bien que quieres dar de baja");
            return;
        }
        if (!SolicitudService.disponible()) {
            NotificacionUtil.info(scene, "Sin conexión con el servidor: se generó el formato de solicitud de baja. "
                + ENTREGALO + ".");
            imprimir(scene, bienes, null, null);
            return;
        }
        pedir(bienes).ifPresent(d -> DialogUtil.runAsync(
            () -> SERVICE.solicitarBaja(bienes, d.motivo(), d.detalle()),
            envio -> {
                if (!envio.enviadas().isEmpty() && alEnviar != null) alEnviar.run();
                avisar(scene, envio);
                imprimir(scene, bienes, d.motivo(), d.detalle());
            },
            e -> NotificacionUtil.error(scene, e instanceof IllegalArgumentException || e instanceof IllegalStateException
                || e instanceof SecurityException ? e.getMessage()
                : "No se pudo enviar la solicitud de baja: revisa la conexión e inténtalo de nuevo")));
    }

    static Optional<Datos> pedir(List<Producto> bienes) {
        Dialog<Datos> dlg = new Dialog<>();
        dlg.setTitle("Solicitar baja");
        ButtonType enviar = new ButtonType("Enviar e imprimir el formato", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(enviar, ButtonType.CANCEL);
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2f-file-remove-outline", "Solicitar baja",
            cuales(bienes) + "\nPatrimonio recibe el aviso, revisa el bien y decide si procede.",
            AppColors.WARNING_D, AppColors.WARNING_DD);

        ToggleGroup grupo = new ToggleGroup();
        GridPane motivos = new GridPane();
        motivos.setHgap(18);
        motivos.setVgap(6);
        int i = 0;
        for (String m : SolicitudService.MOTIVOS_BAJA) {
            RadioButton r = new RadioButton(m);
            r.setUserData(m);
            r.setToggleGroup(grupo);
            motivos.add(r, i % 2, i / 2);
            i++;
        }

        TextArea detalle = new TextArea();
        detalle.setPromptText("Ej. la pantalla ya no enciende; el respaldo está roto y no tiene arreglo");
        detalle.setPrefRowCount(3);
        detalle.setWrapText(true);
        detalle.setPrefWidth(400);
        Label cuenta = new Label();
        cuenta.getStyleClass().add("muted-sm");
        detalle.textProperty().addListener((o, a, b) -> cuenta.setText(b == null || b.isEmpty() ? "" : b.length() + " / 500"));

        // The paper is still required: said where it can't be missed.
        Label papel = new Label("Al enviarla sale el formato con lo que escribas aquí. Fírmalo (titular del área y "
            + "resguardante) y entrégalo en " + Solicitud.ENTREGAR_FORMATO_EN + ": sin el formato firmado el bien "
            + "no se da de baja.");
        papel.setWrapText(true);
        FontIcon iconoPapel = new FontIcon("mdi2f-file-document-edit-outline");
        iconoPapel.setIconSize(18);
        HBox entrega = new HBox(10, iconoPapel, papel);
        entrega.getStyleClass().add("cnt-aviso");
        entrega.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Node ok = dlg.getDialogPane().lookupButton(enviar);
        ok.getStyleClass().add("dialog-ok-btn-danger");
        Runnable validar = () -> {
            Toggle t = grupo.getSelectedToggle();
            String texto = detalle.getText() == null ? "" : detalle.getText().trim();
            boolean falta = t == null || ("Otro".equals(t.getUserData()) && texto.isEmpty()) || texto.length() > 500;
            ok.setDisable(falta);
        };
        grupo.selectedToggleProperty().addListener((o, a, b) -> {
            boolean otro = b != null && "Otro".equals(b.getUserData());
            detalle.setPromptText(otro ? "¿Cuál es el motivo? (obligatorio)"
                : "Ej. la pantalla ya no enciende; el respaldo está roto y no tiene arreglo");
            validar.run();
        });
        detalle.textProperty().addListener((o, a, b) -> validar.run());
        validar.run();

        VBox form = new VBox(8,
            new Label("¿Por qué se debe dar de baja? *"), motivos,
            new Label("¿Cómo está el bien?"), detalle, cuenta,
            entrega);
        form.setPadding(new Insets(16, 24, 8, 24));
        dlg.getDialogPane().setContent(new VBox(header, form));
        dlg.getDialogPane().setPrefWidth(480);
        dlg.setResultConverter(bt -> {
            if (bt != enviar || grupo.getSelectedToggle() == null) return null;
            String texto = detalle.getText() == null ? "" : detalle.getText().trim();
            return new Datos((String) grupo.getSelectedToggle().getUserData(), texto.isEmpty() ? null : texto);
        });
        return dlg.showAndWait();
    }

    /** "«Silla» (DGOB/07)" or "3 bienes: Silla, Mesa, Archivero". */
    static String cuales(List<Producto> bienes) {
        if (bienes.size() == 1) {
            Producto p = bienes.get(0);
            return "«" + p.getNombre() + "»" + (p.getCodigo() != null ? " (" + p.getCodigo() + ")" : "");
        }
        String nombres = bienes.stream().limit(3).map(Producto::getNombre).collect(Collectors.joining(", "));
        return FormatUtils.plural(bienes.size(), "bien", "bienes") + ": " + nombres + (bienes.size() > 3 ? "…" : "");
    }

    /** What happened, said once. */
    static String mensaje(SolicitudService.EnvioBaja envio) {
        int enviadas = envio.enviadas().size(), yaEstaban = envio.yaEnEspera().size();
        if (enviadas == 0)
            return (yaEstaban == 1 ? "Ese bien ya tiene su solicitud de baja en espera"
                : "Esos bienes ya tienen su solicitud de baja en espera")
                + ": se volvió a generar el formato. " + ENTREGALO + ".";
        String s = (enviadas == 1 ? "Se envió a Patrimonio la solicitud de baja"
            : "Se enviaron a Patrimonio " + enviadas + " solicitudes de baja")
            + ". Ahora " + Character.toLowerCase(ENTREGALO.charAt(0)) + ENTREGALO.substring(1)
            + ": sin él no se da de baja. La respuesta la ves en Inicio.";
        if (yaEstaban > 0)
            s += " " + FormatUtils.plural(yaEstaban, "bien ya tenía", "bienes ya tenían") + " su solicitud en espera.";
        return s;
    }

    private static void avisar(Scene scene, SolicitudService.EnvioBaja envio) {
        if (envio.enviadas().isEmpty()) NotificacionUtil.info(scene, mensaje(envio));
        else NotificacionUtil.exito(scene, mensaje(envio));
    }

    private static void imprimir(Scene scene, List<Producto> bienes, String motivo, String detalle) {
        DialogUtil.runAsyncWithProgress(scene,
            bienes.size() == 1 ? "Generando solicitud de baja…" : "Generando " + bienes.size() + " solicitudes de baja…",
            () -> REPORTES.exportSolicitudBaja(bienes, motivo, detalle),
            file -> DialogUtil.showExportResultDialog(scene, file),
            ex -> NotificacionUtil.error(scene, "No se pudo generar la solicitud de baja"));
    }
}
