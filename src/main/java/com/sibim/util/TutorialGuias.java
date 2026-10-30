package com.sibim.util;

import com.sibim.controller.MainController;
import com.sibim.model.Usuario;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;

import java.util.ArrayList;
import java.util.List;

/** What the tutorial teaches, one guide per thing somebody comes to do: it takes them to
 *  the screen, marks the exact button and can open the real window. An área only gets
 *  the guides of what its account can do. Write here what the screens say, word by word:
 *  a guide that names a button that isn't there is worse than no guide. */
final class TutorialGuias {

    private TutorialGuias() {}

    private static final String GUINDA = "#A2232D", GUINDA_D = "#5A141A",
                                VERDE = "#235B4E", VERDE_D = "#1B4A3F",
                                DORADO = "#9C7A3C", DORADO_D = "#6E5118",
                                GRIS = "#64748B", GRIS_D = "#334155";

    /** A step of a guide.
     *  @param navigateId the screen it takes you to, or null to stay where you are
     *  @param foco       what to mark: a CSS selector on that screen, or "nav:<vista>" for a
     *                    button of the side menu; null marks the screen's own menu button
     *  @param accion     what "Probarlo ahora" does, or null when there is nothing to open */
    record Paso(String icon, String color, String gradEnd, String title, String shortcut,
                String navigateId, String foco, String accionTexto, Runnable accion, String[] bullets) {}

    /** @param id what remembers that it was seen */
    record Guia(String id, String titulo, String descripcion, String icon, List<Paso> pasos) {}

    private static Paso paso(String icon, String color, String gradEnd, String title, String navigateId, String foco, String... bullets) {
        return new Paso(icon, color, gradEnd, title, null, navigateId, foco, null, null, bullets);
    }

    private static Paso probar(Paso p, String texto, Runnable accion) {
        return new Paso(p.icon(), p.color(), p.gradEnd(), p.title(), p.shortcut(), p.navigateId(), p.foco(), texto, accion, p.bullets());
    }

    /** Goes to the screen and opens one of its windows (read by its controller on loading). */
    private static Runnable abrir(String vista, String accion) {
        return () -> {
            NavigationContext.setPendingAbrir(accion);
            MainController mc = MainController.getInstance();
            if (mc != null) mc.navigateToView(vista);
        };
    }

    private static Runnable ir(String vista) {
        return () -> {
            MainController mc = MainController.getInstance();
            if (mc != null) mc.navigateToView(vista);
        };
    }

    // ── Las guías ─────────────────────────────────────────────────────────────

    /** The guides this account can follow, in the order they are usually needed. */
    static List<Guia> disponibles() {
        List<Guia> g = new ArrayList<>();
        g.add(conoceElSistema());
        if (SessionManager.isAdmin()) {
            g.add(registrarResguardo());
            g.add(resguardoDePersona());
            g.add(conteoFisico());
            g.add(completarDatos());
            g.add(personasYSeries());
            g.add(etiquetas());
            g.add(resguardosEnLote());
            g.add(transferir());
            g.add(darDeBaja());
            g.add(prepararFormatos());
        } else {
            g.add(misBienes());
            g.add(resguardoDePersona());
            g.add(pedirBaja());
            g.add(recibirBienes());
        }
        return g;
    }

