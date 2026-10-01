package com.sibim.controller.dialogs;

import com.sibim.db.offline.OfflineUserCache;
import com.sibim.service.AccesosEquipoService;
import com.sibim.service.AccesosEquipoService.Cuenta;
import com.sibim.service.AccesosEquipoService.Equipo;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** For the administrator: which PCs each account has signed in on, so the
 *  accounts that could not work without internet are found before an outage. */
public final class AccesosEquipoDialog {

    private AccesosEquipoDialog() {}

    public static void show(Scene scene) {
        DialogUtil.runAsync(
            () -> new AccesosEquipoService().porCuenta(),
            AccesosEquipoDialog::mostrar,
            e -> NotificacionUtil.error(scene, e instanceof IllegalStateException ? e.getMessage()
                : "No se pudieron cargar los accesos por equipo"));
    }

    private static void mostrar(List<Cuenta> cuentas) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(700);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        long sinEntrar = cuentas.stream().filter(c -> c.activa() && c.equipos().isEmpty()).count();
        HBox header = DialogUtil.gradientHeader("mdi2l-laptop",
            "Equipos por cuenta",
            sinEntrar == 0
                ? "Todas las cuentas activas ya entraron en alguna computadora"
                : sinEntrar + " cuenta(s) aún no entran en ninguna computadora: sin internet no podrán entrar",
            AppColors.PRIMARY_D, AppColors.INDIGO);

        Label nota = new Label("Una cuenta puede entrar sin internet solo en las computadoras donde ya entró con "
            + "conexión en los últimos " + OfflineUserCache.OFFLINE_CACHE_TTL_DAYS + " días. Haz ese primer ingreso "
            + "al instalar SIBIM en cada equipo.");
        nota.getStyleClass().add("muted-sm");
        nota.setWrapText(true);
        nota.setPadding(new Insets(4, 6, 8, 6));

        VBox list = new VBox(6, nota);
        list.setPadding(new Insets(4));
        LocalDateTime limite = LocalDateTime.now().minusDays(OfflineUserCache.OFFLINE_CACHE_TTL_DAYS);
        for (Cuenta c : cuentas) {
            if (!c.activa()) continue;
            Label titulo = new Label(c.nombre() + "  (@" + c.username() + ")");
            titulo.getStyleClass().add("dlg-detail-value");
            titulo.setWrapText(true);
            String equipos = c.equipos().isEmpty()
                ? (c.area() != null ? c.area() + " · " : "") + "No ha entrado en ninguna computadora"
                : c.equipos().stream().map(AccesosEquipoDialog::texto).collect(Collectors.joining("   ·   "));
            Label detalle = new Label(equipos);
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            VBox info = new VBox(3, titulo, detalle);
            HBox.setHgrow(info, Priority.ALWAYS);

            boolean vigente = c.equipos().stream()
                .anyMatch(e -> e.ultimoAcceso() != null && e.ultimoAcceso().isAfter(limite));
            Label estado = new Label(c.equipos().isEmpty() ? "Sin ingreso" : vigente ? "Listo sin internet" : "Vencido");
            estado.getStyleClass().addAll("cell-badge", c.equipos().isEmpty() ? "cell-badge-danger"
                : vigente ? "cell-badge-success" : "cell-badge-warning");
            estado.setMinWidth(Region.USE_PREF_SIZE);

            HBox row = new HBox(12, info, estado);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(440);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    private static String texto(Equipo e) {
        return e.nombre() + " (" + (e.ultimoAcceso() != null ? FormatUtils.formatDateTime(e.ultimoAcceso()) : "—") + ")";
    }
}
