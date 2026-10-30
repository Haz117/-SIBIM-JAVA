package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.PorHacer;
import com.sibim.service.ProductoService;
import com.sibim.service.ResguardoService;
import com.sibim.session.NavigationContext;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.DialogUtil;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Inicio, for Patrimonio: what the inventory still needs, with its real count and one
 *  click to the screen that fixes it. Those screens live under "Más", and nothing said
 *  there was anything to do in them. The áreas have "Pendientes de tu área" instead. */
final class PorHacerPanel {

    private record Datos(List<Producto> bienes, List<Resguardo> resguardos, Set<String> distintas, Map<String, String> config) {}

    private final VBox seccion;

    PorHacerPanel(VBox seccion) {
        this.seccion = seccion;
        CalidadDatosService calidad = new CalidadDatosService();
        DialogUtil.runAsync(
            () -> new Datos(new ProductoService().getAll(), new ResguardoService().getAll(),
                calidad.personasDistintas(), new ConfiguracionRepository().findAll()),
            d -> pintar(CalidadDatosService.porHacer(d.bienes(), d.resguardos(), d.distintas(), d.config())),
            e -> { /* It is a shortcut: without it, Inicio is what it was. */ });
    }

    private void pintar(List<PorHacer> lista) {
        seccion.getChildren().clear();
        boolean hay = !lista.isEmpty();
        seccion.setVisible(hay);
        seccion.setManaged(hay);
        if (!hay) return;

        Region barra = new Region();
        barra.getStyleClass().add("dash-section-bar");
        Label titulo = new Label("POR HACER");
        titulo.getStyleClass().add("dash-section-label");
        Label pista = new Label("Lo que le falta al inventario: da clic para resolverlo");
        pista.getStyleClass().add("muted-sm");
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Button plegar = new Button();
        plegar.getStyleClass().add("btn-ghost");
        plegar.setGraphic(new FontIcon("mdi2c-chevron-up"));
        HBox encabezado = new HBox(10, barra, titulo, pista, hueco, plegar);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        FlowPane tarjetas = new FlowPane(10, 10);
        for (PorHacer p : lista) tarjetas.getChildren().add(tarjeta(p));
        DialogUtil.makeCollapsible("inicio.porhacer.colapsado", plegar, tarjetas, "Mostrar", "Ocultar");
        seccion.getChildren().setAll(encabezado, tarjetas);
    }

    private static VBox tarjeta(PorHacer p) {
        Label numero = new Label(String.format("%,d", p.cantidad()));
        numero.getStyleClass().add("por-hacer-numero");
        Label que = new Label(p.titulo());
        que.getStyleClass().add("por-hacer-titulo");
        que.setWrapText(true);
        Label ir = new Label(p.accion() + " →");
        ir.getStyleClass().add("por-hacer-accion");
        VBox t = new VBox(2, numero, que, ir);
        t.getStyleClass().addAll("quick-action-card", "por-hacer-tarjeta");
        t.setOnMouseClicked(e -> abrir(p));
        AccessibilityUtils.asButton(t, p.cantidad() + " " + p.titulo() + ": " + p.accion());
        return t;
    }

    private static void abrir(PorHacer p) {
        if (p.abrir() != null) NavigationContext.setPendingAbrir(p.abrir());
        MainController mc = MainController.getInstance();
        if (mc != null) mc.navigateToView(p.vista());
    }
}
