package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Resguardo;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.ReporteResguardoVehicularService;
import com.sibim.service.ReporteService;
import com.sibim.service.ResguardoService;
import com.sibim.service.TitularesArea;
import com.sibim.service.TitularesArea.Candidato;
import com.sibim.util.AppColors;
import com.sibim.util.BarraAvance;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.MensajeError;
import com.sibim.util.NotificacionUtil;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/**
 * Everything the official formats print and has to be typed (9-nov-2026), in one
 * window: the RFC and domicilio of the municipio, who signs each format, and the
 * titular of every área. The titulares were a column of a 280 px table edited
 * with a double click, and no área had one; here each área has its own field,
 * grouped by dependencia, with the people whose cargo in its resguardos says they
 * head it as suggestions, one click to use.
 */
public final class DatosFormatosDialog {

    /** The three parts, each a card on top. */
    public enum Seccion { MUNICIPIO, FIRMAS, TITULARES }

    /** One field: its configuracion key, what it is, where it prints, and whether a
     *  format leaves a blank without it (the others print «Nombre y firma»). */
    record Dato(String clave, String titulo, String quien, String saleEn, boolean necesario, String areaSugerencias,
                String ejemplo, String palabra) {
        Dato(String clave, String titulo, String quien, String saleEn, boolean necesario, String areaSugerencias, String palabra) {
            this(clave, titulo, quien, saleEn, necesario, areaSugerencias, null, palabra);
        }
    }

    static final List<Dato> MUNICIPIO = List.of(
        new Dato(ReporteResguardoVehicularService.CLAVE_RFC, "RFC del municipio", null,
            "Hoja de resguardo de vehículos y Anexo V.4", true, null, "Ej. MIX-850101-5Q0", null),
        new Dato(ReporteResguardoVehicularService.CLAVE_DOMICILIO, "Domicilio", null,
            "Hoja de resguardo de vehículos y Anexo V.4", true, null, "Ej. Plaza Juárez S/N, Col. Centro, C.P. 42300", null));

    static final List<Dato> FIRMAS = List.of(
        new Dato(ReporteResguardoVehicularService.CLAVE_ELABORA, "Elaboró", "Director de Recursos Materiales y Patrimonio",
            "Resguardos, reportes y formatos", true, "Recursos Materiales y Patrimonio", "DIRECTOR"),
        new Dato(ReporteResguardoVehicularService.CLAVE_VOBO, "Vo. Bo.", "Secretario General Municipal",
            "Resguardos, reportes y formatos", true, "Secretaría General Municipal", "SECRETARI"),
        new Dato(ReporteService.FIRMA_SINDICO, "Supervisó", "Síndico hacendario", "Resguardos", false, null, "SINDIC"),
        new Dato(ReporteService.FIRMA_JEFE_INVENTARIOS, "Revisó", "Jefe de inventarios", "Bajas", false,
            "Recursos Materiales y Patrimonio", "INVENTARIO"),
        new Dato(ReporteService.FIRMA_TESORERO, "Vo. Bo. de la depreciación", "Tesorero municipal", "Depreciación", false,
            "Tesorería Municipal", "TESORER"),
        new Dato(ReporteService.FIRMA_CONTADOR, "Contador o contralor", "Contador / Contralor municipal", "Depreciación", false,
            "Contraloría Municipal", "CONT"),
        new Dato(ReporteService.FIRMA_PRESIDENTE, "Autorizó", "Presidente municipal", "Acta de baja", false, Areas.PRESIDENCIA, "PRESIDENT"));

    private final Map<String, String> originales = new HashMap<>();
    private final Map<String, TextField> campos = new LinkedHashMap<>();
    private final Map<String, Label> marcas = new HashMap<>();
    private final Map<Seccion, ToggleButton> tarjetas = new EnumMap<>(Seccion.class);
    private final Map<String, List<Candidato>> candidatos;
    private final List<String> areas;
    private final boolean editable;
    private final Runnable alGuardar;
    private Button guardar;

