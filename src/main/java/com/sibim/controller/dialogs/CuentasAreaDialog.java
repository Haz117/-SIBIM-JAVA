package com.sibim.controller.dialogs;

import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.enums.Rol;
import com.sibim.service.AvanceAreasService;
import com.sibim.service.CuentasAreaService;
import com.sibim.service.CuentasAreaService.Plan;
import com.sibim.service.ReporteCuentasService;
import com.sibim.util.AppColors;
import com.sibim.util.BarraAvance;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.MensajeError;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.util.*;

/**
 * «Cuentas de las áreas» (9-nov-2026): every área with the account it has or would
 * get — its user name, what it can see, and whether it has entered — before
 * creating any. "Cuentas por área" created the 57 at once from a confirmation;
 * now they can be created a few at a time (a check box each), and an área that
 * lost its paper gets a new password with its own sheet.
 */
public final class CuentasAreaDialog {

    /** What the cards on top filter by. */
    enum Filtro {
        TODAS("Todas las áreas", "todas"), SIN_CUENTA("Sin cuenta", "entrar"),
        SIN_ENTRAR("Tienen cuenta, no han entrado", "revisar"), ENTRARON("Ya entraron", "terminaron");

        final String texto, clave;
        Filtro(String texto, String clave) { this.texto = texto; this.clave = clave; }
    }

    private final CuentasAreaService service = new CuentasAreaService();
    private final Set<String> marcadas = new LinkedHashSet<>();
    private final Runnable alCambiar;
    private List<Plan> planes = List.of();
    private Map<String, LocalDateTime> accesos = Map.of();
    private final VBox lista = new VBox();
    private final ScrollPane scroll = new ScrollPane(lista);
    private final TextField buscar = new TextField();
    private final ToggleGroup grupo = new ToggleGroup();
    private final Map<Filtro, ToggleButton> tarjetas = new EnumMap<>(Filtro.class);
    private final Label cuantas = new Label();
    private final Button crear = new Button("Crear las cuentas");
    private final Button marcarFaltantes = new Button("Marcar todas las que faltan");
    private final boolean editable = !DatabaseConfig.isOfflineMode();
    private Dialog<ButtonType> dialog;
    private boolean cambio;

    private CuentasAreaDialog(Runnable alCambiar) { this.alCambiar = alCambiar; }

    /** Reads the accounts and the accesses, then opens.
     *  @param marcar áreas to start ticked (those without an account), or null to tick
     *                every área that has none
     *  @param alCambiar run on closing when an account was created or changed */
    public static void abrir(Scene escena, Collection<String> marcar, Runnable alCambiar) {
        CuentasAreaDialog d = new CuentasAreaDialog(alCambiar);
        d.cargar(escena, () -> {
            for (Plan p : d.planes)
                if (!p.tieneCuenta() && p.problema() == null && (marcar == null || marcar.contains(p.area())))
                    d.marcadas.add(p.area());
            d.mostrar();
        });
    }

    private void cargar(Scene escena, Runnable luego) {
        DialogUtil.runAsync(
            () -> {
                List<Plan> p = service.planear();
                Map<String, LocalDateTime> a;
                try { a = new AvanceAreasService().accesosPorArea(); } catch (Exception e) { a = Map.of(); }
                return Map.entry(p, a);
            },
            r -> { planes = r.getKey(); accesos = r.getValue(); luego.run(); },
            e -> NotificacionUtil.error(escena, "No se pudieron leer las cuentas: " + MensajeError.de(e)));
    }

