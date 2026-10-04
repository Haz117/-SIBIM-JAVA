package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * "Acomodar" board of the Organigrama (admin only): Presidencia and every
 * secretaría are columns, their direcciones are chips. Dragging a chip onto
 * another column asks to move the dirección there. Unlike the other views it
 * shows every area, also the ones without bienes: an empty secretaría still
 * has to be a place to drop on.
 */
class OrganigramaAcomodoBuilder {

    private static final DataFormat AREA = new DataFormat("application/x-sibim-area");

    private final Map<String, List<Producto>> productosPorArea;
    /** (dirección, new parent) — the controller confirms and saves. */
    private final BiConsumer<String, String> onMover;

    OrganigramaAcomodoBuilder(Map<String, List<Producto>> productosPorArea, BiConsumer<String, String> onMover) {
        this.productosPorArea = productosPorArea;
        this.onMover = onMover;
    }

    void build(VBox orgTree, String filter) {
        orgTree.getChildren().clear();
        String q = filter == null ? "" : filter.toLowerCase().trim();

        FlowPane board = new FlowPane(14, 14);
        board.setPadding(new Insets(4, 0, 8, 0));

        board.getChildren().add(columna(Areas.PRESIDENCIA, Areas.direccionesPresidencia(), q));
        for (Areas.SecretariaInfo sec : Areas.secretarias())
            board.getChildren().add(columna(sec.nombre(), sec.direcciones(), q));

        orgTree.getChildren().add(board);
    }

    private VBox columna(String padre, List<String> direcciones, String q) {
        Label nombre = new Label(padre);
        nombre.getStyleClass().add("org-area-name");
        nombre.setWrapText(true);
        Label cuenta = new Label(FormatUtils.plural(direcciones.size(), "dependencia", "dependencias"));
        cuenta.getStyleClass().add("org-table-sub");
        FontIcon icono = new FontIcon("mdi2o-office-building-outline");
        icono.setIconSize(16);
        icono.getStyleClass().add("org-section-icon");
        VBox titulos = new VBox(1, nombre, cuenta);
        HBox.setHgrow(titulos, Priority.ALWAYS);
        HBox encabezado = new HBox(8, icono, titulos);
        encabezado.setAlignment(Pos.TOP_LEFT);

        VBox fichas = new VBox(6);
        List<String> visibles = new ArrayList<>();
        for (String d : direcciones)
            if (q.isEmpty() || d.toLowerCase().contains(q) || padre.toLowerCase().contains(q)) visibles.add(d);
        for (String d : visibles) fichas.getChildren().add(ficha(d, padre));
        if (visibles.isEmpty()) {
            Label vacio = new Label(direcciones.isEmpty() ? "Sin dependencias — suelta aquí una dirección"
                                                          : "Ninguna coincide con la búsqueda");
            vacio.getStyleClass().add("org-acomodo-vacio");
            vacio.setWrapText(true);
            fichas.getChildren().add(vacio);
        }

        VBox col = new VBox(10, encabezado, fichas);
        col.getStyleClass().add("org-acomodo-col");

        col.setOnDragOver(e -> {
            if (vieneDeOtra(e.getDragboard(), padre)) e.acceptTransferModes(TransferMode.MOVE);
            e.consume();
        });
        col.setOnDragEntered(e -> {
            if (vieneDeOtra(e.getDragboard(), padre)) col.getStyleClass().add("org-acomodo-col-drop");
        });
        col.setOnDragExited(e -> col.getStyleClass().remove("org-acomodo-col-drop"));
        col.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean ok = vieneDeOtra(db, padre);
            String direccion = ok ? ((String) db.getContent(AREA)).split("\t", 2)[0] : null;
            e.setDropCompleted(ok);
            e.consume();
            // After the drop finishes: the handler opens a confirmation dialog.
            if (ok) javafx.application.Platform.runLater(() -> onMover.accept(direccion, padre));
        });
        return col;
    }

    /** The dragboard carries "dirección \t current parent". */
    private static boolean vieneDeOtra(Dragboard db, String padre) {
        if (!db.hasContent(AREA)) return false;
        String[] f = ((String) db.getContent(AREA)).split("\t", 2);
        return f.length == 2 && !f[1].equals(padre);
    }

    private HBox ficha(String direccion, String padre) {
        FontIcon asa = new FontIcon("mdi2d-drag-vertical");
        asa.setIconSize(16);
        asa.getStyleClass().add("org-acomodo-asa");
        Label nombre = new Label(direccion);
        nombre.getStyleClass().add("org-child-label");
        nombre.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nombre, Priority.ALWAYS);
        int bienes = productosPorArea.getOrDefault(direccion, List.of()).size();
        Label cuenta = new Label(String.valueOf(bienes));
        cuenta.getStyleClass().add("org-area-count");
        Tooltip.install(cuenta, new Tooltip(FormatUtils.plural(bienes, "bien", "bienes")));

        HBox ficha = new HBox(8, asa, nombre, cuenta);
        ficha.setAlignment(Pos.CENTER_LEFT);
        ficha.getStyleClass().add("org-acomodo-ficha");
        Tooltip.install(ficha, new Tooltip("Arrastra a otra secretaría para cambiar de quién depende"));

        ficha.setOnDragDetected(e -> {
            Dragboard db = ficha.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent contenido = new ClipboardContent();
            contenido.put(AREA, direccion + "\t" + padre);
            db.setContent(contenido);
            SnapshotParameters sp = new SnapshotParameters();
            sp.setFill(Color.TRANSPARENT);
            db.setDragView(ficha.snapshot(sp, null), e.getX(), e.getY());
            ficha.getStyleClass().add("org-acomodo-ficha-arrastrando");
            e.consume();
        });
        ficha.setOnDragDone(e -> ficha.getStyleClass().remove("org-acomodo-ficha-arrastrando"));
        return ficha;
    }
}
