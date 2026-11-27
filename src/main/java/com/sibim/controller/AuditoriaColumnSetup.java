package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.service.LecturaAuditoria;
import com.sibim.util.DialogUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

/** The columns of Auditoría: when, which bien (its número first), its área, the acción as a
 *  pill, who, and what changed (19-nov-2026: «Datos del bien actualizados · número …» was all
 *  the Detalle column showed). Package-private — only used by AuditoriaController. */
final class AuditoriaColumnSetup {

    private AuditoriaColumnSetup() {}

    /** Each entry read once, as long as it is on screen. */
    private static final Map<AuditLog, LecturaAuditoria.Lectura> LECTURAS = new WeakHashMap<>();

    static synchronized LecturaAuditoria.Lectura lectura(AuditLog a) {
        return LECTURAS.computeIfAbsent(a, LecturaAuditoria::de);
    }

    /** «SSPCR/19 · LAPTOP ACER…» for a bien; «Resguardo · PERSONA…» for the rest. */
    static String sobre(AuditLog a) {
        String nombre = Objects.requireNonNullElse(a.getEntidadNombre(), "").trim();
        if ("producto".equals(a.getEntidad())) {
            String numero = lectura(a).numero();
            return numero == null ? nombre : numero + " · " + nombre;
        }
        String que = EtiquetasAuditoria.entidadSingular(a.getEntidad());
        if (nombre.isEmpty()) return que;
        return que.isEmpty() || que.equalsIgnoreCase(nombre) ? nombre : que + " · " + nombre;
    }

    static void configure(TableView<AuditLog> table,
                          TableColumn<AuditLog, LocalDateTime> colCuando,
                          TableColumn<AuditLog, String> colSobre,
                          TableColumn<AuditLog, String> colArea,
                          TableColumn<AuditLog, String> colAccion,
                          TableColumn<AuditLog, String> colQuien,
                          TableColumn<AuditLog, String> colCambio,
                          Preferences sticky) {

        // The date as a date (sorting by its text put «Ayer» before «Hoy»), shown short.
        colCuando.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCreadoEn()));
        colCuando.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(LocalDateTime t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setText(null); setTooltip(null); return; }
                setText(EtiquetasAuditoria.cuando(t, LocalDate.now()));
                tip.setText(t.format(EtiquetasAuditoria.CON_SEGUNDOS));
                setTooltip(tip);
            }
        });

        colSobre.setCellValueFactory(c -> new SimpleStringProperty(sobre(c.getValue())));
        colArea.setCellValueFactory(c -> new SimpleStringProperty(Objects.requireNonNullElse(lectura(c.getValue()).area(), "")));
        com.sibim.util.CeldaArea.instalar(colArea);

        colAccion.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAccion()));
        // A compact pill with the action in words («Inicio de sesión», «Baja»).
        colAccion.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("audit-pill"); setContentDisplay(ContentDisplay.GRAPHIC_ONLY); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setGraphic(null); setText(null); return; }
                pill.setText(EtiquetasAuditoria.accion(item));
                pill.getStyleClass().removeAll("audit-pill-verde", "audit-pill-guinda", "audit-pill-dorado", "audit-pill-neutro");
                pill.getStyleClass().add(EtiquetasAuditoria.tono(item));
                setText(null);
                setGraphic(pill);
            }
        });

        colQuien.setCellValueFactory(c -> new SimpleStringProperty(Objects.requireNonNullElse(c.getValue().getUsuarioNombre(), "—")));

        colCambio.setCellValueFactory(c -> new SimpleStringProperty(lectura(c.getValue()).resumen()));
        colCambio.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            { tip.setWrapText(true); tip.setMaxWidth(480); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                // One change per line in the tooltip.
                AuditLog a = getTableRow() != null ? getTableRow().getItem() : null;
                var cambios = a == null ? java.util.List.<LecturaAuditoria.Cambio>of() : lectura(a).cambios();
                tip.setText(cambios.isEmpty() ? item
                    : String.join("\n", cambios.stream().map(LecturaAuditoria.Cambio::texto).toList()));
                setTooltip(tip);
            }
        });

        DialogUtil.persistTableSort(table, sticky, "sort");
        DialogUtil.persistColumnWidths(table, sticky, "colW");
    }
}