    private void mostrar() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Cuentas de las áreas");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Cerrar");
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1120, pantalla.getWidth() * 0.94), Math.min(780, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        HBox header = DialogUtil.gradientHeader("mdi2a-account-multiple-outline", "Cuentas de las áreas",
            "Cada área entra con su propia cuenta, la misma en todas sus computadoras; marca cuáles crear",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        HBox fila = new HBox(10);
        for (Filtro f : Filtro.values()) fila.getChildren().add(tarjeta(f));
        grupo.selectToggle(tarjetas.get(Filtro.TODAS));
        grupo.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null && antes != null) { antes.setSelected(true); return; }
            pintar();
        });

        buscar.setPromptText("Buscar área o usuario…");
        buscar.getStyleClass().add("search-field");
        buscar.setPrefWidth(280);
        buscar.setAccessibleText("Buscar área o usuario");
        buscar.textProperty().addListener((o, a, b) -> pintar());
        marcarFaltantes.getStyleClass().addAll("btn-secondary", "btn-sm");
        marcarFaltantes.setGraphic(new FontIcon("mdi2c-checkbox-multiple-marked-outline"));
        marcarFaltantes.setOnAction(e -> {
            planes.stream().filter(p -> !p.tieneCuenta() && p.problema() == null).forEach(p -> marcadas.add(p.area()));
            pintar();
        });
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox barra = new HBox(10, buscar, hueco, marcarFaltantes);
        barra.setAlignment(Pos.CENTER_LEFT);

        lista.getStyleClass().add("avance-lista");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("avance-lista-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox marco = new VBox(scroll);
        marco.getStyleClass().add("avance-marco");
        VBox.setVgrow(marco, Priority.ALWAYS);

        crear.getStyleClass().add("btn-primary");
        FontIcon mas = new FontIcon("mdi2a-account-multiple-plus-outline");
        mas.getStyleClass().add("avance-aviso-boton-icono");
        crear.setGraphic(mas);
        crear.setMinWidth(Region.USE_PREF_SIZE);
        crear.setOnAction(e -> crearMarcadas());
        cuantas.getStyleClass().add("muted-sm");
        cuantas.setMinWidth(Region.USE_PREF_SIZE);   // «Ninguna marcada» was cut to «Ningu…» by the note beside it
        Label nota = new Label("Al crearlas sale un PDF con una hoja para entregar a cada área (su usuario, su contraseña "
            + "y qué hacer el primer día) y una de control para ti: guárdalo, las contraseñas no se vuelven a ver.");
        nota.getStyleClass().add("muted-sm");
        nota.setWrapText(true);
        HBox.setHgrow(nota, Priority.ALWAYS);
        nota.setMaxWidth(Double.MAX_VALUE);
        HBox abajo = new HBox(12, cuantas, nota, crear);
        abajo.setAlignment(Pos.CENTER_LEFT);

        VBox cuerpo = new VBox(12, fila, barra, marco, abajo);
        if (!editable) {
            Label sin = new Label("Sin conexión a la base: aquí solo se pueden ver.");
            sin.getStyleClass().add("muted-sm");
            cuerpo.getChildren().add(1, sin);
        }
        cuerpo.setPadding(new Insets(0, 22, 12, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dialog.setOnHidden(e -> { if (cambio && alCambiar != null) alCambiar.run(); });
        pintar();
        dialog.showAndWait();
    }

    private ToggleButton tarjeta(Filtro f) {
        Label t = new Label(f.texto);
        t.getStyleClass().add("avance-tarjeta-titulo");
        Label cifra = new Label();
        cifra.getStyleClass().add("avance-tarjeta-cifra");
        Label unidad = new Label("áreas");
        unidad.getStyleClass().add("avance-tarjeta-unidad");
        HBox numeros = new HBox(5, cifra, unidad);
        numeros.setAlignment(Pos.BASELINE_LEFT);
        BarraAvance barra = new BarraAvance(0);
        VBox caja = new VBox(4, t, numeros, barra);
        VBox.setMargin(barra, new Insets(4, 0, 0, 0));
        // «Todas» is the whole: a full bar says nothing.
        barra.setVisible(f != Filtro.TODAS);
        barra.setManaged(f != Filtro.TODAS);
        caja.setMaxWidth(Double.MAX_VALUE);
        ToggleButton b = new ToggleButton();
        b.setGraphic(caja);
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        b.getStyleClass().addAll("avance-tarjeta", "avance-" + f.clave);
        b.setUserData(f);
        b.setToggleGroup(grupo);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setMinWidth(150);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.getProperties().put("cifra", cifra);
        b.getProperties().put("unidad", unidad);
        b.getProperties().put("barra", barra);
        tarjetas.put(f, b);
        return b;
    }

    private Filtro de(Plan p) {
        if (!p.tieneCuenta()) return Filtro.SIN_CUENTA;
        return accesos.containsKey(p.area()) ? Filtro.ENTRARON : Filtro.SIN_ENTRAR;
    }

    private void pintar() {
        int total = planes.size();
        for (Filtro f : Filtro.values()) {
            ToggleButton b = tarjetas.get(f);
            long n = f == Filtro.TODAS ? total : planes.stream().filter(p -> de(p) == f).count();
            ((Label) b.getProperties().get("cifra")).setText(String.valueOf(n));
            ((Label) b.getProperties().get("unidad")).setText(n == 1 ? "área" : "áreas");
            ((BarraAvance) b.getProperties().get("barra")).setAvance(total == 0 ? 0 : (double) n / total);
            b.setAccessibleText(f.texto + ": " + n);
        }
        Filtro f = grupo.getSelectedToggle() == null ? Filtro.TODAS : (Filtro) grupo.getSelectedToggle().getUserData();
        String t = plano(buscar.getText());
        List<Plan> visibles = planes.stream()
            .filter(p -> f == Filtro.TODAS || de(p) == f)
            .filter(p -> t.isEmpty() || plano(p.area()).contains(t) || plano(p.usuario()).contains(t)
                || plano(Areas.dependenciaDe(p.area())).contains(t))
            .toList();
        lista.getChildren().clear();
        Areas.porDependencia(visibles, Plan::area).forEach((g, deEse) -> {
            Label n = new Label(g);
            n.getStyleClass().add("avance-grupo-nombre");
            long con = deEse.stream().filter(Plan::tieneCuenta).count();
            Label det = new Label(FormatUtils.plural(deEse.size(), "área", "áreas") + " · " + con + " con cuenta");
            det.getStyleClass().add("avance-grupo-detalle");
            HBox cab = new HBox(8, n, det);
            cab.setAlignment(Pos.CENTER_LEFT);
            cab.getStyleClass().add("avance-grupo");
            lista.getChildren().add(cab);
            deEse.forEach(p -> lista.getChildren().add(renglon(p)));
        });
        if (visibles.isEmpty()) {
            Label v = new Label("Ningún área en esta lista");
            v.getStyleClass().add("muted");
            StackPane c = new StackPane(v);
            c.setPadding(new Insets(36));
            lista.getChildren().add(c);
        }
        long faltan = planes.stream().filter(p -> !p.tieneCuenta() && p.problema() == null).count();
        marcarFaltantes.setDisable(faltan == 0 || marcadas.size() == faltan || !editable);
        cuantas.setText(marcadas.isEmpty() ? "Ninguna marcada" : FormatUtils.plural(marcadas.size(), "marcada", "marcadas"));
        crear.setText(marcadas.isEmpty() ? "Crear las cuentas"
            : marcadas.size() == 1 ? "Crear 1 cuenta" : "Crear " + marcadas.size() + " cuentas");
        crear.setDisable(marcadas.isEmpty() || !editable);
    }

    /** One área: a check box when it can get an account, its name and user name, what
     *  it can see, how it is going, and «Contraseña nueva» when it has one. */
    private Node renglon(Plan p) {
        Node izquierda;
        if (!p.tieneCuenta() && p.problema() == null && editable) {
            CheckBox c = new CheckBox();
            c.setSelected(marcadas.contains(p.area()));
            c.setAccessibleText("Crear la cuenta de " + p.area());
            c.setOnAction(e -> { if (c.isSelected()) marcadas.add(p.area()); else marcadas.remove(p.area()); pintar(); });
            izquierda = c;
        } else {
            Region r = new Region();
            izquierda = r;
        }
        StackPane caja = new StackPane(izquierda);
        caja.setMinWidth(24); caja.setPrefWidth(24); caja.setMaxWidth(24);

        Label nombre = new Label(p.area());
        nombre.getStyleClass().add("avance-area-nombre");
        Label usuario = new Label((p.tieneCuenta() ? "Usuario: " : "Su usuario será: ") + p.usuario());
        usuario.getStyleClass().add("cuentas-usuario");
        VBox textos = new VBox(1, nombre, usuario);
        HBox.setHgrow(textos, Priority.ALWAYS);
        textos.setMaxWidth(Double.MAX_VALUE);
        textos.setMinWidth(200);

        Label permisos = new Label(permisos(p.rol()));
        permisos.getStyleClass().add("muted-sm");
        permisos.setWrapText(true);
        fijo(permisos, 190);

        Label estado;
        if (p.problema() != null) {
            estado = new Label("No se puede crear");
            estado.getStyleClass().addAll("cuentas-estado", "cuentas-estado-problema");
            estado.setTooltip(new Tooltip(p.problema()));
        } else if (!p.tieneCuenta()) {
            estado = new Label("Sin cuenta");
            estado.getStyleClass().addAll("cuentas-estado", "cuentas-estado-sin");
        } else if (accesos.containsKey(p.area())) {
            estado = new Label("Entró " + haceCuanto(accesos.get(p.area())), new FontIcon("mdi2c-check-circle"));
            estado.getStyleClass().addAll("cuentas-estado", "cuentas-estado-entro");
        } else {
            estado = new Label("Nunca ha entrado");
            estado.getStyleClass().addAll("cuentas-estado", "cuentas-estado-nunca");
        }
        fijo(estado, 170);

        Node accion;
        if (p.tieneCuenta() && editable) {
            Button b = new Button("Contraseña nueva", icono("mdi2k-key-outline"));
            b.getStyleClass().add("revisar-boton");
            b.setTooltip(new Tooltip("Para un área que perdió su contraseña: la de ahora deja de servir y sale una nueva en un PDF"));
            b.setOnAction(e -> nuevaContrasena(p));
            b.setMinWidth(Region.USE_PREF_SIZE);
            accion = b;
        } else {
            accion = new Region();
        }
        StackPane accionCaja = new StackPane(accion);
        StackPane.setAlignment(accion, Pos.CENTER_RIGHT);
        fijo(accionCaja, 160);

        HBox h = new HBox(14, caja, textos, permisos, estado, accionCaja);
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("cuentas-renglon");
        if (marcadas.contains(p.area())) h.getStyleClass().add("cuentas-renglon-marcado");
        return h;
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    private void crearMarcadas() {
        if (marcadas.isEmpty()) return;
        Scene escena = dialog.getDialogPane().getScene();
        List<String> areas = List.copyOf(marcadas);
        DialogUtil.runAsyncWithProgress(escena, "Creando " + FormatUtils.plural(areas.size(), "cuenta", "cuentas") + "…",
            () -> {
                var cuentas = service.crear(areas);
                long nuevas = cuentas.stream().filter(CuentasAreaService.Cuenta::nueva).count();
                return Map.entry(nuevas, new ReporteCuentasService().exportCuentas(cuentas));
            },
            r -> {
                cambio = true;
                marcadas.clear();
                // The PDF is a temporary file and the passwords are stored only as
                // hashes: it is their one copy. Read before the PDF's window.
                NotificacionUtil.importanteYEspera("Guarda el PDF de contraseñas", FormatUtils.plural(r.getKey(), "cuenta creada", "cuentas creadas") + ".\n\n"
                    + "En la siguiente ventana usa «Guardar como…» y guárdalo en un lugar seguro: es un archivo "
                    + "temporal que se borra al cerrar SIBIM, y las contraseñas no se pueden volver a ver "
                    + "(si un área pierde la suya, aquí mismo le das una nueva).\n\n"
                    + "Imprímelo: la primera hoja es tu control (anota quién recibió cada una) y después viene una hoja "
                    + "para cada área, lista para entregar.");
                DialogUtil.showExportResultDialog(escena, r.getValue());
                cargar(escena, this::pintar);
            },
            e -> NotificacionUtil.error(escena, "No se pudieron crear las cuentas: " + MensajeError.de(e)));
    }

    private void nuevaContrasena(Plan p) {
        if (!ConfirmacionUtil.confirmarPeligro("Contraseña nueva para " + p.area(),
                "La contraseña de «" + p.usuario() + "» cambia ahora: quien tenga la de antes ya no podrá entrar "
                    + "hasta que le des la nueva, que sale en un PDF.", "Cambiar la contraseña")) return;
        Scene escena = dialog.getDialogPane().getScene();
        DialogUtil.runAsyncWithProgress(escena, "Cambiando la contraseña…",
            () -> new ReporteCuentasService().exportCuentas(List.of(service.nuevaContrasena(p.area()))),
            pdf -> {
                cambio = true;
                NotificacionUtil.importanteYEspera("Guarda el PDF", "Contraseña nueva para " + p.area() + ".\n\n"
                    + "En la siguiente ventana usa «Guardar como…» o imprímelo: la contraseña no se vuelve a ver.");
                DialogUtil.showExportResultDialog(escena, pdf);
            },
            e -> NotificacionUtil.error(escena, "No se pudo cambiar la contraseña: " + MensajeError.de(e)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    static String permisos(Rol rol) {
        return switch (rol) {
            case ADMIN -> "Administrador: todo el inventario";
            case SECRETARIO -> "Ve su secretaría y sus direcciones";
            case DIRECCION -> "Ve solo su área";
        };
    }

    /** "hoy", "ayer", "hace 5 días", or "el 03/10/2026". */
    static String haceCuanto(LocalDateTime cuando) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(cuando.toLocalDate(), java.time.LocalDate.now());
        if (dias <= 0) return "hoy";
        if (dias == 1) return "ayer";
        if (dias < 31) return "hace " + dias + " días";
        return "el " + cuando.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static FontIcon icono(String literal) {
        FontIcon i = new FontIcon(literal);
        i.getStyleClass().add("revisar-boton-icono");
        return i;
    }

    private static void fijo(Region r, double ancho) {
        r.setMinWidth(ancho);
        r.setPrefWidth(ancho);
        r.setMaxWidth(ancho);
    }

    private static String plano(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
