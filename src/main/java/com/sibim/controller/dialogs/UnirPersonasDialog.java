package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.ResguardoService.Resguardante;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Bienes › Unir personas repetidas: the same words under two spellings ("ANA
 *  RUIZ SOTO" / "RUIZ SOTO ANA" / "LIC. ANA RUIZ SOTO"). Only someone
 *  who knows them can tell whether they are one person, so each case is asked:
 *  which name stays, or that they are two people, which is not asked again. */
public final class UnirPersonasDialog {

    private final CalidadDatosService service = new CalidadDatosService();
    private final List<Producto> bienes;
    private final ObservableList<List<Resguardante>> grupos = FXCollections.observableArrayList();
    private final ListView<List<Resguardante>> lista = new ListView<>(grupos);
    private final VBox tarjetas = new VBox(10);
    private final VBox vacio = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(tarjetas);
    private final Label pregunta = new Label();
    private final Label aviso = new Label();
    private final Label resumen = new Label();
    /** The name typed by hand, when neither spelling is the right one. */
    private final TextField otroNombre = new TextField();
    private final List<Runnable> pintarTarjetas = new ArrayList<>();
    private final HBox barraDeshacer = new HBox(10);
    private final Label textoDeshacer = new Label();
    private final Button deshacer = new Button("Deshacer");
    /** The last union, while this window is open: there is one way back, right after. */
    private CalidadDatosService.Union ultima;
    private List<Resguardante> ultimoGrupo;
    private int ultimaPosicion;
    private final Button unir = new Button("Son la misma persona: unir");
    private final Button distintas = new Button("Son personas distintas");
    private final ToggleGroup nombres = new ToggleGroup();
    private final Label cuantos = new Label();
    private final Runnable alCambiar;
    private Dialog<ButtonType> dialog;
    private int unidas = 0;

    private UnirPersonasDialog(List<Producto> bienes, List<List<Resguardante>> iniciales, Runnable alCambiar) {
        this.bienes = bienes;
        this.alCambiar = alCambiar;
        grupos.setAll(iniciales);
    }

    /** @param alCambiar run on closing when something was unified */
    public static void show(List<Producto> bienes, List<Resguardo> resguardos, Runnable alCambiar) {
        CalidadDatosService s = new CalidadDatosService();
        new UnirPersonasDialog(bienes, CalidadDatosService.personasRepetidas(resguardos, bienes, s.personasDistintas()), alCambiar).abrir();
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Unir personas repetidas");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1080, pantalla.getWidth() * 0.94), Math.min(680, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2a-account-multiple-check-outline", "Unir personas repetidas",
            "Mismas palabras en otro orden, con título o con una letra distinta: decide si son la misma persona",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        lista.getStyleClass().add("trf-areas");
        lista.setCellFactory(lv -> new CeldaGrupo(lv));
        lista.setPlaceholder(new Label("No queda ninguna por revisar"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, g) -> mostrar(g));
        VBox.setVgrow(lista, Priority.ALWAYS);
        cuantos.getStyleClass().add("table-count-label");
        VBox izquierda = new VBox(8, TransferenciaArrastreDialog.paso(1, "Elige a quién revisar"), lista, cuantos);
        izquierda.getStyleClass().add("trf-panel");
        izquierda.setPrefWidth(400);
        // The long question on the right would otherwise squeeze the names to "…".
        izquierda.setMinWidth(380);

        FontIcon icono = new FontIcon("mdi2a-account-check-outline");
        icono.getStyleClass().add("empty-icon-lg");
        Label tituloVacio = new Label("Elige una persona de la lista");
        tituloVacio.getStyleClass().add("empty-state-msg");
        vacio.getChildren().setAll(icono, tituloVacio);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        tarjetas.setPadding(new Insets(2, 4, 4, 2));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        aviso.getStyleClass().addAll("field-hint", "field-hint-warn");
        aviso.setWrapText(true);
        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);
        resumen.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(resumen, Priority.ALWAYS);

        unir.setGraphic(new FontIcon("mdi2a-account-multiple-check-outline"));
        unir.getStyleClass().add("btn-primary");
        unir.setMinWidth(Region.USE_PREF_SIZE);
        unir.setOnAction(e -> unirElegida());
        distintas.getStyleClass().add("btn-secondary");
        distintas.setMinWidth(Region.USE_PREF_SIZE);
        distintas.setTooltip(new Tooltip("Las deja como están y no vuelve a preguntar por ellas"));
        distintas.setOnAction(e -> marcarDistintas());
        HBox pie = new HBox(10, resumen, distintas, unir);
        pie.setAlignment(Pos.CENTER_LEFT);

        HBox paso2 = TransferenciaArrastreDialog.paso(2, "¿Son la misma persona?");
        paso2.setPadding(Insets.EMPTY);
        pregunta.getStyleClass().add("org-table-sub");
        pregunta.setWrapText(true);
        textoDeshacer.getStyleClass().add("rsg-tarjeta-titulo");
        textoDeshacer.setWrapText(true);
        textoDeshacer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textoDeshacer, Priority.ALWAYS);
        deshacer.setGraphic(new FontIcon("mdi2u-undo"));
        deshacer.getStyleClass().add("btn-secondary");
        deshacer.setMinWidth(Region.USE_PREF_SIZE);
        deshacer.setOnAction(e -> deshacerUltima());
        barraDeshacer.getChildren().setAll(textoDeshacer, deshacer);
        barraDeshacer.setAlignment(Pos.CENTER_LEFT);
        barraDeshacer.getStyleClass().add("cnt-aviso");
        ver(barraDeshacer, false);
        VBox derecha = new VBox(8, paso2, pregunta, aviso, vacio, scroll, barraDeshacer, new Separator(), pie);
        HBox.setHgrow(derecha, Priority.ALWAYS);

        HBox cuerpo = new HBox(16, izquierda, derecha);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        dialog.setOnHidden(e -> { if (unidas > 0 && alCambiar != null) alCambiar.run(); });
        actualizarCuenta();
        mostrar(null);
        Platform.runLater(() -> {
            if (!grupos.isEmpty()) lista.getSelectionModel().select(0);
            lista.requestFocus();
        });
        dialog.showAndWait();
    }

    private void actualizarCuenta() {
        cuantos.setText(grupos.isEmpty() ? "Nada por revisar"
            : FormatUtils.plural(grupos.size(), "persona por revisar", "personas por revisar"));
    }

    private void mostrar(List<Resguardante> grupo) {
        tarjetas.getChildren().clear();
        boolean hay = grupo != null;
        ver(vacio, !hay);
        ver(scroll, hay);
        unir.setDisable(!hay);
        distintas.setDisable(!hay);
        ver(aviso, false);
        ver(pregunta, hay);
        resumen.setText("");
        pintarTarjetas.clear();
        if (!hay) return;
        pregunta.setText("Estos " + grupo.size() + " nombres " + (CalidadDatosService.mismasPalabras(grupo)
                ? "tienen las mismas palabras" : "se parecen mucho, pero cambia alguna letra: revisa que no sean dos personas")
            + ". Si son la misma persona, marca cómo debe quedar "
            + "su nombre y pulsa «Son la misma persona: unir». Si no, pulsa «Son personas distintas» y se dejan como están.");
        String propuesto = CalidadDatosService.nombrePropuesto(grupo);
        nombres.getToggles().clear();
        for (Resguardante p : grupo) tarjetas.getChildren().add(tarjeta(p, p.nombre().equals(propuesto)));
        tarjetas.getChildren().add(tarjetaOtroNombre(propuesto));
        List<String> areas = grupo.stream().flatMap(p -> p.areas().stream()).distinct().toList();
        aviso.getStyleClass().removeAll("field-hint-warn", "field-hint-ok");
        if (areas.size() > 1) {
            aviso.getStyleClass().add("field-hint-warn");
            aviso.setText("Ojo: están en " + areas.size() + " áreas distintas (" + String.join(", ", areas)
                + "). Revisa que de verdad sean la misma persona.");
            ver(aviso, true);
        } else if (areas.size() == 1) {
            aviso.getStyleClass().add("field-hint-ok");
            aviso.setText("Las dos formas están en la misma área: " + areas.get(0) + ".");
            ver(aviso, true);
        }
        actualizarResumen(grupo);
    }

    /** "ARQ. LUIS MIGUEL SOTO RAMOS" and "SOTO RAMOS LUIS MIGUEL" may both be wrong: the
     *  right one can be neither, without the title and in its natural order. */
    private Region tarjetaOtroNombre(String propuesto) {
        RadioButton rb = new RadioButton("Ninguno de estos: escribirlo");
        rb.setToggleGroup(nombres);
        rb.getStyleClass().add("rsg-tarjeta-titulo");
        otroNombre.setId("otroNombre");
        otroNombre.setPromptText("Nombre como debe quedar");
        otroNombre.setText(propuesto);
        otroNombre.getStyleClass().add("form-input");
        otroNombre.setDisable(true);
        otroNombre.textProperty().addListener((o, a, b) -> actualizarResumen(lista.getSelectionModel().getSelectedItem()));
        VBox caja = new VBox(6, rb, otroNombre);
        caja.getStyleClass().add("rsg-tarjeta");
        caja.setOnMouseClicked(e -> { if (!rb.isSelected()) { rb.setSelected(true); otroNombre.requestFocus(); } });
        Runnable pintar = () -> {
            caja.getStyleClass().remove("rsg-tarjeta-sel");
            if (rb.isSelected()) caja.getStyleClass().add("rsg-tarjeta-sel");
            otroNombre.setDisable(!rb.isSelected());
        };
        pintar.run();
        pintarTarjetas.add(pintar);
        rb.selectedProperty().addListener((o, a, si) -> {
            pintarTarjetas.forEach(Runnable::run);
            actualizarResumen(lista.getSelectionModel().getSelectedItem());
        });
        return caja;
    }

    private Region tarjeta(Resguardante p, boolean elegida) {
        RadioButton rb = new RadioButton(p.nombre());
        rb.setToggleGroup(nombres);
        rb.setUserData(p);
        rb.getStyleClass().add("rsg-tarjeta-titulo");
        rb.setWrapText(true);
        rb.setSelected(elegida);
        // What it will become, in words, and why this one was proposed.
        Label destino = new Label();
        destino.setMinWidth(Region.USE_PREF_SIZE);
        Label sugerido = new Label("Sugerido: tiene más bienes");
        sugerido.getStyleClass().add("muted-sm");
        sugerido.setMinWidth(Region.USE_PREF_SIZE);
        ver(sugerido, elegida);
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox arriba = new HBox(8, rb, hueco, sugerido, destino);
        arriba.setAlignment(Pos.CENTER_LEFT);
        List<String> folios = p.resguardos().stream().filter(Resguardo::isActivo).map(Resguardo::getNumero).toList();
        String cargo = p.resguardos().stream().map(Resguardo::getResguardanteCargo)
            .filter(c -> c != null && !c.isBlank()).findFirst().orElse(null);
        Label datos = new Label(FormatUtils.plural(CalidadDatosService.bienesDe(p), "bien", "bienes")
            + (p.areas().isEmpty() ? "" : " · " + String.join(", ", p.areas()))
            + (cargo != null ? " · " + cargo : "")
            + (folios.isEmpty() ? " · sin resguardo vigente" : " · " + String.join(", ", folios)));
        datos.getStyleClass().add("org-table-sub");
        datos.setWrapText(true);
        FlowPane chips = new FlowPane(5, 5);
        List<String> codigos = new ArrayList<>();
        p.resguardos().stream().filter(Resguardo::isActivo).forEach(r -> { if (r.getItems() != null) r.getItems().forEach(i -> codigos.add(i.getProductoCodigo())); });
        p.sinResguardo().forEach(b -> codigos.add(b.getCodigo()));
        codigos.stream().filter(c -> c != null && !c.isBlank()).limit(8).forEach(c -> {
            Label chip = new Label(c);
            chip.getStyleClass().add("trf-codigo");
            chips.getChildren().add(chip);
        });
        if (codigos.size() > 8) {
            Label mas = new Label("+" + (codigos.size() - 8) + " más");
            mas.getStyleClass().add("org-table-sub");
            chips.getChildren().add(mas);
        }
        VBox caja = new VBox(4, arriba, datos, chips);
        caja.getStyleClass().add("rsg-tarjeta");
        caja.setOnMouseClicked(e -> rb.setSelected(true));
        Runnable pintar = () -> {
            caja.getStyleClass().remove("rsg-tarjeta-sel");
            if (rb.isSelected()) caja.getStyleClass().add("rsg-tarjeta-sel");
            destino.getStyleClass().setAll("cell-badge", rb.isSelected() ? "cell-badge-success" : "cell-badge-blue");
            destino.setText(rb.isSelected() ? "Este se queda" : "Se reemplaza");
        };
        pintar.run();
        pintarTarjetas.add(pintar);
        rb.selectedProperty().addListener((o, a, si) -> {
            pintarTarjetas.forEach(Runnable::run);
            actualizarResumen(lista.getSelectionModel().getSelectedItem());
        });
        return caja;
    }

    /** @return the name that stays; null when "escribirlo" was chosen and nothing is written */
    private String nombreElegido() {
        if (nombres.getSelectedToggle() == null) return null;
        if (nombres.getSelectedToggle().getUserData() instanceof Resguardante p) return p.nombre();
        String escrito = otroNombre.getText() == null ? "" : otroNombre.getText().trim().replaceAll("\\s+", " ");
        return escrito.isEmpty() ? null : escrito;
    }

    private void actualizarResumen(List<Resguardante> grupo) {
        if (grupo == null) return;
        String n = nombreElegido();
        int total = grupo.stream().mapToInt(CalidadDatosService::bienesDe).sum();
        long vigentes = grupo.stream().mapToLong(Resguardante::activos).sum();
        resumen.setText(n == null ? "Escribe cómo debe quedar el nombre"
            : "Si los unes: " + FormatUtils.plural(total, "bien", "bienes")
                + (vigentes > 0 ? " y " + FormatUtils.plural((int) vigentes, "resguardo vigente", "resguardos vigentes") : "")
                + " quedan a nombre de " + n);
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (n != null) resumen.getStyleClass().add("trf-seleccion-activa");
        unir.setDisable(n == null);
    }

    private void unirElegida() {
        List<Resguardante> grupo = lista.getSelectionModel().getSelectedItem();
        String nombre = nombreElegido();
        if (grupo == null || nombre == null) return;
        String otros = grupo.stream().map(Resguardante::nombre).filter(n -> !n.equals(nombre)).collect(Collectors.joining("\n"));
        if (!ConfirmacionUtil.confirmar("Unir personas",
                "Va a quedar todo a nombre de:\n" + nombre + "\n\nSe reemplaza:\n" + otros
                    + "\n\nCambia en sus bienes y en sus resguardos vigentes; los PDF nuevos saldrán con este nombre. "
                    + "Mientras esta ventana siga abierta puedes deshacerlo.")) return;
        unir.setDisable(true);
        distintas.setDisable(true);
        DialogUtil.runAsync(() -> service.unirPersonas(nombre, grupo, bienes),
            union -> {
                unidas++;
                ultima = union;
                ultimoGrupo = grupo;
                ultimaPosicion = grupos.indexOf(grupo);
                textoDeshacer.setText("Listo: quedó «" + nombre + "» (" + FormatUtils.plural(union.cuantosBienes(), "bien cambió", "bienes cambiaron") + ")");
                ver(barraDeshacer, true);
                quitar(grupo);
            },
            e -> {
                NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo unir: " + e.getMessage());
                unir.setDisable(false);
                distintas.setDisable(false);
            });
    }

    /** Puts the names back and the case back on the list, to answer it again. */
    private void deshacerUltima() {
        if (ultima == null) return;
        CalidadDatosService.Union union = ultima;
        deshacer.setDisable(true);
        DialogUtil.runAsync(() -> { service.deshacer(union); },
            () -> {
                ultima = null;
                ver(barraDeshacer, false);
                deshacer.setDisable(false);
                grupos.add(Math.min(ultimaPosicion, grupos.size()), ultimoGrupo);
                actualizarCuenta();
                lista.getSelectionModel().select(ultimoGrupo);
                NotificacionUtil.exito(dialog.getDialogPane().getScene(), "Se regresaron los nombres de antes");
            },
            e -> {
                deshacer.setDisable(false);
                NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo deshacer: " + e.getMessage());
            });
    }

    private void marcarDistintas() {
        List<Resguardante> grupo = lista.getSelectionModel().getSelectedItem();
        if (grupo == null) return;
        DialogUtil.runAsync(() -> { service.marcarDistintas(grupo); },
            () -> quitar(grupo),
            e -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo guardar: " + e.getMessage()));
    }

    /** The case is done: on to the next, the one that took its place. */
    private void quitar(List<Resguardante> grupo) {
        int i = grupos.indexOf(grupo);
        grupos.remove(grupo);
        actualizarCuenta();
        if (grupos.isEmpty()) { lista.getSelectionModel().clearSelection(); mostrar(null); return; }
        lista.getSelectionModel().select(Math.min(i, grupos.size() - 1));
    }

    private static void ver(javafx.scene.Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static final class CeldaGrupo extends ListCell<List<Resguardante>> {
        private final FontIcon icono = new FontIcon("mdi2a-account-multiple-outline");
        private final StackPane fondo = new StackPane(icono);
        private final Label nombre = new Label();
        private final Label otro = new Label();
        private final Label cuenta = new Label();
        private final HBox caja;

        CeldaGrupo(ListView<List<Resguardante>> lv) {
            icono.getStyleClass().add("trf-tarjeta-icono");
            fondo.getStyleClass().addAll("trf-tarjeta-icono-fondo", "trf-area-icono");
            fondo.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            nombre.getStyleClass().add("org-child-label");
            nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
            otro.getStyleClass().add("org-table-sub");
            otro.setTextOverrun(OverrunStyle.ELLIPSIS);
            VBox textos = new VBox(1, nombre, otro);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, fondo, textos, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(List<Resguardante> g, boolean empty) {
            super.updateItem(g, empty);
            setText(null);
            if (empty || g == null) { setGraphic(null); return; }
            nombre.setText(g.get(0).nombre());
            otro.setText(g.size() == 2 ? g.get(1).nombre() : g.size() - 1 + " formas más");
            cuenta.setText(FormatUtils.plural(g.stream().mapToInt(CalidadDatosService::bienesDe).sum(), "bien", "bienes"));
            setGraphic(caja);
        }
    }
}