    /** What changed in this version, for whoever already knew the system. */
    static Guia novedades() {
        List<Paso> p = new ArrayList<>();
        if (SessionManager.isAdmin()) {
            p.add(paso("mdi2s-star-outline", GUINDA, GUINDA_D, "Lo nuevo en esta versión", null, null,
                "Hay pantallas nuevas para corregir los datos del inventario y varias se rediseñaron",
                "Cada paso te lleva a la pantalla y marca el botón; con «Probarlo ahora» la abres ya",
                "Nada se guarda hasta que tú lo confirmas dentro de cada ventana"));
            p.add(probar(paso("mdi2b-badge-account-outline", VERDE, VERDE_D, "Resguardos de una persona", "reportes", "#listaListados .report-row",
                "Es la primera fila de Reportes: busca a la persona sin entrar a Resguardos",
                "Escribe parte del nombre y Enter genera el PDF",
                "Si tiene bienes sin resguardo, el botón «Registrar como resguardo» los deja vigentes"),
                "Abrir el reporte", abrir("reportes", "resguardante")));
            p.add(probar(paso("mdi2c-clipboard-account-outline", DORADO, DORADO_D, "Nuevo Resguardo, rediseñado", "resguardos", "#btnNuevo",
                "A la izquierda la persona, a la derecha sus bienes, ya marcados",
                "Un bien que está en otro resguardo vigente te dice en cuál",
                "Avisa si la persona también aparece con su nombre en otro orden"),
                "Abrir Nuevo Resguardo", abrir("resguardos", "nuevo")));
            p.add(probar(paso("mdi2c-clipboard-check-outline", VERDE, VERDE_D, "Conteo físico, rediseñado", null, "nav:conteo",
                "Ya abre con todo el inventario sin problema: antes podía tronar el sistema",
                "Eliges área o categoría y ves su avance; un clic en el bien lo marca como visto",
                "La cantidad solo aparece en los lotes; tu avance se guarda solo al cerrar"),
                "Abrir el Conteo físico", ir("conteo")));
            p.add(probar(paso("mdi2c-clipboard-edit-outline", GUINDA, GUINDA_D, "Completar datos", "productos", "#btnMasBienes",
                "Está en Bienes › Más › Completar datos",
                "Por área: precio, fecha, vida útil y foto, en la misma lista",
                "Escribe el valor y Enter guarda y pasa al siguiente; la vida útil se puede proponer según CONAC"),
                "Abrir Completar datos", abrir("productos", "completar")));
            p.add(probar(paso("mdi2a-account-multiple-check-outline", GUINDA, GUINDA_D, "Unir personas y series repetidas", "productos", "#btnMasBienes",
                "Bienes › Más › Unir personas repetidas: el mismo nombre en otro orden o con título",
                "Bienes › Más › Series repetidas: una serie en varios bienes",
                "Tú decides cada caso; «Son personas distintas» no vuelve a preguntar"),
                "Abrir Unir personas", abrir("productos", "unir")));
            p.add(probar(paso("mdi2t-tag-multiple-outline", DORADO, DORADO_D, "Etiquetas y resguardos en lote", "productos", "#btnMasBienes",
                "Bienes › Más › Etiquetas por área: imprime por área y ve el avance",
                "Resguardos › Más › Resguardos en lote: varias personas a la vez, un solo PDF para firmar",
                "Cambiar resguardante y los formatos de baja ahora eligen de una lista, no a mano"),
                "Abrir Etiquetas por área", abrir("productos", "etiquetas")));
        } else {
            p.add(paso("mdi2s-star-outline", GUINDA, GUINDA_D, "Lo nuevo en esta versión", null, null,
                "Cada paso te lleva a la pantalla y marca dónde está",
                "Con «Probarlo ahora» abres la ventana de verdad"));
            p.add(probar(paso("mdi2b-badge-account-outline", VERDE, VERDE_D, "Resguardos de una persona", "reportes", "#listaListados .report-row",
                "Es la primera fila de Reportes: busca a la persona y genera su PDF",
                "Escribe parte del nombre y Enter genera el PDF"),
                "Abrir el reporte", abrir("reportes", "resguardante")));
            p.add(paso("mdi2f-file-remove-outline", DORADO, DORADO_D, "Pedir una baja es más fácil", "reportes", "#listaFormatos .report-row",
                "En Reportes, Solicitud de Baja ya no pide escribir códigos separados por coma",
                "Búscalo en la lista: escribe el código, Enter lo marca y sigues con el siguiente"));
        }
        return new Guia("novedades", "Novedades de esta versión", "Lo que cambió, con un clic para probar cada cosa", "mdi2s-star-outline", p);
    }