    private DatosFormatosDialog(Map<String, String> config, Map<String, List<Candidato>> candidatos,
                                List<String> areas, boolean editable, Runnable alGuardar) {
        this.candidatos = candidatos;
        this.areas = areas;
        this.editable = editable;
        this.alGuardar = alGuardar;
        for (Dato d : MUNICIPIO) originales.put(d.clave(), config.getOrDefault(d.clave(), "").trim());
        for (Dato d : FIRMAS) originales.put(d.clave(), config.getOrDefault(d.clave(), "").trim());
        for (String a : areas) originales.put(TitularesArea.clave(a), config.getOrDefault(TitularesArea.clave(a), "").trim());
    }

    /** Reads the configuration and the resguardos in force, then opens on {@code seccion}
     *  (null: the first part with something missing). Patrimonio only. */
    public static void abrir(Scene escena, Seccion seccion, Runnable alGuardar) {
        DialogUtil.runAsync(
            () -> {
                Map<String, String> cfg = new ConfiguracionRepository().findAll();
                List<Resguardo> rs;
                try { rs = new ResguardoService().getAll(); } catch (Exception e) { rs = List.of(); }
                return new Object[]{cfg, rs};
            },
            datos -> {
                @SuppressWarnings("unchecked") Map<String, String> cfg = (Map<String, String>) datos[0];
                @SuppressWarnings("unchecked") List<Resguardo> rs = (List<Resguardo>) datos[1];
                mostrar(cfg, rs, seccion, alGuardar);
            },
            e -> NotificacionUtil.error(escena, "No se pudieron leer los datos de los formatos: " + MensajeError.de(e)));
    }

    /** Opens with data already read (also what the screenshots use). */
    public static void mostrar(Map<String, String> config, List<Resguardo> resguardos, Seccion seccion, Runnable alGuardar) {
        boolean editable = !DatabaseConfig.isDemoMode() && !DatabaseConfig.isOfflineMode();
        new DatosFormatosDialog(config, TitularesArea.candidatos(resguardos), new ArrayList<>(Areas.getAllAreaNames()),
            editable, alGuardar).show(seccion);
    }

