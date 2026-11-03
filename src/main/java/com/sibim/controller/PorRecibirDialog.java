package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.service.MovimientoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Transfers already applied that the receiving área still has to confirm
 *  it physically has — same layout as {@link PendientesDialog}. Grouped by área:
 *  Patrimonio confirms an área's whole batch at once when its acuse arrives
 *  signed on paper (an área without an account can't do it in SIBIM). */
class PorRecibirDialog {

    private final MovimientoService service;
    private final Runnable onDataRefresh;
    /** Patrimonio confirms for the áreas, with their acuse on paper. */
    private final boolean enPapel = SessionManager.isAdmin();

    PorRecibirDialog(MovimientoService service, Runnable onDataRefresh) {
        this.service       = service;
        this.onDataRefresh = onDataRefresh;
    }

    void show(List<Movimiento> porRecibir) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2i-inbox-arrow-down-outline",
            "Bienes por recibir",
            enPapel ? "Transferencias que el área aún no confirma: confírmalas cuando tengas su acuse firmado"
                    : "Transferencias hacia tu área: confirma cuando tengas el bien físicamente",
            AppColors.PURPLE, AppColors.PURPLE_D);

        VBox list = new VBox(10);
        list.setPadding(new Insets(4));

        if (porRecibir.isEmpty()) {
            Label empty = new Label("No hay bienes por recibir");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }

        // The área with the most first: that is the paper that clears the most.
        Map<String, List<Movimiento>> porArea = porRecibir.stream()
            .collect(Collectors.groupingBy(m -> m.getAreaDestino() != null ? m.getAreaDestino() : "—",
                LinkedHashMap::new, Collectors.toList()));
        porArea.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, List<Movimiento>> e) -> -e.getValue().size())
                .thenComparing(Map.Entry::getKey))
            .forEach(e -> list.getChildren().add(grupo(dialog, list, e.getKey(), new ArrayList<>(e.getValue()))));

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    /** One área: its name, how many, "Confirmar las N" for Patrimonio, and its rows. */
    private VBox grupo(Dialog<ButtonType> dialog, VBox list, String area, List<Movimiento> suyos) {
        VBox grupo = new VBox(6);
        Label titulo = new Label();
        titulo.getStyleClass().add("org-area-name");
        Runnable contar = () -> titulo.setText(area + " · " + FormatUtils.plural(suyos.size(), "bien", "bienes"));
        contar.run();
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox encabezado = new HBox(8, titulo, espacio);
        encabezado.setAlignment(Pos.CENTER_LEFT);
        grupo.getChildren().add(encabezado);

        VBox filas = new VBox(6);
        grupo.getChildren().add(filas);
        Runnable quitar = () -> {
            contar.run();
            if (suyos.isEmpty()) list.getChildren().remove(grupo);
        };
        for (Movimiento m : suyos) filas.getChildren().add(fila(dialog, m, filas, suyos, quitar));

        if (enPapel && suyos.size() > 1) {
            Button todas = new Button();
            todas.setGraphic(new FontIcon("mdi2c-check-all"));
            todas.getStyleClass().add("btn-secondary");
            todas.setMinWidth(Region.USE_PREF_SIZE);
            Runnable rotular = () -> todas.setText("Confirmar " + (suyos.size() == 1 ? "la" : "las " + suyos.size()));
            rotular.run();
            todas.setOnAction(e -> {
                List<Movimiento> lote = List.copyOf(suyos);
                if (!ConfirmacionUtil.confirmar("Confirmar recepción",
                        "¿" + area + " ya firmó de recibido " + (lote.size() == 1 ? "el bien" : "los " + lote.size() + " bienes")
                        + "?\n\nÚsalo cuando tengas su acuse firmado en papel. Queda registrado con tu nombre, "
                        + "«acuse en papel» y la fecha de hoy.")) return;
                todas.setDisable(true);
                DialogUtil.runAsync(() -> {
                    List<Movimiento> hechos = new ArrayList<>();
                    for (Movimiento m : lote) {
                        try { service.confirmarRecepcion(m, true); hechos.add(m); }
                        catch (MovimientoService.ValidationException ya) { hechos.add(m); }   // already confirmed elsewhere
                    }
                    return hechos;
                }, hechos -> {
                    int i = 0;
                    for (Movimiento m : hechos) {
                        int pos = suyos.indexOf(m);
                        if (pos >= 0) { suyos.remove(pos); filas.getChildren().remove(pos); i++; }
                    }
                    quitar.run();
                    rotular.run();
                    todas.setDisable(suyos.isEmpty());
                    onDataRefresh.run();
                    NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                        FormatUtils.plural(i, "recepción confirmada", "recepciones confirmadas") + " de " + area);
                }, ex -> {
                    todas.setDisable(false);
                    NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudieron confirmar todas; vuelve a intentarlo");
                });
            });
            encabezado.getChildren().add(todas);
        }
        return grupo;
    }

    private HBox fila(Dialog<ButtonType> dialog, Movimiento m, VBox filas, List<Movimiento> suyos, Runnable quitar) {
        HBox row = new HBox(12);
        row.getStyleClass().add("dlg-detail-header");
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(3);
        Label titulo = new Label(m.getProductoNombre()
            + (m.getCodigoNuevo() != null ? "  [" + m.getCodigoNuevo() + "]" : ""));
        titulo.getStyleClass().add("dlg-detail-value");
        titulo.setWrapText(true);
        Label detalle = new Label(
            "De " + (m.getAreaOrigen() != null ? m.getAreaOrigen() : "—") + " → " + m.getAreaDestino()
            + " · " + FormatUtils.formatDateTime(m.getCreadoEn())
            + (m.getMotivo() != null && !m.getMotivo().isBlank() ? " · " + m.getMotivo() : ""));
        detalle.getStyleClass().add("muted-sm");
        detalle.setWrapText(true);
        info.getChildren().addAll(titulo, detalle);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button btnRecibir = new Button("Confirmar recepción");
        btnRecibir.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
        btnRecibir.setContentDisplay(ContentDisplay.LEFT);
        btnRecibir.getStyleClass().add("btn-primary");
        btnRecibir.setMinWidth(Region.USE_PREF_SIZE);
        btnRecibir.setOnAction(e -> {
            if (!ConfirmacionUtil.confirmar("Confirmar recepción",
                    "¿Confirmas que " + m.getAreaDestino() + " ya tiene \"" + m.getProductoNombre() + "\"?\n\n"
                    + (enPapel ? "Úsalo cuando tengas su acuse firmado en papel. Queda registrado con tu nombre, «acuse en papel» y la fecha de hoy."
                               : "Quedará registrado con tu nombre y la fecha de hoy."))) return;
            btnRecibir.setDisable(true);
            DialogUtil.runAsync(
                () -> service.confirmarRecepcion(m, enPapel),
                () -> {
                    int pos = suyos.indexOf(m);
                    if (pos >= 0) suyos.remove(pos);
                    filas.getChildren().remove(row);
                    quitar.run();
                    onDataRefresh.run();
                    NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                        "Recepción de \"" + m.getProductoNombre() + "\" confirmada");
                },
                ex -> {
                    btnRecibir.setDisable(false);
                    NotificacionUtil.error(dialog.getDialogPane().getScene(),
                        ex instanceof MovimientoService.ValidationException
                            ? ex.getMessage() : "No se pudo confirmar la recepción");
                }
            );
        });

        row.getChildren().addAll(info, btnRecibir);
        return row;
    }
}