    private static Guia conoceElSistema() {
        Usuario user = SessionManager.getCurrentUser();
        String primerNombre = user != null && user.getNombre() != null && !user.getNombre().isBlank()
            ? user.getNombre().split("\\s+")[0] : null;
        String rol = SessionManager.isAdmin()
            ? "Tienes acceso completo como Administrador: inventario, documentos, usuarios y auditoría"
            : SessionManager.isSecretario()
                ? "Ves los bienes de tu secretaría y sus direcciones, y mantienes al día su ubicación, descripción, estado físico y fotos"
                : "Ves los bienes de tu área y mantienes al día su ubicación, descripción, estado físico y fotos";
        List<Paso> p = new ArrayList<>();
        p.add(paso("mdi2b-book-open-outline", GUINDA, GUINDA_D, primerNombre != null ? "¡Hola, " + primerNombre + "!" : "Bienvenido a SIBIM", null, null,
            rol, "Cada paso te lleva a la pantalla que describe, para que la veas mientras lees"));
        if (user != null && com.sibim.service.CuentasAreaService.CARGO_COMPARTIDA.equals(user.getCargo()))
            p.add(paso("mdi2a-account-edit-outline", GUINDA, GUINDA_D, "Cuenta compartida", null, null,
                "Esta cuenta la usa todo el personal de tu área",
                "Al entrar escribe tu nombre, o confirma el que aparece: así tus cambios quedan a tu nombre",
                "Si alguien más se sienta sin cerrar sesión: menú de tu cuenta › «Cambiar quién usa la cuenta»"));
        p.add(new Paso("mdi2v-view-dashboard-outline", GUINDA, GUINDA_D, "Inicio", "Ctrl + 1", "dashboard", null, null, null,
            SessionManager.isAdmin() ? new String[]{
                "Los recuadros son clicables: llevan directo a lo que cuentan",
                "Da clic en una categoría de la gráfica para ver sus bienes"
            } : new String[]{
                "«Pendientes de tu área»: los bienes sin foto, sin ubicación o sin estado físico",
                "Da clic en un pendiente, elige el bien y «Actualizar datos»",
                "Si Patrimonio te transfirió bienes, ahí mismo confirmas que los recibiste"
            }));
        p.add(new Paso("mdi2p-package-variant", GUINDA, GUINDA_D, "Bienes", "Ctrl + 3", "productos", null, null, null, new String[]{
            SessionManager.isAdmin()
                ? "Ctrl+N registra un bien nuevo, en el área que tengas filtrada; el número se asigna solo"
                : "Selecciona un bien y «Actualizar datos»: ubicación, estado físico, descripción y fotos",
            "Doble clic en una fila: foto, código QR e historial",
            "Busca mientras escribes y combina los filtros de categoría, área y estado"
        }));
        if (SessionManager.isAdmin()) {
            p.add(new Paso("mdi2s-swap-vertical", VERDE, VERDE_D, "Movimientos", "Ctrl + 5", "movimientos", null, null, null, new String[]{
                "«Transferir arrastrando» lleva bienes a otra área, con su número nuevo y su resguardo",
                "«Agregar o quitar» suma o resta piezas a un lote",
                "Aquí queda el historial de todo lo que entró, salió o cambió de área"
            }));
            p.add(paso("mdi2c-clipboard-account-outline", DORADO, DORADO_D, "Documentos", "resguardos", null,
                "Resguardos: quién tiene un bien a su cargo, con PDF para firma",
                "Préstamos, Comodatos y Actas están en el mismo grupo del menú",
                "Bajas: los bienes que salieron del inventario; su historial se conserva"));
        }
        p.add(new Paso("mdi2f-file-chart-outline", VERDE, VERDE_D, "Reportes", "Ctrl + 7", "reportes", null, null, null, new String[]{
            "La primera fila saca los resguardos de una persona",
            "Elige el período arriba: los reportes que muestran las fechas solo incluyen ese período",
            SessionManager.isAdmin() ? "Excel y CSV para trabajar los datos; PDF para entregar" : "Se exportan en PDF, listos para imprimir y firmar"
        }));
        p.add(new Paso("mdi2b-bell-outline", GUINDA, GUINDA_D, "Alertas", "Ctrl + 6", "alertas", null, null, null, new String[]{
            "Se generan solas: garantías por vencer, mantenimientos próximos y comodatos vencidos",
            "El número rojo en el menú dice cuántas hay sin atender"
        }));
        p.add(paso("mdi2w-wifi-off", DORADO, DORADO_D, "Sin internet", null, null,
            "Si se va el internet aparece la franja «Sin conexión» y puedes seguir trabajando",
            "Lo que captures se guarda en esta computadora y se sube solo al volver la conexión",
            "Las correcciones que tocan muchos bienes sí piden conexión: te lo dicen si no la hay"));
        p.add(new Paso("mdi2k-keyboard-outline", GRIS, GRIS_D, "Atajos que ahorran tiempo", "F1", null, null, null, null, new String[]{
            "Ctrl+1 a 9 cambian de pantalla · Ctrl+K busca en todo SIBIM · F5 actualiza",
            "Ctrl+N crea un registro · Ctrl+E edita la fila elegida",
            "F2 abre estas guías en cualquier momento · F1 muestra todos los atajos"
        }));
        return new Guia("general", "Conoce el sistema", "Un recorrido corto por las pantallas principales", "mdi2c-compass-outline", p);
    }

