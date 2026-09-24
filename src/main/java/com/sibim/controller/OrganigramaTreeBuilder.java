package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Comodato;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

class OrganigramaTreeBuilder {

    @FunctionalInterface
    interface AreaDialogHandler {
        void show(String areaName, List<Producto> prods, boolean soloAlertasOnly);
    }

    private final Map<String, List<Producto>>  productosPorArea;
    private final Map<String, List<Resguardo>> resguardosPorArea;
    private final Map<String, List<Prestamo>>  prestamosPorArea;
    private final Map<String, List<Comodato>>  comodatosPorArea;
    private final boolean soloAlertas;
    private final AreaDialogHandler onAreaClick;
    private final BiConsumer<String, List<Resguardo>> onRsgClick;
    private final BiConsumer<String, List<Prestamo>>  onPrestClick;
    private final BiConsumer<String, List<Comodato>>  onComodClick;

    OrganigramaTreeBuilder(Map<String, List<Producto>> productosPorArea,
                           Map<String, List<Resguardo>> resguardosPorArea,
                           boolean soloAlertas,
                           Map<String, List<Prestamo>> prestamosPorArea,
                           Map<String, List<Comodato>> comodatosPorArea,
                           AreaDialogHandler onAreaClick,
                           BiConsumer<String, List<Resguardo>> onRsgClick,
                           BiConsumer<String, List<Prestamo>> onPrestClick,
                           BiConsumer<String, List<Comodato>> onComodClick) {
        this.productosPorArea  = productosPorArea;
        this.resguardosPorArea = resguardosPorArea;
        this.soloAlertas       = soloAlertas;
        this.prestamosPorArea  = prestamosPorArea;
        this.comodatosPorArea  = comodatosPorArea;
        this.onAreaClick       = onAreaClick;
        this.onRsgClick        = onRsgClick;
        this.onPrestClick      = onPrestClick;
        this.onComodClick      = onComodClick;
    }

    void build(VBox orgTree, String filter) {
        orgTree.getChildren().clear();
        String q = filter == null ? "" : filter.toLowerCase();
        Set<String> accessible = SessionManager.getAccessibleAreas();

        if (accessible == null || accessible.contains(Areas.PRESIDENCIA)
                || anyAccessible(accessible, Areas.DIRECCIONES_PRESIDENCIA))
            addAreaSection(orgTree, Areas.PRESIDENCIA, Areas.DIRECCIONES_PRESIDENCIA, q, true, accessible);

        for (Areas.SecretariaInfo sec : Areas.SECRETARIAS) {
            if (accessible == null || accessible.contains(sec.nombre())
                    || anyAccessible(accessible, sec.direcciones()))
                addAreaSection(orgTree, sec.nombre(), sec.direcciones(), q, false, accessible);
        }

        if (accessible == null || anyAccessible(accessible, Areas.AUTONOMOS))
            addAreaSection(orgTree, "Organismos Autónomos", Areas.AUTONOMOS, q, false, accessible);

        if (orgTree.getChildren().isEmpty()) {
            FontIcon icon = new FontIcon("mdi2o-office-building-outline");
            icon.setIconSize(44);
            icon.getStyleClass().add("empty-icon-lg");
            Label msg = new Label(q.isBlank()
                ? "No hay áreas con bienes registrados"
                : "No se encontraron áreas para \"" + filter + "\"");
            msg.getStyleClass().add("empty-state-msg");
            Label hint = new Label(q.isBlank()
                ? "Registra bienes con área asignada en la sección Bienes"
                : "Intenta con otro término de búsqueda");
            hint.getStyleClass().add("empty-state-hint");
            VBox empty = new VBox(10, icon, msg, hint);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("empty-state-pane");
            empty.setPadding(new Insets(48, 24, 48, 24));
            orgTree.getChildren().add(empty);
        }
        AnimationUtils.staggeredFadeInUp(orgTree.getChildren(), 270, 50);
    }

