package com.sibim.controller.dialogs;

import com.sibim.db.offline.ConflictoInfo;
import com.sibim.db.offline.SyncService;
import com.sibim.model.Producto;
import com.sibim.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shows a dialog listing all sync conflicts detected by {@link SyncService},
 * letting the user decide per product whether to apply their offline version or
 * keep the version already on the server. Decisions are applied asynchronously
 * via a virtual thread so the FX thread never blocks on DB I/O.
 */
public final class ConflictResolutionDialog {

    private ConflictResolutionDialog() {}

    /** Show the conflict dialog. Must be called on the JavaFX Application Thread. */
    public static void show(List<ConflictoInfo> conflictos) {
        if (conflictos == null || conflictos.isEmpty()) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Conflictos de sincronización");
        int n = conflictos.size();
        dialog.setHeaderText(
            (n == 1 ? "1 bien fue modificado" : n + " bienes fueron modificados")
            + " en otro equipo mientras estabas sin conexión.\n"
            + "Elige qué versión conservar para cada uno.");

        // Single button on purpose — see the note on showAndWait() below for why
        // there's no Cancel: every row already has a safe default selected
        // ("conservar versión del servidor"), so any way of leaving this dialog
        // resolves every conflict one way or another. A Cancel button that let
        // the user walk away with nothing decided used to leave the row stuck
        // forever as status='CONFLICT' — nothing else in the app ever re-reads
        // or re-shows an unresolved conflict, so it was effectively silent data
        // loss for that offline edit.
        ButtonType aplicarType = new ButtonType("Aplicar decisiones", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(aplicarType);

        // outboxId → ToggleGroup with selected toggle's userData = "SERVER" | "MINE"
        Map<Integer, ToggleGroup> decisions = new LinkedHashMap<>();

        VBox content = new VBox(12);
        content.setPadding(new Insets(4, 0, 4, 0));
        for (ConflictoInfo c : conflictos) {
            content.getChildren().add(buildCard(c, decisions));
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(660, 380);
        scroll.getStyleClass().add("conflict-scroll");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(700);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        // Deliberately unconditional: apply the current selections regardless
        // of how the dialog closed (the button, Escape, or the window's own
        // close box all end showAndWait() the same way here since there's no
        // second button to distinguish "cancel" from "confirm" anymore).
        dialog.showAndWait();
        applyDecisions(conflictos, decisions);
    }

    private static VBox buildCard(ConflictoInfo c, Map<Integer, ToggleGroup> decisions) {
        Producto off = c.versionOffline();
        Producto srv = c.versionServidor();

        VBox card = new VBox(10);
        card.setPadding(new Insets(12));
        card.getStyleClass().add("conflict-card");

        Label nameLabel = new Label(off.getNombre() != null ? off.getNombre() : "(sin nombre)");
        nameLabel.getStyleClass().add("conflict-card-title");

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(3);
        ColumnConstraints cc0 = new ColumnConstraints(); cc0.setPrefWidth(140);
        ColumnConstraints cc1 = new ColumnConstraints(); cc1.setPrefWidth(200);
        ColumnConstraints cc2 = new ColumnConstraints(); cc2.setPrefWidth(200);
        grid.getColumnConstraints().addAll(cc0, cc1, cc2);

        int row = 0;
        grid.add(bold("Campo"),           0, row);
        grid.add(bold("Tu versión"),      1, row);
        grid.add(bold("Servidor"),        2, row);
        row++;

        row = addRow(grid, row, "Stock actual",  str(off.getStockActual()),     srv == null ? "—" : str(srv.getStockActual()));
        row = addRow(grid, row, "Stock mínimo",  str(off.getStockMinimo()),     srv == null ? "—" : str(srv.getStockMinimo()));
        row = addRow(grid, row, "Área",          nvl(off.getArea()),            srv == null ? "—" : nvl(srv.getArea()));
        row = addRow(grid, row, "Resguardante",  nvl(off.getResguardante()),    srv == null ? "—" : nvl(srv.getResguardante()));
        row = addRow(grid, row, "Precio compra", moneda(off.getPrecioCompra()), srv == null ? "—" : moneda(srv.getPrecioCompra()));
        row = addRow(grid, row, "Proveedor",     nvl(off.getProveedor()),       srv == null ? "—" : nvl(srv.getProveedor()));
        addRow(grid, row, "Descripción",         nvl(off.getDescripcion()),     srv == null ? "—" : nvl(srv.getDescripcion()));

        ToggleGroup tg = new ToggleGroup();
        decisions.put(c.outboxId(), tg);

        RadioButton rbServer = new RadioButton("Conservar versión del servidor");
        rbServer.setToggleGroup(tg);
        rbServer.setSelected(true);
        rbServer.setUserData("SERVER");

        RadioButton rbMine = new RadioButton("Usar mi versión (offline)");
        rbMine.setToggleGroup(tg);
        rbMine.setUserData("MINE");

        HBox radios = new HBox(24, rbServer, rbMine);
        radios.setAlignment(Pos.CENTER_LEFT);
        radios.setPadding(new Insets(4, 0, 0, 0));

        card.getChildren().addAll(nameLabel, grid, new Separator(), radios);
        return card;
    }

    private static int addRow(GridPane grid, int row, String campo, String offline, String server) {
        boolean differs = !Objects.equals(offline, server);
        Label lCampo = new Label(campo);
        Label lOff   = new Label(offline);
        Label lSrv   = new Label(server);
        if (differs) {
            lOff.getStyleClass().add("conflict-differs");
            lSrv.getStyleClass().add("conflict-differs");
        }
        grid.add(lCampo, 0, row);
        grid.add(lOff,   1, row);
        grid.add(lSrv,   2, row);
        return row + 1;
    }

    private static void applyDecisions(List<ConflictoInfo> conflictos,
                                        Map<Integer, ToggleGroup> decisions) {
        Thread.ofVirtual().name("sibim-conflict-resolve").start(() -> {
            for (ConflictoInfo c : conflictos) {
                ToggleGroup tg = decisions.get(c.outboxId());
                boolean usarMio = tg != null
                    && tg.getSelectedToggle() != null
                    && "MINE".equals(tg.getSelectedToggle().getUserData());
                SyncService.resolveConflicto(c.outboxId(), usarMio ? c.versionOffline() : null);
            }
        });
    }

    private static Label bold(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("conflict-bold");
        return l;
    }

    private static String str(int v)          { return String.valueOf(v); }
    private static String nvl(String s)       { return s != null && !s.isBlank() ? s : "—"; }
    private static String moneda(BigDecimal v) {
        return v == null ? "—" : "$" + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