    private static Guia registrarResguardo() {
        return new Guia("registrar-resguardo", "Registrar un resguardo", "Asigna bienes a una persona, con folio y PDF para firmar",
            "mdi2c-clipboard-account-outline", List.of(
            paso("mdi2c-clipboard-account-outline", DORADO, DORADO_D, "1. Abre Nuevo Resguardo", "resguardos", "#btnNuevo",
                "Está arriba a la derecha en Resguardos (Ctrl+N)",
                "Un PDF que dice PENDIENTE no es un resguardo: aquí es donde se registra"),
            paso("mdi2a-account-search-outline", DORADO, DORADO_D, "2. Elige a la persona", "resguardos", "#btnNuevo",
                "Escribe parte del nombre; la primera coincidencia se elige sola",
                "Sus bienes sin resguardo aparecen ya marcados",
                "Si no existe, aparece «Persona nueva» con lo que escribiste"),
            probar(paso("mdi2c-check-circle-outline", DORADO, DORADO_D, "3. Revisa y guarda", "resguardos", "#btnNuevo",
                "Las pestañas A su nombre, De su área y Todos cambian la lista; marca o quita los que quieras",
                "Un bien en otro resguardo vigente dice en cuál y no se puede marcar",
                "«Guardar resguardo» le da folio RSG y queda vigente; después te ofrece abrir el PDF para firmar"),
                "Abrir Nuevo Resguardo", abrir("resguardos", "nuevo"))));
    }

    private static Guia resguardoDePersona() {
        List<Paso> p = new ArrayList<>();
        p.add(probar(paso("mdi2b-badge-account-outline", VERDE, VERDE_D, "Busca a la persona", "reportes", "#listaListados .report-row",
            "Es la primera fila de Reportes: «Resguardos por resguardante», botón PDF",
            "Escribe parte del nombre o del área, sin importar acentos",
            "Las flechas ↑ ↓ cambian de persona; Enter genera el PDF"),
            "Abrir el reporte", abrir("reportes", "resguardante")));
        p.add(paso("mdi2f-file-pdf-box", VERDE, VERDE_D, "Elige qué imprimir", "reportes", "#listaListados .report-row",
            "Cada resguardo es una tarjeta; los vigentes ya vienen marcados",
            "Abajo dice qué saldrá: varios resguardos salen juntos en un solo PDF",
            "«Sin resguardo registrado» son bienes a su nombre: en el PDF salen con folio RSP y dicen PENDIENTE"));
        if (SessionManager.isAdmin())
            p.add(paso("mdi2c-clipboard-account-outline", VERDE, VERDE_D, "De PENDIENTE a vigente", "reportes", "#listaListados .report-row",
                "En la tarjeta «Sin resguardo registrado», pulsa «Registrar como resguardo»",
                "Abre Nuevo Resguardo con la persona y esos bienes ya marcados",
                "Al guardar queda vigente, con folio RSG"));
        return new Guia("resguardo-persona", "Sacar los resguardos de una persona", "El PDF de lo que tiene alguien, sin entrar a Resguardos", "mdi2b-badge-account-outline", p);
    }

