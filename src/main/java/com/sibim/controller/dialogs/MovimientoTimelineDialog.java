package com.sibim.controller.dialogs;

import com.sibim.model.AuditLog;
import com.sibim.model.Movimiento;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.repository.AuditLogRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.PrestamoService;
import com.sibim.service.ResguardoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * "Cadena de custodia" del bien: une en una sola línea de tiempo cronológica
 * los movimientos, resguardos, préstamos y (si el usuario es admin) el
 * registro de auditoría — antes había que cruzar a mano cada una de esas
 * cuatro pantallas para reconstruir quién ha tenido un bien.
 */
public final class MovimientoTimelineDialog {

    private MovimientoTimelineDialog() {}

    enum Fuente { MOVIMIENTO, RESGUARDO, PRESTAMO, AUDITORIA }

    record Entrada(LocalDateTime fecha, Fuente fuente, String iconLit,
                    String badgeClass, String etiqueta, String detalle) {}

    record TimelineData(List<Movimiento> movs, List<Resguardo> resguardos,
                         List<Prestamo> prestamos, List<AuditLog> auditoria) {}

    public static void show(Producto p, Scene ownerScene, MovimientoService movimientoService) {
        DialogUtil.runAsyncWithProgress(ownerScene, "Cargando historial…",
            () -> {
                List<Movimiento> movs        = movimientoService.getByProducto(p.getId());
                List<Resguardo> resguardos   = new ResguardoService().getByProductoId(p.getId());
                List<Prestamo> prestamos     = new PrestamoService().getByProductoId(p.getId());
                // Auditoría es admin-only (ver AuditLogRepository#requireAdmin) — un
                // usuario sin ese rol simplemente no ve esa parte de la línea de tiempo.
                List<AuditLog> auditoria = SessionManager.isAdmin()
                    ? new AuditLogRepository().findByEntidadId("producto", p.getId())
                    : List.of();
                return new TimelineData(movs, resguardos, prestamos, auditoria);
            },
            data -> {
                List<Entrada> entradas = mergeEntradas(data);

                Dialog<ButtonType> dlg = new Dialog<>();
                DialogUtil.applyOwner(dlg);
                dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                dlg.getDialogPane().setPrefWidth(560);
                dlg.getDialogPane().setPrefHeight(520);
                DialogUtil.applyStylesheet(dlg.getDialogPane());

                VBox content = new VBox(0);
                content.getStyleClass().add("timeline-root");
                HBox header = DialogUtil.gradientHeader(
                    "mdi2h-history", "Cadena de custodia",
                    p.getNombre() + "  ·  " + entradas.size() + " registro(s)",
                    AppColors.SLATE, AppColors.SLATE_D);
                content.getChildren().add(header);

                ScrollPane scroll = new ScrollPane();
                scroll.setFitToWidth(true);
                scroll.getStyleClass().add("edge-to-edge");
                VBox list = new VBox(0);
                list.getStyleClass().add("timeline-list");
                list.setPadding(new Insets(8, 16, 16, 16));

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                if (entradas.isEmpty()) {
                    Label empty = new Label("Sin historial registrado");
                    empty.getStyleClass().add("muted-sm");
                    empty.setPadding(new Insets(24, 0, 0, 0));
                    list.getChildren().add(empty);
                } else {
                    for (Entrada en : entradas) {
                        HBox row = new HBox(10);
                        row.getStyleClass().add("timeline-row");
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(8, 4, 8, 4));

                        FontIcon ico = new FontIcon(en.iconLit());
                        ico.setIconSize(16);
                        ico.getStyleClass().add("timeline-icon");

                        Label tipo = new Label(en.etiqueta());
                        tipo.getStyleClass().addAll("audit-pill", en.badgeClass());
                        tipo.setMinWidth(90);

                        VBox details = new VBox(1);
                        Label fechaLbl = new Label(en.fecha() != null ? en.fecha().format(fmt) : "—");
                        fechaLbl.getStyleClass().add("muted-sm");
                        Label detLbl = new Label(en.detalle());
                        detLbl.getStyleClass().add("timeline-detail");
                        detLbl.setWrapText(true);
                        details.getChildren().addAll(fechaLbl, detLbl);
                        HBox.setHgrow(details, Priority.ALWAYS);
                        row.getChildren().addAll(ico, tipo, details);
                        list.getChildren().add(row);
                    }
                }
                scroll.setContent(list);
                scroll.setPrefHeight(430);
                content.getChildren().add(scroll);
                VBox.setVgrow(scroll, Priority.ALWAYS);
                dlg.getDialogPane().setContent(content);
                AnimationUtils.staggeredFadeInUp(
                    List.of(header, scroll), 240, 60);
                dlg.showAndWait();
            },
            ex -> NotificacionUtil.error(ownerScene, "No se pudo cargar el historial"));
    }

    static List<Entrada> mergeEntradas(TimelineData data) {
        List<Entrada> entradas = new ArrayList<>();

        for (Movimiento m : data.movs()) {
            String iconLit = switch (m.getTipo()) {
                case ENTRADA       -> "mdi2a-arrow-down-circle-outline";
                case SALIDA        -> "mdi2a-arrow-up-circle-outline";
                case AJUSTE        -> "mdi2a-adjust";
                case TRANSFERENCIA -> "mdi2s-swap-horizontal";
                default            -> "mdi2c-circle-outline";
            };
            String badgeClass = switch (m.getTipo()) {
                case ENTRADA       -> "audit-pill-green";
                case SALIDA        -> "audit-pill-red";
                case AJUSTE        -> "audit-pill-blue";
                case TRANSFERENCIA -> "audit-pill-purple";
                default            -> "audit-pill-orange";
            };
            String detalle = (m.getCantidad() > 0 ? "+" : "") + m.getCantidad()
                + "  →  stock: " + m.getStockAnterior() + " → " + m.getStockNuevo()
                + (m.getMotivo() != null && !m.getMotivo().isBlank() ? "  ·  " + m.getMotivo() : "")
                + "  ·  " + (m.getUsuarioNombre() != null ? m.getUsuarioNombre() : "—");
            entradas.add(new Entrada(m.getCreadoEn(), Fuente.MOVIMIENTO, iconLit, badgeClass,
                m.getTipo().getEtiqueta(), detalle));
        }

        for (Resguardo r : data.resguardos()) {
            String detalle = "Resguardante: " + (r.getResguardanteNombre() != null ? r.getResguardanteNombre() : "—")
                + (r.getResguardanteArea() != null ? "  ·  " + r.getResguardanteArea() : "")
                + "  ·  " + r.getEstado();
            entradas.add(new Entrada(r.getCreadoEn(), Fuente.RESGUARDO, "mdi2c-clipboard-account-outline",
                "audit-pill-purple", "Resguardo " + (r.getNumero() != null ? r.getNumero() : ""), detalle));
        }

        for (Prestamo pr : data.prestamos()) {
            String detalle = "Responsable: " + (pr.getResponsableNombre() != null ? pr.getResponsableNombre() : "—")
                + (pr.getAreaDestino() != null ? "  ·  " + pr.getAreaDestino() : "")
                + "  ·  " + pr.getEstado();
            LocalDateTime fecha = pr.getFechaPrestamo() != null ? pr.getFechaPrestamo().atStartOfDay() : pr.getCreadoEn();
            entradas.add(new Entrada(fecha, Fuente.PRESTAMO, "mdi2h-hand-extended-outline",
                "audit-pill-blue", "Préstamo " + (pr.getNumero() != null ? pr.getNumero() : ""), detalle));
        }

        for (AuditLog a : data.auditoria()) {
            String detalle = (a.getDetalle() != null && !a.getDetalle().isBlank() ? a.getDetalle() : "—")
                + "  ·  " + (a.getUsuarioNombre() != null ? a.getUsuarioNombre() : "—");
            entradas.add(new Entrada(a.getCreadoEn(), Fuente.AUDITORIA, "mdi2s-shield-search-outline",
                "audit-pill-orange", a.getAccion() != null ? a.getAccion() : "Auditoría", detalle));
        }

        return entradas.stream()
            .sorted((a, b) -> {
                if (a.fecha() == null) return 1;
                if (b.fecha() == null) return -1;
                return b.fecha().compareTo(a.fecha());
            })
            .toList();
    }
}
