package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.util.*;

/**
 * Bienes › Más › Costo de los lotes (6-nov-2026). A bien of several pieces carries
 * the cost of ONE piece; when the factura's total was captured there instead, the
 * inventory counts it once per piece (RCE/41 and RCE/42 added about $1.27 millones
 * that way). Each lote is checked against its factura and, when its cost is the
 * total, divided among the pieces. Every change is audited and can be undone.
 */
public final class CostoLotesDialog {

    private final ProductoService service = new ProductoService();
    /** Cost per piece each bien had before it was divided: the way back. */
    private final Map<String, BigDecimal> antes = new HashMap<>();
    private final Runnable alCambiar;
    private boolean cambio;
    private TableView<Producto> tabla;

    private CostoLotesDialog(Runnable alCambiar) { this.alCambiar = alCambiar; }

    /** @param alCambiar run on closing when a cost changed */
    public static void show(List<Producto> bienes, Runnable alCambiar) {
        new CostoLotesDialog(alCambiar).abrir(CalidadDatosService.lotesPorRevisar(bienes));
    }

    private void abrir(List<Producto> lotes) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Costo de los lotes");
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dlg.getDialogPane().setPrefSize(Math.min(1100, pantalla.getWidth() * 0.94), Math.min(680, pantalla.getHeight() * 0.88));
        dlg.setResizable(true);
        HBox header = DialogUtil.gradientHeader("mdi2c-calculator-variant-outline", "Costo de los lotes",
            FormatUtils.plural(lotes.size(), "bien con varias piezas", "bienes con varias piezas")
                + " · el costo es el de una pieza; el total sale de multiplicarlo",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        FontIcon icono = new FontIcon("mdi2i-information-outline");
        icono.setIconSize(18);
        Label aviso = new Label("Revisa cada lote contra su factura. Si el costo capturado es el TOTAL de la factura "
            + "(no el de una pieza), pulsa «Es el total del lote»: el costo queda dividido entre las piezas. "
            + "Los lotes van de mayor a menor total, así salen primero los que más pesan en el inventario.");
        aviso.setWrapText(true);
        aviso.setMinHeight(Region.USE_PREF_SIZE);
        aviso.getStyleClass().add("muted-sm");
        HBox.setHgrow(aviso, Priority.ALWAYS);
        HBox cajaAviso = new HBox(10, icono, aviso);
        cajaAviso.setAlignment(Pos.CENTER_LEFT);
        cajaAviso.getStyleClass().add("cnt-aviso");

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar por código, nombre o área…");
        buscar.getStyleClass().add("search-field");
        FilteredList<Producto> visibles = new FilteredList<>(FXCollections.observableArrayList(lotes));
        buscar.textProperty().addListener((o, a, q) -> {
            String t = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
            visibles.setPredicate(p -> t.isEmpty() || (Objects.toString(p.getCodigo(), "") + " " + Objects.toString(p.getNombre(), "")
                + " " + Objects.toString(p.getArea(), "")).toLowerCase(Locale.ROOT).contains(t));
        });

        tabla = new TableView<>(visibles);
        tabla.getStyleClass().add("data-table");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tabla.setPlaceholder(new Label("No hay bienes con varias piezas y costo capturado"));
        tabla.getColumns().add(texto("Código", 90, p -> Objects.toString(p.getCodigo(), "")));
        tabla.getColumns().add(texto("Bien", 260, p -> Objects.toString(p.getNombre(), "")));
        tabla.getColumns().add(texto("Área", 170, p -> Objects.toString(p.getArea(), "")));
        tabla.getColumns().add(texto("Piezas", 80, p -> String.valueOf(p.getStockActual())));
        tabla.getColumns().add(texto("Costo por pieza", 135, p -> FormatUtils.formatCurrency(p.getPrecioCompra())));
        tabla.getColumns().add(texto("Total del lote", 130, p -> FormatUtils.formatCurrency(p.getValorTotal())));
        tabla.getColumns().add(columnaAccion());
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label pie = new Label("Al dividir, el costo por pieza se redondea a centavos; el total puede variar unos centavos.");
        pie.getStyleClass().add("muted-sm");
        VBox cuerpo = new VBox(10, cajaAviso, buscar, tabla, pie);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dlg.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dlg.setOnHidden(e -> { if (cambio && alCambiar != null) alCambiar.run(); });
        dlg.showAndWait();
    }