    private static Guia conteoFisico() {
        return new Guia("conteo", "Hacer un conteo físico", "Revisa que los bienes estén donde dice el sistema", "mdi2c-clipboard-check-outline", List.of(
            paso("mdi2c-clipboard-check-outline", VERDE, VERDE_D, "1. Elige qué contar", null, "nav:conteo",
                "Ábrelo en el menú, grupo Operación (Ctrl+Alt+C)",
                "A la izquierda elige un área o categoría: un área a la vez es lo más manejable",
                "Junto a cada una ves cuántos llevas"),
            paso("mdi2c-cursor-default-click-outline", VERDE, VERDE_D, "2. Marca lo que tengas enfrente", null, "nav:conteo",
                "Un clic en el bien lo marca como visto; con el teclado, la barra espaciadora marca y pasa al siguiente",
                "Con pistola lectora basta escanear cada etiqueta",
                "La cantidad solo aparece en los lotes"),
            paso("mdi2a-alert-circle-outline", VERDE, VERDE_D, "3. Anota lo que no coincida", null, "nav:conteo",
                "El botón «Incidencia» de cada bien: Mal estado, En otra área o Faltante",
                "También ahí: cambiar la cantidad o agregar una nota",
                "Las pestañas Pendientes y Con incidencia te dicen qué falta"),
            probar(paso("mdi2c-check-circle-outline", VERDE, VERDE_D, "4. Finaliza cuando termines", null, "nav:conteo",
                "Si cierras la ventana tu avance se guarda solo, y al abrir te ofrece retomarlo",
                "«Finalizar conteo» guarda lo elegido y aplica los ajustes de cantidad, con auditoría",
                "Lo que nadie marcó queda como «sin revisar», no como encontrado"),
                "Abrir el Conteo físico", ir("conteo"))));
    }

    private static Guia completarDatos() {
        return new Guia("completar", "Completar los datos de los bienes", "Precio, fecha, vida útil y foto, sin abrir ficha por ficha", "mdi2c-clipboard-edit-outline", List.of(
            paso("mdi2c-clipboard-edit-outline", GUINDA, GUINDA_D, "1. Ábrelo y elige un área", "productos", "#btnMasBienes",
                "Está en Bienes › Más › Completar datos",
                "El número junto a cada área es lo que le falta del dato elegido",
                "Arriba eliges el dato: Sin precio, Sin fecha, Sin vida útil o Sin foto"),
            paso("mdi2k-keyboard-outline", GUINDA, GUINDA_D, "2. Escribe y pulsa Enter", "productos", "#btnMasBienes",
                "Escribe el valor en la fila: Enter guarda y pasa al siguiente bien",
                "El costo es por pieza, también en los lotes",
                "La fila queda en verde si se guardó; en amarillo con el motivo si no"),
            probar(paso("mdi2c-calendar-check-outline", GUINDA, GUINDA_D, "3. La vida útil, de una vez", "productos", "#btnMasBienes",
                "En «Sin vida útil» aparece «Revisar y aplicar»: la propone según la clave CONAC",
                "Te muestra cuántos bienes por años antes de aplicar; cambia la depreciación",
                "Conviene hacerlo por área la primera vez; equipo de defensa se captura a mano"),
                "Abrir Completar datos", abrir("productos", "completar"))));
    }

