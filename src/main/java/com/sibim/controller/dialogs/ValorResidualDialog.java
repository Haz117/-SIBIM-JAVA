package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.ResidualDeCategoria;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.ProductoUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

/** Bienes › Más and Inicio › Por hacer › «Poner el 15 %»: the bienes with a costo and the valor
 *  residual at $0, by categoría, to give them the 15 % of their costo (19-nov-2026: 285 of the
 *  296 that have one carry exactly that; 1,588 with a costo had $0). A categoría where no bien
 *  has it yet comes unticked: there is no criterion to follow there (Defensa y Seguridad, 257).
 *  What it does to the valor en libros is said before, as the ticks change. */
public final class ValorResidualDialog {

    private ValorResidualDialog() {}

    /** @return the bienes of the ticked categorías, or empty if declined */
    public static Optional<List<Producto>> show(List<Producto> inventario) {
        List<ResidualDeCategoria> categorias = CalidadDatosService.residualPorCategoria(inventario);
        if (categorias.isEmpty()) return Optional.empty();
        long[] criterio = CalidadDatosService.criterioDelResidual(inventario);
        String pct = ProductoUtils.PORCENTAJE_RESIDUAL.toPlainString() + " %";
        int total = categorias.stream().mapToInt(c -> c.sinResidual().size()).sum();

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Valor residual: el " + pct + " del costo");
        ButtonType ok = new ButtonType("Poner el " + pct, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(700, pantalla.getWidth() * 0.94), Math.min(640, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        DialogUtil.styleOkButton(dialog.getDialogPane(), AppColors.PRIMARY_D);
        HBox header = DialogUtil.gradientHeader("mdi2c-cash-check", "Valor residual: el " + pct + " del costo",
            cuantos(total, "bien tiene", "bienes tienen") + " costo y el valor residual en $0",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        Label criterioLbl = new Label(criterio[1] > 0
            ? String.format("%,d de los %,d bienes que ya tienen valor residual llevan el %s de su costo. ", criterio[0], criterio[1], pct)
                + "Marca las categorías que lo llevan; las que no tienen ningún bien con él vienen sin marcar."
            : "Marca las categorías que llevan el " + pct + " de su costo como valor residual.");
        criterioLbl.getStyleClass().add("cnt-aviso");
        criterioLbl.setWrapText(true);
        criterioLbl.setMaxWidth(Double.MAX_VALUE);
        criterioLbl.setMinHeight(Region.USE_PREF_SIZE);
        criterioLbl.setPadding(new Insets(8, 12, 8, 12));

        Map<ResidualDeCategoria, CheckBox> marcas = new LinkedHashMap<>();
        GridPane lista = new GridPane();
        lista.setHgap(14);
        lista.setVgap(6);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setHgrow(Priority.ALWAYS);
        lista.getColumnConstraints().addAll(c0, new ColumnConstraints());
        int fila = 0;
        for (ResidualDeCategoria c : categorias) {
            CheckBox cb = new CheckBox(c.categoria());
            cb.setSelected(c.conElQuince() > 0);
            cb.setWrapText(true);
            cb.setMaxWidth(Double.MAX_VALUE);
            Label lblCuantos = new Label(cuantos(c.sinResidual().size(), "bien", "bienes") + " · "
                + (c.conElQuince() > 0 ? cuantos(c.conElQuince(), "ya lo tiene", "ya lo tienen")
                    : "ninguno lo tiene todavía"));
            lblCuantos.getStyleClass().add(c.conElQuince() > 0 ? "muted-sm" : "field-hint-warn");
            lblCuantos.setMinWidth(Region.USE_PREF_SIZE);
            cb.setAccessibleHelp(lblCuantos.getText());
            lista.add(cb, 0, fila);
            lista.add(lblCuantos, 1, fila++);
            marcas.put(c, cb);
        }
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label efecto = new Label();
        efecto.getStyleClass().add("table-count-label");
        efecto.setWrapText(true);
        efecto.setMinHeight(Region.USE_PREF_SIZE);
        Label ayuda = new Label("Los que ya tienen otro valor residual no se tocan. Se deshace desde el aviso.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        Button boton = (Button) dialog.getDialogPane().lookupButton(ok);
        Runnable contar = () -> {
            List<Producto> elegidos = elegidos(marcas);
            BigDecimal[] libros = CalidadDatosService.librosConResidual(elegidos);
            efecto.setText(cuantos(elegidos.size(), "bien", "bienes") + " de " + String.format("%,d", total)
                + (libros[0].compareTo(libros[1]) == 0 ? ""
                    : ". En los que ya se deprecian, el valor en libros pasa de " + FormatUtils.formatCurrency(libros[0])
                        + " a " + FormatUtils.formatCurrency(libros[1]) + ": la depreciación se detiene en el " + pct + " de su costo."));
            boton.setText("Poner el " + pct + " a " + cuantos(elegidos.size(), "bien", "bienes"));
            boton.setDisable(elegidos.isEmpty());
        };
        marcas.values().forEach(cb -> cb.selectedProperty().addListener((o, a, b) -> contar.run()));
        contar.run();

        VBox cuerpo = new VBox(10, criterioLbl, scroll, efecto, ayuda);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        cuerpo.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ok) return Optional.empty();
        List<Producto> elegidos = elegidos(marcas);
        return elegidos.isEmpty() ? Optional.empty() : Optional.of(elegidos);
    }

    /** «1,307 bienes»: with the thousands, as the rest of the window. */
    private static String cuantos(long n, String uno, String varios) {
        return String.format("%,d", n) + " " + (n == 1 ? uno : varios);
    }

    static List<Producto> elegidos(Map<ResidualDeCategoria, CheckBox> marcas) {
        List<Producto> out = new ArrayList<>();
        marcas.forEach((c, cb) -> { if (cb.isSelected()) out.addAll(c.sinResidual()); });
        return out;
    }
}