    private void addAreaSection(VBox orgTree, String parentName, List<String> children,
                                 String filter, boolean expanded, Set<String> accessible) {
        List<Producto> allAreaProds = new ArrayList<>(
            productosPorArea.getOrDefault(parentName, List.of()));
        children.forEach(c -> allAreaProds.addAll(productosPorArea.getOrDefault(c, List.of())));

        if (soloAlertas) {
            boolean hasAlert = allAreaProds.stream().anyMatch(
                p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK);
            if (!hasAlert) return;
        }

        if (!filter.isBlank()) {
            boolean nameMatch = parentName.toLowerCase().contains(filter)
                || children.stream().anyMatch(c -> c.toLowerCase().contains(filter));
            if (!nameMatch && !matchesFilter(allAreaProds, filter)) return;
        }

        TitledPane section = new TitledPane();
        section.setExpanded(expanded || !filter.isBlank());

        // ── Header ────────────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 4, 2, 0));

        StackPane iconBadge = new StackPane();
        iconBadge.getStyleClass().add("org-section-icon-badge");
        FontIcon iconLbl = new FontIcon("mdi2o-office-building-outline");
        iconLbl.setIconSize(18);
        iconLbl.getStyleClass().add("org-section-icon");
        iconBadge.getChildren().add(iconLbl);

        Label nameLabel = new Label(parentName);
        nameLabel.getStyleClass().add("org-area-name");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        int totalBienes = allAreaProds.size();
        Label countLabel = new Label(totalBienes + " bienes");
        countLabel.getStyleClass().add("org-area-count");
        if (totalBienes > 0) {
            countLabel.getStyleClass().add("org-area-count-clickable");
            countLabel.setOnMouseClicked(e -> { e.consume(); onAreaClick.show(parentName, allAreaProds, false); });
            Tooltip.install(countLabel, new Tooltip("Ver todos los bienes de " + parentName + " y sus dependencias"));
        }

        Label valorLabel = new Label(FormatUtils.formatCurrency(valorPatrimonial(allAreaProds)));
        valorLabel.getStyleClass().add("org-area-valor");

        header.getChildren().addAll(iconBadge, nameLabel, valorLabel, countLabel);

        long alertasArea = allAreaProds.stream()
            .filter(p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK)
            .count();
        if (alertasArea > 0) {
            FontIcon alertIcon = new FontIcon("mdi2a-alert-circle");
            alertIcon.setIconSize(12);
            Label alertDot = new Label(" " + alertasArea);
            alertDot.setGraphic(alertIcon);
            alertDot.setContentDisplay(ContentDisplay.LEFT);
            alertDot.getStyleClass().addAll("org-alert-badge", "org-alert-badge-clickable");
            alertDot.setOnMouseClicked(e -> { e.consume(); onAreaClick.show(parentName, allAreaProds, true); });
            Tooltip.install(alertDot, new Tooltip(alertasArea + " bien(es) agotado(s) o bajo stock en esta área — clic para verlos"));
            header.getChildren().add(alertDot);
        }

        List<Resguardo> rsgArea = new ArrayList<>(resguardosPorArea.getOrDefault(parentName, List.of()));
        for (String child : children) rsgArea.addAll(resguardosPorArea.getOrDefault(child, List.of()));
        if (!rsgArea.isEmpty()) {
            FontIcon rsgIcon = new FontIcon("mdi2c-clipboard-account-outline");
            rsgIcon.setIconSize(12);
            Label rsgDot = new Label(" " + rsgArea.size());
            rsgDot.setGraphic(rsgIcon);
            rsgDot.setContentDisplay(ContentDisplay.LEFT);
            rsgDot.getStyleClass().addAll("org-resguardo-badge", "org-alert-badge-clickable");
            final List<Resguardo> rsgFinal = List.copyOf(rsgArea);
            rsgDot.setOnMouseClicked(e -> { e.consume(); onRsgClick.accept(parentName, rsgFinal); });
            Tooltip.install(rsgDot, new Tooltip(rsgArea.size() + " resguardo(s) activo(s) en esta área — clic para verlos"));
            header.getChildren().add(rsgDot);
        }

        List<Prestamo> prestArea = new ArrayList<>(prestamosPorArea.getOrDefault(parentName, List.of()));
        for (String child : children) prestArea.addAll(prestamosPorArea.getOrDefault(child, List.of()));
        if (!prestArea.isEmpty()) {
            FontIcon prestIcon = new FontIcon("mdi2c-clipboard-arrow-right-outline");
            prestIcon.setIconSize(12);
            Label prestDot = new Label(" " + prestArea.size());
            prestDot.setGraphic(prestIcon);
            prestDot.setContentDisplay(ContentDisplay.LEFT);
            prestDot.getStyleClass().addAll("org-prestamo-badge", "org-alert-badge-clickable");
            final List<Prestamo> prestFinal = List.copyOf(prestArea);
            prestDot.setOnMouseClicked(e -> { e.consume(); onPrestClick.accept(parentName, prestFinal); });
            Tooltip.install(prestDot, new Tooltip(prestArea.size() + " préstamo(s) activo(s) en esta área — clic para verlos"));
            header.getChildren().add(prestDot);
        }

        List<Comodato> comodArea = new ArrayList<>(comodatosPorArea.getOrDefault(parentName, List.of()));
        for (String child : children) comodArea.addAll(comodatosPorArea.getOrDefault(child, List.of()));
        if (!comodArea.isEmpty()) {
            FontIcon comodIcon = new FontIcon("mdi2h-handshake-outline");
            comodIcon.setIconSize(12);
            Label comodDot = new Label(" " + comodArea.size());
            comodDot.setGraphic(comodIcon);
            comodDot.setContentDisplay(ContentDisplay.LEFT);
            comodDot.getStyleClass().addAll("org-comodato-badge", "org-alert-badge-clickable");
            final List<Comodato> comodFinal = List.copyOf(comodArea);
            comodDot.setOnMouseClicked(e -> { e.consume(); onComodClick.accept(parentName, comodFinal); });
            Tooltip.install(comodDot, new Tooltip(comodArea.size() + " comodato(s) activo(s) en esta área — clic para verlos"));
            header.getChildren().add(comodDot);
        }

        if (!SessionManager.isAdmin()
                && SessionManager.getCurrentUser().getArea() != null
                && SessionManager.getCurrentUser().getArea().equals(parentName)) {
            Label badge = new Label("Tu área");
            badge.getStyleClass().add("org-my-area-badge");
            header.getChildren().add(badge);
        }
        section.setGraphic(header);

        // ── Content ───────────────────────────────────────────────────
        VBox content = new VBox(8);
        content.setPadding(new Insets(10, 8, 10, 12));

        List<Producto> directProds = productosPorArea.getOrDefault(parentName, List.of());
        if (!directProds.isEmpty()) {
            addProductPreview(content, directProds, parentName);
            if (!children.isEmpty()) {
                Separator sep = new Separator();
                sep.getStyleClass().add("org-sep");
                content.getChildren().add(sep);
            }
        }

        for (String child : children) {
            if (accessible != null && !accessible.contains(child)) continue;
            List<Producto> childProds = productosPorArea.getOrDefault(child, List.of());
            if (!filter.isBlank() && !child.toLowerCase().contains(filter)
                    && !matchesFilter(childProds, filter)) continue;

            boolean isMyArea = !SessionManager.isAdmin()
                && child.equals(SessionManager.getCurrentUser().getArea());

            VBox childCard = new VBox(0);
            childCard.getStyleClass().add("org-child-card");
            if (isMyArea) childCard.getStyleClass().add("org-child-card-myarea");
            VBox.setMargin(childCard, new Insets(0, 0, 0, 8));

            HBox childHeader = new HBox(8);
            childHeader.getStyleClass().add("org-child-header");
            childHeader.setAlignment(Pos.CENTER_LEFT);
            childHeader.setPadding(new Insets(9, 12, 9, 12));

            FontIcon dirIcon = new FontIcon("mdi2f-folder-outline");
            dirIcon.setIconSize(14);
            dirIcon.getStyleClass().add("org-dir-icon");
            Label childName = new Label(child);
            childName.getStyleClass().add("org-child-label");
            childName.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(childName, Priority.ALWAYS);

            Label childValor = new Label(FormatUtils.formatCurrency(valorPatrimonial(childProds)));
            childValor.getStyleClass().add("org-area-valor");

            Label childCount = new Label(String.valueOf(childProds.size()));
            childCount.getStyleClass().add("org-area-count");
            if (isMyArea) childCount.getStyleClass().add("org-area-count-myarea");

            childHeader.getChildren().addAll(dirIcon, childName, childValor, childCount);

            long alertasChild = childProds.stream()
                .filter(p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK)
                .count();
            if (alertasChild > 0) {
                FontIcon alertIcon = new FontIcon("mdi2a-alert-circle");
                alertIcon.setIconSize(12);
                Label alertDot = new Label(" " + alertasChild);
                alertDot.setGraphic(alertIcon);
                alertDot.setContentDisplay(ContentDisplay.LEFT);
                alertDot.getStyleClass().addAll("org-alert-badge", "org-alert-badge-clickable");
                alertDot.setOnMouseClicked(e -> { e.consume(); onAreaClick.show(child, childProds, true); });
                Tooltip.install(alertDot, new Tooltip(alertasChild + " bien(es) agotado(s) o bajo stock en " + child + " — clic para verlos"));
                childHeader.getChildren().add(alertDot);
            }

            List<Resguardo> rsgChild = resguardosPorArea.getOrDefault(child, List.of());
            if (!rsgChild.isEmpty()) {
                FontIcon rsgIcon = new FontIcon("mdi2c-clipboard-account-outline");
                rsgIcon.setIconSize(12);
                Label rsgDot = new Label(" " + rsgChild.size());
                rsgDot.setGraphic(rsgIcon);
                rsgDot.setContentDisplay(ContentDisplay.LEFT);
                rsgDot.getStyleClass().addAll("org-resguardo-badge", "org-alert-badge-clickable");
                final List<Resguardo> rsgChildFinal = List.copyOf(rsgChild);
                rsgDot.setOnMouseClicked(e -> { e.consume(); onRsgClick.accept(child, rsgChildFinal); });
                Tooltip.install(rsgDot, new Tooltip(rsgChild.size() + " resguardo(s) activo(s) en " + child + " — clic para verlos"));
                childHeader.getChildren().add(rsgDot);
            }

            List<Prestamo> prestChild = prestamosPorArea.getOrDefault(child, List.of());
            if (!prestChild.isEmpty()) {
                FontIcon prestIcon = new FontIcon("mdi2c-clipboard-arrow-right-outline");
                prestIcon.setIconSize(12);
                Label prestDot = new Label(" " + prestChild.size());
                prestDot.setGraphic(prestIcon);
                prestDot.setContentDisplay(ContentDisplay.LEFT);
                prestDot.getStyleClass().addAll("org-prestamo-badge", "org-alert-badge-clickable");
                final List<Prestamo> prestChildFinal = List.copyOf(prestChild);
                prestDot.setOnMouseClicked(e -> { e.consume(); onPrestClick.accept(child, prestChildFinal); });
                Tooltip.install(prestDot, new Tooltip(prestChild.size() + " préstamo(s) activo(s) en " + child + " — clic para verlos"));
                childHeader.getChildren().add(prestDot);
            }

            List<Comodato> comodChild = comodatosPorArea.getOrDefault(child, List.of());
            if (!comodChild.isEmpty()) {
                FontIcon comodIcon = new FontIcon("mdi2h-handshake-outline");
                comodIcon.setIconSize(12);
                Label comodDot = new Label(" " + comodChild.size());
                comodDot.setGraphic(comodIcon);
                comodDot.setContentDisplay(ContentDisplay.LEFT);
                comodDot.getStyleClass().addAll("org-comodato-badge", "org-alert-badge-clickable");
                final List<Comodato> comodChildFinal = List.copyOf(comodChild);
                comodDot.setOnMouseClicked(e -> { e.consume(); onComodClick.accept(child, comodChildFinal); });
                Tooltip.install(comodDot, new Tooltip(comodChild.size() + " comodato(s) activo(s) en " + child + " — clic para verlos"));
                childHeader.getChildren().add(comodDot);
            }

            if (isMyArea) {
                Label badge = new Label("Tu área");
                badge.getStyleClass().add("org-my-area-badge");
                childHeader.getChildren().add(badge);
            }
            if (!childProds.isEmpty()) {
                String areaKey = child;
                childHeader.setOnMouseClicked(e -> onAreaClick.show(areaKey, childProds, false));
                Tooltip.install(childHeader, new Tooltip("Ver todos los bienes de " + child));
            }
            childCard.getChildren().add(childHeader);

            if (!childProds.isEmpty()) {
                VBox prodsBox = new VBox(0);
                prodsBox.getStyleClass().add("org-products-inner-box");
                prodsBox.setPadding(new Insets(0, 8, 8, 8));
                addProductPreview(prodsBox, childProds, child);
                childCard.getChildren().add(prodsBox);
            }

            content.getChildren().add(childCard);
        }

        section.setContent(content);
        orgTree.getChildren().add(section);
    }