    private static Guia personasYSeries() {
        return new Guia("personas-series", "Corregir personas y series repetidas", "Limpia los nombres y las series que vinieron del Excel", "mdi2a-account-multiple-check-outline", List.of(
            probar(paso("mdi2a-account-multiple-check-outline", GUINDA, GUINDA_D, "Personas repetidas", "productos", "#btnMasBienes",
                "Bienes › Más › Unir personas repetidas: el mismo nombre en otro orden o con título",
                "Si son la misma persona, marca cómo queda su nombre: se sugiere el que tiene más bienes, o escríbelo",
                "«Son la misma persona» cambia sus bienes y resguardos vigentes; no se deshace sola",
                "«Son personas distintas» las deja igual y no vuelve a preguntar"),
                "Abrir Unir personas", abrir("productos", "unir")),
            probar(paso("mdi2b-barcode-scan", GUINDA, GUINDA_D, "Series repetidas", "productos", "#btnMasBienes",
                "Bienes › Más › Series repetidas: cada serie que está en más de un bien",
                "Corrige la serie en su campo y Enter, o «Sin serie» si no tiene",
                "Avisa si está en varias áreas: podrían ser dos registros del mismo bien, revísalo en físico"),
                "Abrir Series repetidas", abrir("productos", "series"))));
    }

    private static Guia etiquetas() {
        return new Guia("etiquetas", "Etiquetar los bienes de un área", "Imprime por área y lleva la cuenta de lo pegado", "mdi2t-tag-multiple-outline", List.of(
            paso("mdi2t-tag-multiple-outline", DORADO, DORADO_D, "1. Elige el área y los bienes", "productos", "#btnMasBienes",
                "Está en Bienes › Más › Etiquetas por área; junto a cada área ves el avance",
                "«Sin etiquetar» muestra lo que falta; «Marcar todos» los elige de una vez"),
            probar(paso("mdi2p-printer", DORADO, DORADO_D, "2. Imprime y confirma", "productos", "#btnMasBienes",
                "«Imprimir etiquetas»: para pegar, o solo códigos QR",
                "Al terminar te pregunta si ya están pegadas: imprimir no es pegar",
                "«Ya están pegadas» las marca y dejan de contar en Alertas"),
                "Abrir Etiquetas por área", abrir("productos", "etiquetas"))));
    }

    private static Guia resguardosEnLote() {
        return new Guia("lote", "Registrar resguardos en lote", "Varias personas de una vez, con un solo PDF para firmar", "mdi2c-clipboard-multiple-outline", List.of(
            paso("mdi2c-clipboard-multiple-outline", DORADO, DORADO_D, "1. Primero une los nombres repetidos", "resguardos", "#btnMasResguardos",
                "Quien aparece como «Nombre repetido» no se puede marcar: únelo antes",
                "Está en Bienes › Más › Unir personas repetidas"),
            probar(paso("mdi2c-clipboard-check-outline", DORADO, DORADO_D, "2. Marca y registra", "resguardos", "#btnMasResguardos",
                "Está en Resguardos › Más › Resguardos en lote",
                "Elige a la izquierda para ver los bienes de cada quien",
                "«Registrar y generar PDF» le da un folio RSG a cada persona y saca un solo PDF",
                "Si una falla, las demás siguen, y al final te dice cuál"),
                "Abrir Resguardos en lote", abrir("resguardos", "lote"))));
    }

    private static Guia transferir() {
        return new Guia("transferir", "Transferir bienes a otra área", "Arrastrando, con número nuevo y resguardo", "mdi2d-drag-variant", List.of(
            paso("mdi2d-drag-variant", VERDE, VERDE_D, "1. Elige de dónde y qué", "movimientos", "#btnArrastrar",
                "Movimientos › «Transferir arrastrando»",
                "Elige el área de origen y selecciona los bienes (Ctrl o Mayús + clic para varios)"),
            probar(paso("mdi2s-swap-horizontal-bold", VERDE, VERDE_D, "2. Llévalos al área que recibe", "movimientos", "#btnArrastrar",
                "Arrástralos a la tarjeta del área, o da clic en ella",
                "Te pide el motivo y quién los recibe; salen de su resguardo anterior y se crea el nuevo",
                "Abajo queda lo transferido, con «Deshacer» por si fue un error"),
                "Abrir Transferir arrastrando", () -> { NavigationContext.setPendingTransferir(); ir("movimientos").run(); })));
    }

