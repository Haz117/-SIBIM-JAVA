package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.service.LecturaAuditoria;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Objects;

/** One bitácora entry, read: when, who, which bien and its área, and each change on its own
 *  row — before, after (19-nov-2026: it showed «producto» and «actualizar» as stored and the
 *  changes as one block of text). «Ver todo lo de SSPCR/19» filters the screen to that bien. */
final class AuditoriaDetailDialog {

    private AuditoriaDetailDialog() {}

    /** @param verTodo filters the screen to this entry's bien (or resguardo…); null hides the button */
    static void show(AuditLog entry, java.util.function.Consumer<AuditLog> verTodo) {
        LecturaAuditoria.Lectura l = AuditoriaColumnSetup.lectura(entry);
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Detalle del registro");
        ButtonType cerrar = new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().add(cerrar);
        dlg.getDialogPane().setPrefWidth(640);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        String accion = EtiquetasAuditoria.accion(entry.getAccion());
        HBox header = DialogUtil.gradientHeader("mdi2m-magnify-scan", accion.isEmpty() ? "Detalle del registro" : accion,
            AuditoriaColumnSetup.sobre(entry), AppColors.INDIGO, AppColors.INDIGO_D);

        GridPane datos = new GridPane();
        datos.setHgap(16);
        datos.setVgap(6);
        int r = 0;
        r = dato(datos, r, "Cuándo", entry.getCreadoEn() == null ? "—" : entry.getCreadoEn().format(EtiquetasAuditoria.CON_SEGUNDOS));
        r = dato(datos, r, "Quién", Objects.requireNonNullElse(entry.getUsuarioNombre(), "—"));
        r = dato(datos, r, "Sobre qué", EtiquetasAuditoria.entidadSingular(entry.getEntidad()) + " · "
            + Objects.requireNonNullElse(entry.getEntidadNombre(), "—"));
        if (l.numero() != null) r = dato(datos, r, "Número", l.numero());
        if (l.area() != null) dato(datos, r, "Área", l.area());

        VBox cuerpo = new VBox(12, datos);
        if (!l.cambios().isEmpty()) {
            Label titulo = new Label("Qué cambió");
            titulo.getStyleClass().add("dialog-field-label");
            GridPane tabla = new GridPane();
            tabla.setHgap(12);
            tabla.setVgap(6);
            ColumnConstraints c0 = new ColumnConstraints(), c1 = new ColumnConstraints(), c2 = new ColumnConstraints(), c3 = new ColumnConstraints();
            c0.setMinWidth(Region.USE_PREF_SIZE);
            c1.setPercentWidth(40);
            c3.setPercentWidth(40);
            tabla.getColumnConstraints().addAll(c0, c1, c2, c3);
            int f = 0;
            for (String t : List.of("Dato", "Antes", "", "Ahora")) {
                Label h = new Label(t);
                h.getStyleClass().add("muted-sm");
                tabla.add(h, f++, 0);
            }
            int fila = 1;
            for (LecturaAuditoria.Cambio c : l.cambios()) {
                Label campo = new Label(capital(c.campo()));
                campo.getStyleClass().add("dlg-detail-label");
                campo.setWrapText(true);
                if (c.antes() == null && c.ahora() == null) {
                    // «descripción», «foto agregada», or a note: without values.
                    tabla.add(campo, 0, fila++, 4, 1);
                    continue;
                }
                tabla.add(campo, 0, fila);
                tabla.add(valor(c.antes()), 1, fila);
                Label flecha = new Label("→");
                flecha.getStyleClass().add("muted");
                tabla.add(flecha, 2, fila);
                tabla.add(valor(c.ahora()), 3, fila);
                fila++;
            }
            cuerpo.getChildren().addAll(new Separator(), titulo, tabla);
        } else if (entry.getDetalle() != null && !entry.getDetalle().isBlank()) {
            Label titulo = new Label("Detalle");
            titulo.getStyleClass().add("dialog-field-label");
            Label texto = new Label(entry.getDetalle());
            texto.setWrapText(true);
            texto.getStyleClass().add("dlg-detail-value");
            texto.setMinHeight(Region.USE_PREF_SIZE);
            cuerpo.getChildren().addAll(new Separator(), titulo, texto);
        }

        Button copiar = new Button("Copiar");
        copiar.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2c-content-copy"));
        copiar.getStyleClass().add("btn-secondary");
        copiar.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(textoParaCopiar(entry, l));
            Clipboard.getSystemClipboard().setContent(cc);
            if (dlg.getDialogPane().getScene() != null) NotificacionUtil.exito(dlg.getDialogPane().getScene(), "Copiado");
        });
        HBox acciones = new HBox(8, copiar);
        acciones.setAlignment(Pos.CENTER_LEFT);
        if (verTodo != null && entry.getEntidadId() != null && !entry.getEntidadId().isBlank()) {
            Button todo = new Button("Ver todo lo de " + (l.numero() != null ? l.numero() : "este registro"));
            todo.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("mdi2h-history"));
            todo.getStyleClass().add("btn-secondary");
            todo.setOnAction(e -> { dlg.close(); verTodo.accept(entry); });
            acciones.getChildren().add(todo);
        }
        // Right under the data, not after the changes: with nine of them they fell below the fold.
        cuerpo.getChildren().add(1, acciones);
        cuerpo.setPadding(new Insets(16, 22, 16, 22));

        ScrollPane scroll = new ScrollPane(cuerpo);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        scroll.setPrefViewportHeight(Math.min(460, javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() * 0.55));
        VBox content = new VBox(0, header, scroll);
        dlg.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(List.of(header, scroll), 260, 70);
        dlg.showAndWait();
    }

    private static int dato(GridPane g, int fila, String etiqueta, String valor) {
        Label k = new Label(etiqueta);
        k.getStyleClass().add("dlg-detail-label");
        k.setMinWidth(90);
        Label v = new Label(valor);
        v.getStyleClass().add("dlg-detail-value");
        v.setWrapText(true);
        g.add(k, 0, fila);
        g.add(v, 1, fila);
        return fila + 1;
    }

    private static Label valor(String v) {
        Label l = new Label(v == null ? "—" : v);
        l.getStyleClass().add(v == null ? "muted" : "dlg-detail-value");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static String capital(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** What «Copiar» puts: the header line and one change per line. */
    static String textoParaCopiar(AuditLog e, LecturaAuditoria.Lectura l) {
        StringBuilder sb = new StringBuilder();
        if (e.getCreadoEn() != null) sb.append(e.getCreadoEn().format(EtiquetasAuditoria.CON_SEGUNDOS)).append(" · ");
        sb.append(EtiquetasAuditoria.accion(e.getAccion())).append(" · ").append(AuditoriaColumnSetup.sobre(e));
        if (l.area() != null) sb.append(" · ").append(l.area());
        if (e.getUsuarioNombre() != null) sb.append(" · ").append(e.getUsuarioNombre());
        if (!l.cambios().isEmpty()) for (LecturaAuditoria.Cambio c : l.cambios()) sb.append("\n").append(c.texto());
        else if (e.getDetalle() != null) sb.append("\n").append(e.getDetalle());
        return sb.toString();
    }
}
