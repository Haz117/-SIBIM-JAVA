package com.sibim.controller;

import com.sibim.model.Comodato;
import com.sibim.util.EmptyStateUtil;
import com.sibim.util.FormatUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.*;

import java.util.List;
import java.util.function.Supplier;

/** "Comodatos vencidos": lent out and past their return date. */
class AlertasComodatosSection {

    private final Supplier<Node> okPlaceholder;
    private final TableView<Comodato> tabla;

    AlertasComodatosSection(Supplier<Node> okPlaceholder) {
        this.okPlaceholder = okPlaceholder;
        this.tabla = buildTable();
    }

    TableView<Comodato> tabla() { return tabla; }

    /** @param q the search text in lower case, blank when not searching
     *  @return how many comodatos match */
    int mostrar(List<Comodato> todos, String q) {
        List<Comodato> visibles = q.isBlank() ? todos : todos.stream()
            .filter(c -> contiene(c.getProductoNombre(), q) || contiene(c.getProductoCodigo(), q)
                      || contiene(c.getEntidadReceptora(), q))
            .toList();
        tabla.setPlaceholder(q.isBlank() || todos.isEmpty() ? okPlaceholder.get() : EmptyStateUtil.buildSearch(q));
        tabla.getItems().setAll(visibles);
        return visibles.size();
    }

    private static boolean contiene(String texto, String q) {
        return texto != null && texto.toLowerCase().contains(q);
    }

    private static TableView<Comodato> buildTable() {
        TableView<Comodato> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setTableMenuButtonVisible(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setId("tableComodatos");

        TableColumn<Comodato, String> colNombre = new TableColumn<>("Bien");
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductoNombre()));
        colNombre.setPrefWidth(220);

        TableColumn<Comodato, String> colEntidad = new TableColumn<>("Entidad Receptora");
        colEntidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEntidadReceptora()));
        colEntidad.setPrefWidth(200);

        TableColumn<Comodato, String> colFechaFin = new TableColumn<>("Fecha Fin");
        colFechaFin.setCellValueFactory(c -> {
            java.time.LocalDate ff = c.getValue().getFechaFin();
            return new SimpleStringProperty(ff != null ? FormatUtils.formatDate(ff) : "—");
        });
        colFechaFin.setPrefWidth(120);
        colFechaFin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                getStyleClass().removeAll("stock-low");
                if (empty || v == null) { setText(null); return; }
                setText(v);
                getStyleClass().add("stock-low");
            }
        });

        table.getColumns().addAll(List.of(colNombre, colEntidad, colFechaFin));
        return table;
    }
}