    private void show(Seccion inicial) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.setTitle("Datos de los formatos");
        ButtonType tGuardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(tGuardar, ButtonType.CLOSE);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dlg.getDialogPane().setPrefSize(Math.min(1080, pantalla.getWidth() * 0.94), Math.min(780, pantalla.getHeight() * 0.9));
        dlg.setResizable(true);
        HBox header = DialogUtil.gradientHeader("mdi2f-file-document-edit-outline", "Datos de los formatos",
            "Lo que imprimen los resguardos, el Anexo V.4, las bajas y la depreciación: escríbelo una vez y sale en todos",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        StackPane contenido = new StackPane();
        Map<Seccion, Node> paginas = new EnumMap<>(Seccion.class);
        paginas.put(Seccion.MUNICIPIO, pagina(seccionDatos(MUNICIPIO,
            "Salen en el encabezado de la hoja de resguardo de vehículos y del Anexo V.4.")));
        paginas.put(Seccion.FIRMAS, pagina(seccionDatos(FIRMAS,
            "En cada firma va el nombre y abajo su cargo. Las que quedan vacías salen como «Nombre y firma», para escribirlo a mano; "
                + "Elaboró y Vo. Bo. van en casi todos los formatos.")));
        paginas.put(Seccion.TITULARES, seccionTitulares());

        ToggleGroup grupo = new ToggleGroup();
        HBox fila = new HBox(10,
            tarjeta(Seccion.MUNICIPIO, grupo, "mdi2o-office-building-outline", "Del municipio", "RFC y domicilio"),
            tarjeta(Seccion.FIRMAS, grupo, "mdi2d-draw", "Quién firma", "Elaboró, Vo. Bo. y las demás"),
            tarjeta(Seccion.TITULARES, grupo, "mdi2a-account-check-outline", "Titulares de las áreas", "El responsable de cada área"));
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) { if (antes != null) antes.setSelected(true); return; }
            contenido.getChildren().setAll(paginas.get((Seccion) ahora.getUserData()));
        });
        Seccion primera = inicial != null ? inicial : primeraConFaltantes();
        grupo.selectToggle(tarjetas.get(primera));
        VBox.setVgrow(contenido, Priority.ALWAYS);

        Label sinConexion = new Label("Sin conexión a la base: aquí solo se pueden ver.");
        sinConexion.getStyleClass().add("muted-sm");
        VBox cuerpo = new VBox(14, fila);
        if (!editable) cuerpo.getChildren().add(sinConexion);
        cuerpo.getChildren().add(contenido);
        cuerpo.setPadding(new Insets(0, 22, 12, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dlg.getDialogPane().setContent(new VBox(12, header, cuerpo));

        guardar = (Button) dlg.getDialogPane().lookupButton(tGuardar);
        guardar.getStyleClass().add("btn-primary");
        // Saving keeps the window open: 57 titulares are typed in more than one sitting.
        guardar.addEventFilter(ActionEvent.ACTION, e -> { e.consume(); guardarCambios(guardar.getScene()); });
        Button cerrar = (Button) dlg.getDialogPane().lookupButton(ButtonType.CLOSE);
        cerrar.setText("Cerrar");
        cerrar.addEventFilter(ActionEvent.ACTION, e -> { if (!puedeCerrar()) e.consume(); });
        dlg.setOnCloseRequest(e -> { if (!puedeCerrar()) e.consume(); });
        actualizar();
        dlg.showAndWait();
    }

    /** With changes not saved, closing asks first. */
    private boolean puedeCerrar() {
        int n = cambios().size();
        return n == 0 || ConfirmacionUtil.confirmarPeligro("Cambios sin guardar",
            (n == 1 ? "Hay 1 dato" : "Hay " + n + " datos") + " sin guardar. Si cierras, se pierden.", "Cerrar sin guardar");
    }

    private Seccion primeraConFaltantes() {
        if (faltan(MUNICIPIO.stream().map(Dato::clave).toList()) > 0) return Seccion.MUNICIPIO;
        if (FIRMAS.stream().anyMatch(d -> d.necesario() && vacio(d.clave()))) return Seccion.FIRMAS;
        return Seccion.TITULARES;
    }

    // ── The cards on top ───────────────────────────────────────────────────────

    private ToggleButton tarjeta(Seccion s, ToggleGroup grupo, String icono, String titulo, String detalle) {
        FontIcon i = new FontIcon(icono);
        i.getStyleClass().add("formatos-tarjeta-icono");
        Label t = new Label(titulo);
        t.getStyleClass().add("avance-tarjeta-titulo");
        HBox arriba = new HBox(7, i, t);
        arriba.setAlignment(Pos.CENTER_LEFT);
        Label cifra = new Label();
        cifra.getStyleClass().add("avance-tarjeta-cifra");
        Label de = new Label();
        de.getStyleClass().add("avance-tarjeta-unidad");
        HBox numeros = new HBox(5, cifra, de);
        numeros.setAlignment(Pos.BASELINE_LEFT);
        Label det = new Label(detalle);
        det.getStyleClass().add("avance-tarjeta-detalle");
        BarraAvance barra = new BarraAvance(0);
        VBox.setMargin(barra, new Insets(4, 0, 0, 0));
        VBox caja = new VBox(4, arriba, numeros, det, barra);
        caja.setMaxWidth(Double.MAX_VALUE);
        ToggleButton b = new ToggleButton();
        b.setGraphic(caja);
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        b.getStyleClass().addAll("avance-tarjeta", "formatos-tarjeta");
        b.setUserData(s);
        b.setToggleGroup(grupo);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMinWidth(180);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.getProperties().put("cifra", cifra);
        b.getProperties().put("de", de);
        b.getProperties().put("barra", barra);
        b.getProperties().put("titulo", titulo);
        tarjetas.put(s, b);
        return b;
    }

    /** The counts of the cards, the ✓ of each field and the Guardar button. */
    private void actualizar() {
        contar(Seccion.MUNICIPIO, MUNICIPIO.stream().map(Dato::clave).toList());
        contar(Seccion.FIRMAS, FIRMAS.stream().map(Dato::clave).toList());
        contar(Seccion.TITULARES, areas.stream().map(TitularesArea::clave).toList());
        marcas.forEach((clave, marca) -> {
            boolean lleno = !vacio(clave);
            marca.setVisible(lleno);
        });
        int n = cambios().size();
        if (guardar != null) {
            guardar.setText(n == 0 ? "Guardar" : n == 1 ? "Guardar 1 cambio" : "Guardar " + n + " cambios");
            guardar.setDisable(n == 0 || !editable);
        }
    }

    private void contar(Seccion s, List<String> claves) {
        ToggleButton b = tarjetas.get(s);
        if (b == null) return;
        int total = claves.size(), llenos = total - faltan(claves);
        ((Label) b.getProperties().get("cifra")).setText(String.valueOf(llenos));
        ((Label) b.getProperties().get("de")).setText("de " + total);
        ((BarraAvance) b.getProperties().get("barra")).setAvance(total == 0 ? 0 : (double) llenos / total);
        b.getStyleClass().removeAll("formatos-completa");
        if (llenos == total) b.getStyleClass().add("formatos-completa");
        b.setAccessibleText(b.getProperties().get("titulo") + ": " + llenos + " de " + total);
    }

    private int faltan(List<String> claves) {
        return (int) claves.stream().filter(this::vacio).count();
    }

    private boolean vacio(String clave) {
        TextField t = campos.get(clave);
        String v = t != null ? t.getText() : originales.get(clave);
        return v == null || v.isBlank();
    }

    /** key → new value, of what differs from what was read. */
    Map<String, String> cambios() {
        Map<String, String> out = new LinkedHashMap<>();
        campos.forEach((clave, t) -> {
            String v = t.getText() == null ? "" : t.getText().trim().replaceAll("\\s+", " ");
            if (!v.equals(originales.getOrDefault(clave, ""))) out.put(clave, v);
        });
        return out;
    }

    // ── Del municipio / Quién firma ───────────────────────────────────────────

    private Node seccionDatos(List<Dato> datos, String ayuda) {
        Label a = new Label(ayuda);
        a.getStyleClass().add("muted-sm");
        a.setWrapText(true);
        a.setMinHeight(Region.USE_PREF_SIZE);
        VBox filas = new VBox(0);
        filas.getStyleClass().add("formatos-lista");
        for (Dato d : datos) filas.getChildren().add(filaDato(d));
        return new VBox(10, a, filas);
    }

    private Node filaDato(Dato d) {
        Label titulo = new Label(d.titulo());
        titulo.getStyleClass().add("formatos-dato-titulo");
        HBox tituloFila = new HBox(6, titulo);
        tituloFila.setAlignment(Pos.CENTER_LEFT);
        if (d.necesario()) {
            Label n = new Label("Necesario");
            n.getStyleClass().add("formatos-necesario");
            tituloFila.getChildren().add(n);
        }
        VBox izquierda = new VBox(2, tituloFila);
        if (d.quien() != null) {
            Label q = new Label(d.quien());
            q.getStyleClass().add("formatos-dato-quien");
            izquierda.getChildren().add(q);
        }
        Label sale = new Label("Sale en: " + d.saleEn());
        sale.getStyleClass().add("muted-sm");
        izquierda.getChildren().add(sale);
        izquierda.setMinWidth(260);
        izquierda.setPrefWidth(300);
        izquierda.setMaxWidth(300);

        TextField campo = campo(d.clave(), d.ejemplo() != null ? d.ejemplo() : "Nombre de quien firma", d.titulo());
        VBox derecha = new VBox(6, campoConMarca(d.clave(), campo));
        if (d.areaSugerencias() != null || d.palabra() != null) {
            // From its área; a signature without one (the síndico) looks for its cargo in every área.
            List<Candidato> deEsa = new ArrayList<>(d.areaSugerencias() != null
                ? candidatos.getOrDefault(d.areaSugerencias(), List.of())
                : candidatos.values().stream().flatMap(List::stream)
                    .filter(c -> plano(c.cargo()).contains(plano(d.palabra()))).distinct().toList());
            // The one whose cargo is this signature's comes first (Jefe de inventarios, not the director).
            if (d.palabra() != null)
                deEsa.sort(Comparator.comparing((Candidato c) -> !plano(c.cargo()).contains(plano(d.palabra()))));
            Node s = sugerencias(deEsa, campo, 2);
            if (s != null) derecha.getChildren().add(s);
        }
        HBox.setHgrow(derecha, Priority.ALWAYS);
        HBox h = new HBox(18, izquierda, derecha);
        h.setAlignment(Pos.TOP_LEFT);
        h.getStyleClass().add("formatos-fila");
        return h;
    }

    // ── Titulares de las áreas ────────────────────────────────────────────────

    private Node seccionTitulares() {
        Label ayuda = new Label("Quien responde por los resguardos de cada área: sale como «Responsable» en el Anexo V.4 y en el "
            + "resguardo del área. Las sugerencias son quienes firman resguardos ahí con cargo de director, titular o coordinador; "
            + "tú decides.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        TextField buscar = new TextField();
        buscar.setPromptText("Buscar área…");
        buscar.getStyleClass().add("search-field");
        buscar.setPrefWidth(260);
        buscar.setAccessibleText("Buscar área");
        long sinTitular = areas.stream().filter(a -> vacio(TitularesArea.clave(a))).count();
        ToggleButton soloFaltan = new ToggleButton("Solo las que faltan");
        soloFaltan.getStyleClass().add("filter-chip");
        soloFaltan.setSelected(sinTitular > 0 && sinTitular < areas.size());
        Tooltip.install(soloFaltan, new Tooltip("Muestra solo las áreas sin titular (se actualiza al buscar o al volver a pulsarlo)"));
        HBox barra = new HBox(10, buscar, soloFaltan);
        barra.setAlignment(Pos.CENTER_LEFT);

        Map<String, Node> filas = new LinkedHashMap<>();
        for (String a : areas) filas.put(a, filaTitular(a));
        VBox lista = new VBox();
        lista.getStyleClass().add("formatos-lista");
        ScrollPane scroll = new ScrollPane(lista);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("avance-lista-scroll");
        VBox marco = new VBox(scroll);
        marco.getStyleClass().add("avance-marco");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(marco, Priority.ALWAYS);

        Runnable pintar = () -> {
            String t = plano(buscar.getText());
            List<String> visibles = areas.stream()
                .filter(a -> !soloFaltan.isSelected() || vacio(TitularesArea.clave(a)))
                .filter(a -> t.isEmpty() || plano(a).contains(t) || plano(Areas.dependenciaDe(a)).contains(t))
                .toList();
            lista.getChildren().clear();
            Areas.porDependencia(visibles, a -> a).forEach((g, deEse) -> {
                Label n = new Label(g);
                n.getStyleClass().add("avance-grupo-nombre");
                Label det = new Label(deEse.size() == 1 ? "1 área" : deEse.size() + " áreas");
                det.getStyleClass().add("avance-grupo-detalle");
                HBox cab = new HBox(8, n, det);
                cab.setAlignment(Pos.CENTER_LEFT);
                cab.getStyleClass().add("avance-grupo");
                lista.getChildren().add(cab);
                deEse.forEach(a -> lista.getChildren().add(filas.get(a)));
            });
            if (visibles.isEmpty()) {
                Label vacio = new Label(soloFaltan.isSelected() && t.isEmpty()
                    ? "Todas las áreas tienen titular" : "Ningún área coincide con la búsqueda");
                vacio.getStyleClass().add("muted");
                StackPane c = new StackPane(vacio);
                c.setPadding(new Insets(36));
                lista.getChildren().add(c);
            }
            scroll.setVvalue(0);
        };
        buscar.textProperty().addListener((o, a, b) -> pintar.run());
        soloFaltan.selectedProperty().addListener((o, a, b) -> pintar.run());
        pintar.run();

        VBox v = new VBox(10, ayuda, barra, marco);
        VBox.setVgrow(v, Priority.ALWAYS);
        return v;
    }

    private Node filaTitular(String area) {
        Label nombre = new Label(area);
        nombre.getStyleClass().add("formatos-dato-titulo");
        nombre.setWrapText(true);
        nombre.setMinWidth(220);
        nombre.setPrefWidth(280);
        nombre.setMaxWidth(280);
        String clave = TitularesArea.clave(area);
        TextField campo = campo(clave, "Nombre del titular", "Titular de " + area);
        VBox derecha = new VBox(6, campoConMarca(clave, campo));
        Node s = sugerencias(candidatos.getOrDefault(area, List.of()), campo, 3);
        if (s != null) derecha.getChildren().add(s);
        HBox.setHgrow(derecha, Priority.ALWAYS);
        HBox h = new HBox(18, nombre, derecha);
        h.setAlignment(Pos.TOP_LEFT);
        h.getStyleClass().add("formatos-fila");
        return h;
    }

    // ── Fields and suggestions ────────────────────────────────────────────────

    private TextField campo(String clave, String prompt, String accesible) {
        TextField t = new TextField(originales.getOrDefault(clave, ""));
        t.setPromptText(prompt);
        t.setAccessibleText(accesible);
        t.setEditable(editable);
        t.setMaxWidth(560);
        t.textProperty().addListener((o, a, b) -> actualizar());
        campos.put(clave, t);
        return t;
    }

    private Node campoConMarca(String clave, TextField campo) {
        FontIcon ok = new FontIcon("mdi2c-check-circle");
        ok.getStyleClass().add("formatos-ok");
        Label marca = new Label(null, ok);
        marca.setVisible(!vacio(clave));
        Tooltip.install(marca, new Tooltip("Capturado"));
        marcas.put(clave, marca);
        HBox.setHgrow(campo, Priority.ALWAYS);
        HBox h = new HBox(8, campo, marca);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    /** "Sugerencias: [NOMBRE · Cargo] …": a click writes that name in the field. */
    private Node sugerencias(List<Candidato> lista, TextField campo, int max) {
        if (lista.isEmpty() || !editable) return null;
        Label t = new Label("Sugerencias:");
        t.getStyleClass().add("muted-sm");
        FlowPane f = new FlowPane(6, 6, t);
        f.setAlignment(Pos.CENTER_LEFT);
        for (Candidato c : lista.subList(0, Math.min(max, lista.size()))) {
            Button b = new Button(c.texto());
            b.getStyleClass().add("formatos-sugerencia");
            b.setTooltip(new Tooltip("Escribir «" + c.nombre() + "» en el campo"));
            b.setOnAction(e -> { campo.setText(c.nombre()); campo.requestFocus(); campo.end(); });
            f.getChildren().add(b);
        }
        return f;
    }

    private static Node pagina(Node contenido) {
        ScrollPane sp = new ScrollPane(contenido);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("avance-lista-scroll");
        return sp;
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    private void guardarCambios(Scene escena) {
        Map<String, String> cambios = cambios();
        if (cambios.isEmpty() || !editable) return;
        guardar.setDisable(true);
        DialogUtil.runAsync(
            () -> {
                ConfiguracionRepository config = new ConfiguracionRepository();
                int titulares = 0, otros = 0;
                for (var c : cambios.entrySet()) {
                    String area = areaDe(c.getKey());
                    if (area != null) { TitularesArea.guardar(config, area, c.getValue()); titulares++; }
                    else { config.set(c.getKey(), c.getValue()); otros++; }
                }
                new AuditLogRepository().log("configuracion", "formatos", "Datos de los formatos", "actualizar",
                    "Datos de los formatos: " + (otros > 0 ? otros + (otros == 1 ? " dato o firma" : " datos o firmas") : "")
                        + (otros > 0 && titulares > 0 ? " y " : "")
                        + (titulares > 0 ? titulares + (titulares == 1 ? " titular de área" : " titulares de área") : ""));
                return null;
            },
            v -> {
                cambios.forEach((clave, valor) -> originales.put(clave,
                    areaDe(clave) != null ? com.sibim.util.NombrePersona.sinTitulos(valor) : valor));
                // A titular is kept without titles: the field shows what was saved.
                cambios.keySet().forEach(clave -> {
                    if (areaDe(clave) != null) campos.get(clave).setText(originales.get(clave));
                });
                actualizar();
                NotificacionUtil.exito(escena, cambios.size() == 1 ? "Se guardó 1 dato" : "Se guardaron " + cambios.size() + " datos");
                if (alGuardar != null) alGuardar.run();
            },
            e -> {
                actualizar();
                NotificacionUtil.error(escena, "No se pudo guardar: " + MensajeError.de(e));
            });
    }

    /** The área of a titular's key, null for any other key. */
    private String areaDe(String clave) {
        for (String a : areas) if (TitularesArea.clave(a).equals(clave)) return a;
        return null;
    }

    private static String plano(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
