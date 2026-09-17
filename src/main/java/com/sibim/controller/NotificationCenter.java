package com.sibim.controller;

import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import javafx.animation.ScaleTransition;
import javafx.animation.Interpolator;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A single global entry point for "things that need attention" — before
 *  this, the only way to see stock alerts or overdue préstamos was to
 *  navigate to their respective screens (or notice the small nav-sidebar
 *  badges, which aren't visible once a screen is open and the sidebar is
 *  collapsed). Deliberately reuses the exact same counts already computed
 *  for the sidebar's alertBadge/loanBadge — this is a presentation layer on
 *  top of that data, not a new persisted notification log, so there's no
 *  new table/migration and "read/unread" state doesn't apply: everything
 *  shown here is a live, current condition, not a past event. */
class NotificationCenter {

    private record Item(String icon, String colorClass, String text, String targetView) {}

    private NotificationCenter() {}

    static void setup(Button btn, Label badge, ProductoService productoService,
                       PrestamoService prestamoService, Consumer<String> navigate) {
        if (btn == null) return;
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("notif-center-menu");

        btn.setOnAction(e -> {
            menu.getItems().setAll(loadingItem());
            menu.show(btn, Side.TOP, 0, -8);
            loadItems(productoService, prestamoService, items ->
                menu.getItems().setAll(buildMenuItems(items, menu, navigate)));
        });

        refreshBadge(badge, productoService, prestamoService);
    }

    /** Called from the same 3-minute badge-refresh timer MainController
     *  already runs for the sidebar badges, so the bell's count stays in
     *  sync with them without a second timer. */
    static void refreshBadge(Label badge, ProductoService productoService, PrestamoService prestamoService) {
        if (badge == null) return;
        loadItems(productoService, prestamoService, items -> {
            int total = items.size();
            if (total > 0) {
                badge.setText(total > 99 ? "99+" : String.valueOf(total));
                boolean wasHidden = !badge.isVisible();
                badge.setVisible(true);
                badge.setManaged(true);
                if (wasHidden) {
                    ScaleTransition pop = new ScaleTransition(Duration.millis(320), badge);
                    pop.setFromX(0.3); pop.setFromY(0.3);
                    pop.setToX(1.0);   pop.setToY(1.0);
                    pop.setInterpolator(Interpolator.EASE_OUT);
                    pop.play();
                } else {
                    AnimationUtils.pulse(badge, 2);
                }
            } else {
                badge.setVisible(false);
                badge.setManaged(false);
            }
        });
    }

    private static void loadItems(ProductoService productoService, PrestamoService prestamoService,
                                   Consumer<List<Item>> onLoaded) {
        DialogUtil.runAsync(() -> {
            List<Item> items = new ArrayList<>();
            int agotados = productoService.getAgotados().size();
            if (agotados > 0) items.add(new Item("mdi2p-package-variant-closed", "danger",
                agotados == 1 ? "1 bien agotado" : agotados + " bienes agotados", "alertas"));

            int bajoStock = productoService.getBajoStock().size();
            if (bajoStock > 0) items.add(new Item("mdi2t-trending-down", "warning",
                bajoStock == 1 ? "1 bien con stock bajo" : bajoStock + " bienes con stock bajo", "alertas"));

            int garantias = productoService.getVencidosProximos(30).size();
            if (garantias > 0) items.add(new Item("mdi2c-calendar-alert", "warning",
                garantias == 1 ? "1 garantía por vencer en 30 días" : garantias + " garantías por vencer en 30 días",
                "alertas"));

            int vencidos = prestamoService.countVencidos();
            if (vencidos > 0) items.add(new Item("mdi2c-clock-alert-outline", "danger",
                vencidos == 1 ? "1 préstamo vencido" : vencidos + " préstamos vencidos", "prestamos"));

            int proximos = prestamoService.getProximosAVencer(3).size();
            if (proximos > 0) items.add(new Item("mdi2c-clock-outline", "info",
                proximos == 1 ? "1 préstamo vence en 3 días" : proximos + " préstamos vencen en 3 días", "prestamos"));

            return items;
        }, onLoaded, ex -> onLoaded.accept(List.of()));
    }

    private static MenuItem loadingItem() {
        MenuItem it = new MenuItem("Cargando…");
        it.setDisable(true);
        return it;
    }

    private static List<MenuItem> buildMenuItems(List<Item> items, ContextMenu menu, Consumer<String> navigate) {
        if (items.isEmpty()) {
            MenuItem empty = new MenuItem("Sin notificaciones pendientes");
            empty.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
            empty.setDisable(true);
            return List.of(empty);
        }
        List<MenuItem> menuItems = new ArrayList<>();
        for (Item item : items) {
            FontIcon icon = new FontIcon(item.icon());
            icon.getStyleClass().add("notif-item-icon-" + item.colorClass());
            Label lbl = new Label(item.text());
            lbl.getStyleClass().add("notif-item-text");
            HBox row = new HBox(10, icon, lbl);
            row.setAlignment(Pos.CENTER_LEFT);
            CustomMenuItem mi = new CustomMenuItem(row, true);
            mi.setOnAction(e -> { menu.hide(); navigate.accept(item.targetView()); });
            menuItems.add(mi);
        }
        return menuItems;
    }
}