    private static Guia darDeBaja() {
        return new Guia("baja", "Dar de baja un bien", "Con la solicitud firmada del área", "mdi2a-archive-arrow-down-outline", List.of(
            paso("mdi2f-file-remove-outline", DORADO, DORADO_D, "1. La solicitud y el dictamen", "reportes", "#listaFormatos",
                "Reportes › Solicitud de Baja: búscalo en la lista; sale una página por bien",
                "Si el área técnica lo revisó, también el Dictamen Técnico de Baja",
                "El área los firma y los entrega a Patrimonio"),
            paso("mdi2a-archive-arrow-down-outline", DORADO, DORADO_D, "2. Regístrala", "productos", "#btnMasBienes",
                "En Bienes selecciona el bien y «Dar de baja»; anota el folio de la solicitud",
                "Si tiene un resguardo vigente, primero hay que quitarlo del resguardo",
                "Sale del inventario activo pero conserva su historial; se consulta en Bajas")));
    }

    private static Guia prepararFormatos() {
        return new Guia("formatos", "Preparar los formatos oficiales", "Los datos que los PDF necesitan y aún no se han capturado", "mdi2f-file-certificate-outline", List.of(
            paso("mdi2c-cog-outline", GRIS, GRIS_D, "1. Datos del municipio", "configuracion", null,
                "En Configuración captura RFC, domicilio, Elabora y Vo. Bo.",
                "Se usan en la hoja de resguardo de cada vehículo"),
            paso("mdi2o-office-building-outline", GRIS, GRIS_D, "2. Titular de cada área", "configuracion", null,
                "En Configuración › Áreas, la columna Titular",
                "Es el responsable en el Anexo V.4: sin él, esa columna sale vacía",
                "También lo propone el Resguardo general del área")));
    }

    private static Guia misBienes() {
        return new Guia("mis-bienes", "Mantener al día los bienes de mi área", "Ubicación, estado físico, descripción y fotos", "mdi2p-package-variant", List.of(
            paso("mdi2v-view-dashboard-outline", GUINDA, GUINDA_D, "1. Ve qué falta", "dashboard", null,
                "En Inicio, «Pendientes de tu área» dice qué bienes no tienen foto, ubicación o estado físico",
                "Da clic en un pendiente para ver sus bienes"),
            paso("mdi2p-package-variant", GUINDA, GUINDA_D, "2. Actualízalo", "productos", null,
                "Selecciona el bien y «Actualizar datos» (Ctrl+E)",
                "Cambias ubicación, estado físico, descripción y fotos",
                "Dar de alta, dar de baja y los movimientos los hace Patrimonio")));
    }

    private static Guia pedirBaja() {
        return new Guia("pedir-baja", "Pedir la baja de un bien", "La solicitud y el dictamen para Patrimonio", "mdi2f-file-remove-outline", List.of(
            paso("mdi2f-file-remove-outline", DORADO, DORADO_D, "1. Genera la solicitud", "reportes", "#listaFormatos",
                "Reportes › Solicitud de Baja: busca el bien; código y Enter lo marca",
                "Sale una página por bien, lista para firmar"),
            paso("mdi2f-file-certificate-outline", DORADO, DORADO_D, "2. El dictamen, si aplica", "reportes", "#listaFormatos",
                "Dictamen Técnico de Baja: lo llena el área técnica que revisó el bien",
                "Entrega todo firmado a Patrimonio, que registra la baja")));
    }

    private static Guia recibirBienes() {
        return new Guia("recibir", "Recibir bienes transferidos", "Confirma que ya tienes lo que te enviaron", "mdi2b-bell-outline", List.of(
            paso("mdi2b-bell-outline", GUINDA, GUINDA_D, "Confirma lo que llegó", "dashboard", null,
                "Cuando Patrimonio te transfiere un bien, aparece «Bienes por recibir» en la campana y en Inicio",
                "Ábrelo y confirma cuando lo tengas físicamente",
                "Lo que tu área necesite se pide a Finanzas, fuera de SIBIM")));
    }
}
