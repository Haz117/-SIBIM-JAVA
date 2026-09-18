package com.sibim.controller;

import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

class MainShortcutHelpDialog {

    static void show() {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(540);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2k-keyboard-outline", "Atajos de Teclado",
            "Referencia rápida de todos los atajos disponibles en SIBIM",
            AppColors.INDIGO, AppColors.INDIGO_D);

        java.util.function.BiFunction<String, String[][], GridPane> makeSection = (title, rows) -> {
            GridPane g = new GridPane();
            g.setHgap(20); g.setVgap(5);
            g.setPadding(new Insets(6, 16, 10, 16));
            Label tit = new Label(title);
            tit.getStyleClass().add("nav-section-label");
            tit.setPadding(new Insets(0, 0, 4, 0));
            g.add(tit, 0, 0, 2, 1);
            for (int i = 0; i < rows.length; i++) {
                Label key = new Label(rows[i][0]);
                key.getStyleClass().add("shortcut-key");
                Label desc = new Label(rows[i][1]);
                desc.getStyleClass().add("shortcut-desc");
                desc.setWrapText(true);
                desc.setMaxWidth(320);
                g.add(key, 0, i + 1);
                g.add(desc, 1, i + 1);
            }
            return g;
        };

        GridPane navGrid = makeSection.apply("NAVEGACIÓN", new String[][]{
            {"Ctrl + 1",      "Dashboard"},
            {"Ctrl + 2",      "Organigrama"},
            {"Ctrl + 3",      "Bienes / Inventario"},
            {"Ctrl + 4",      "Categorías"},
            {"Ctrl + 5",      "Movimientos"},
            {"Ctrl + 6",      "Alertas"},
            {"Ctrl + 7",      "Reportes"},
            {"Ctrl + 8",      "Depreciación"},
            {"Ctrl + 9",      "Configuración"},
            {"Ctrl+Alt + G",  "Resguardos"},
            {"Ctrl+Alt + P",  "Préstamos"},
            {"Ctrl+Alt + A",  "Actas E/R"},
        });

        GridPane accGrid = makeSection.apply("ACCIONES EN TABLA", new String[][]{
            {"Ctrl + N",        "Nuevo registro (Bienes / Movimientos / Categorías)"},
            {"Ctrl + E",        "Editar fila seleccionada (Bienes / Categorías)"},
            {"Ctrl + I",        "Importar bienes desde CSV (sólo en Bienes)"},
            {"Supr",            "Dar de baja / eliminar fila seleccionada"},
            {"Escape",          "Deseleccionar todas las filas de la tabla"},
            {"Doble clic",      "Ver detalle del registro"},
            {"F5",              "Actualizar datos de la vista actual"},
            {"Ctrl / Shift+clic","Selección múltiple — activa barra de acciones en lote (sólo Bienes)"},
        });

        GridPane busqGrid = makeSection.apply("BÚSQUEDA Y FILTROS", new String[][]{
            {"Escribir",             "Búsqueda en tiempo real (con debounce 280 ms)"},
            {"✕ (botón)",            "Limpiar campo de búsqueda"},
            {"Hoy / Semana / Mes",   "Presets de rango de fechas en Movimientos y Reportes"},
            {"Guardar preset",        "Guarda los filtros activos como preset con nombre (sólo Bienes)"},
            {"Clic en chip de preset","Aplica o elimina un preset de filtros guardado (sólo Bienes)"},
            {"Clic derecho en fila",  "Menú contextual: Ver detalle · Imprimir ficha técnica · (editar/baja si admin)"},
        });

        GridPane sysGrid = makeSection.apply("SISTEMA", new String[][]{
            {"Ctrl+K",      "Búsqueda global / paleta de comandos"},
            {"F1",          "Mostrar esta ayuda de atajos"},
            {"F2",          "Tutorial interactivo del sistema"},
            {"F5 / Ctrl+R", "Actualizar vista actual"},
            {"Ctrl+F",      "Enfocar campo de búsqueda"},
            {"Ctrl+0",      "Auditoría del sistema  (solo administrador)"},
        });

        VBox scrollable = new VBox(0, navGrid, new Separator(),
            accGrid, new Separator(), busqGrid,
            new Separator(), sysGrid);
        ScrollPane scroll = new ScrollPane(scrollable);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        // Cap total height to the screen so this never grows taller than the
        // window it's opening over — with 4 sections + this many rows it
        // easily could, especially at "Grande"/"Extra grande" text size.
        double maxH = javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() * 0.82;
        scroll.setMaxHeight(maxH);

        VBox content = new VBox(0, header, scroll);
        dlg.getDialogPane().setContent(content);
        com.sibim.util.AnimationUtils.staggeredFadeInUp(
            java.util.List.of(header, navGrid, accGrid, busqGrid, sysGrid), 260, 60);
        dlg.showAndWait();
    }
}