    private static TableColumn<Producto, String> texto(String titulo, double ancho,
                                                       java.util.function.Function<Producto, String> valor) {
        TableColumn<Producto, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        if (ancho <= 135) c.setMinWidth(ancho);
        return c;
    }

    /** "Es el total del lote" divides it; once divided, "Deshacer" puts it back. */
    private TableColumn<Producto, Producto> columnaAccion() {
        TableColumn<Producto, Producto> c = new TableColumn<>("");
        c.setCellValueFactory(cd -> new SimpleObjectProperty<>(cd.getValue()));
        c.setPrefWidth(190);
        c.setMinWidth(185);
        c.setSortable(false);
        c.setCellFactory(col -> new TableCell<>() {
            private final Button boton = new Button();
            {
                boton.getStyleClass().addAll("btn-secondary", "btn-sm");
                boton.setMinWidth(Region.USE_PREF_SIZE);
            }
            @Override protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                setText(null);
                if (empty || p == null) { setGraphic(null); return; }
                boolean dividido = antes.containsKey(p.getId());
                boton.setText(dividido ? "Deshacer" : "Es el total del lote");
                boton.setGraphic(new FontIcon(dividido ? "mdi2u-undo" : "mdi2d-division"));
                boton.setOnAction(e -> { if (dividido) deshacer(p); else dividir(p); });
                setGraphic(boton);
            }
        });
        return c;
    }

    private void dividir(Producto p) {
        BigDecimal pieza = CalidadDatosService.costoPorPieza(p.getPrecioCompra(), p.getStockActual());
        if (pieza == null) return;
        if (!ConfirmacionUtil.confirmar("Costo de los lotes",
            p.getCodigo() + " · " + p.getNombre() + "\n\n"
                + "Costo capturado: " + FormatUtils.formatCurrency(p.getPrecioCompra()) + " (como total del lote)\n"
                + "Piezas: " + p.getStockActual() + "\n"
                + "Costo por pieza: " + FormatUtils.formatCurrency(pieza) + "\n\n"
                + "El total del lote queda en " + FormatUtils.formatCurrency(pieza.multiply(BigDecimal.valueOf(p.getStockActual())))
                + " en lugar de " + FormatUtils.formatCurrency(p.getValorTotal()) + ". ¿Lo dividimos?")) return;
        cambiarCosto(p, p.getPrecioCompra(), pieza, true);
    }

    private void deshacer(Producto p) {
        BigDecimal original = antes.get(p.getId());
        if (original != null) cambiarCosto(p, p.getPrecioCompra(), original, false);
    }

    private void cambiarCosto(Producto p, BigDecimal anterior, BigDecimal nuevo, boolean dividiendo) {
        p.setPrecioCompra(nuevo);
        DialogUtil.runAsync(() -> service.save(p),
            guardado -> {
                p.setPrecioCompra(guardado.getPrecioCompra());
                if (dividiendo) antes.put(p.getId(), anterior); else antes.remove(p.getId());
                cambio = true;
                tabla.refresh();
                NotificacionUtil.exito(tabla.getScene(), dividiendo
                    ? p.getCodigo() + ": costo por pieza " + FormatUtils.formatCurrency(nuevo)
                    : p.getCodigo() + ": regresó el costo de " + FormatUtils.formatCurrency(nuevo));
            },
            e -> {
                p.setPrecioCompra(anterior);
                tabla.refresh();
                NotificacionUtil.error(tabla.getScene(), "No se pudo guardar el costo de " + p.getCodigo() + ": " + e.getMessage());
            });
    }
}