    private HBox buildProductRow(Producto p) {
        HBox row = new HBox(8);
        row.getStyleClass().add("org-product-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 8, 4, 12));
        Label code = new Label(p.getCodigo());
        code.getStyleClass().add("org-product-code");
        code.setMinWidth(80);
        Label name = new Label(p.getNombre());
        name.getStyleClass().add("org-product-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label stock = new Label("Stock: " + p.getStockActual());
        stock.getStyleClass().add("org-product-stock");
        stock.getStyleClass().add(switch (p.getEstado()) {
            case AGOTADO    -> "stock-low";
            case BAJO_STOCK -> "stock-warn";
            default         -> "stock-ok";
        });
        row.getChildren().addAll(code, name, stock);
        return row;
    }

    private void addProductPreview(Pane container, List<Producto> prods, String areaName) {
        int preview = Math.min(prods.size(), 5);
        for (int i = 0; i < preview; i++)
            container.getChildren().add(buildProductRow(prods.get(i)));

        if (prods.size() > 5) {
            List<javafx.scene.Node> extras = new ArrayList<>();
            for (int j = 5; j < prods.size(); j++) {
                HBox row = buildProductRow(prods.get(j));
                row.setVisible(false); row.setManaged(false);
                container.getChildren().add(row);
                extras.add(row);
            }
            Hyperlink more = new Hyperlink("  +" + (prods.size() - 5) + " bienes más");
            more.getStyleClass().add("org-more-link");
            Hyperlink less = new Hyperlink("  Mostrar menos");
            less.getStyleClass().add("org-more-link");
            less.setVisible(false); less.setManaged(false);
            more.setOnAction(e -> {
                extras.forEach(n -> { n.setVisible(true); n.setManaged(true); });
                AnimationUtils.staggeredFadeInUp(extras, 200, 40);
                more.setVisible(false); more.setManaged(false);
                less.setVisible(true);  less.setManaged(true);
            });
            less.setOnAction(e -> {
                extras.forEach(n -> { n.setVisible(false); n.setManaged(false); });
                less.setVisible(false); less.setManaged(false);
                more.setVisible(true);  more.setManaged(true);
            });
            container.getChildren().addAll(more, less);
        }
    }

    static BigDecimal valorPatrimonial(List<Producto> prods) {
        return prods.stream()
            .map(p -> {
                BigDecimal precio = p.getPrecioVenta() != null ? p.getPrecioVenta() : BigDecimal.ZERO;
                return precio.multiply(BigDecimal.valueOf(p.getStockActual()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean matchesFilter(List<Producto> prods, String filter) {
        return prods.stream().anyMatch(p ->
            p.getNombre().toLowerCase().contains(filter)
            || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(filter)));
    }

    private boolean anyAccessible(Set<String> accessible, List<String> areas) {
        return accessible == null || areas.stream().anyMatch(accessible::contains);
    }
}
