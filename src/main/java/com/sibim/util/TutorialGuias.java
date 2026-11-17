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

    private static final String GUINDA = "#9F2241", GUINDA_D = "#521628",
                                VERDE = "#235B4E", VERDE_D = "#1B4A3F",
                                DORADO = "#9C7A3C", DORADO_D = "#6E5118",
                                GRIS = "#64748B", GRIS_D = "#334155";

    /** A step of a guide.
     *  @param navigateId the screen it takes you to, or null to stay where you are
     *  @param foco       what to mark: a CSS selector on that screen, or "nav:<vista>" for a
     *                    button of the side menu; null marks the screen's own menu button
     *  @param accion     what "Probarlo ahora" does, or null when there is nothing to open
     *  @param grupo      in Novedades, which version it came in ("Nuevo en esta versión"), or null */
    record Paso(String icon, String color, String gradEnd, String title, String shortcut,
                String navigateId, String foco, String accionTexto, Runnable accion, String[] bullets,
                String grupo) {
        Paso(String icon, String color, String gradEnd, String title, String shortcut,
             String navigateId, String foco, String accionTexto, Runnable accion, String[] bullets) {
            this(icon, color, gradEnd, title, shortcut, navigateId, foco, accionTexto, accion, bullets, null);
        }
    }

    /** @param id what remembers that it was seen */
    record Guia(String id, String titulo, String descripcion, String icon, List<Paso> pasos) {}

    private static Paso paso(String icon, String color, String gradEnd, String title, String navigateId, String foco, String... bullets) {
        return new Paso(icon, color, gradEnd, title, null, navigateId, foco, null, null, bullets, null);
    }

    private static Paso probar(Paso p, String texto, Runnable accion) {
        return new Paso(p.icon(), p.color(), p.gradEnd(), p.title(), p.shortcut(), p.navigateId(), p.foco(), texto, accion, p.bullets(), p.grupo());
    }

    /** Steps {@code desde} (inclusive) to {@code hasta} (exclusive) of {@code p}, marked as come in {@code grupo}. */
    private static void enGrupo(List<Paso> p, int desde, int hasta, String grupo) {
        for (int i = desde; i < Math.min(hasta, p.size()); i++) {
            Paso x = p.get(i);
            p.set(i, new Paso(x.icon(), x.color(), x.gradEnd(), x.title(), x.shortcut(), x.navigateId(), x.foco(),
                x.accionTexto(), x.accion(), x.bullets(), grupo));
        }
    }

    static final String NUEVO = "Nuevo en esta versión", ANTERIOR = "De la versión anterior";

    /** The version whose changes {@link #novedades()} tells. Novedades open by
     *  themselves once per user for this version, not for every version: a
     *  version that only fixes things (1.0.23) would open the same steps
     *  again, now labelled as new. Move it when {@link #novedades()} gets new
     *  steps. */
    public static final String VERSION_NOVEDADES = "1.0.35";

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
            g.add(variosSeguidos());
            g.add(completarDatos());
            g.add(personasYSeries());
            g.add(etiquetas());
            g.add(resguardosEnLote());
            g.add(transferir());
            g.add(darDeBaja());
            g.add(prepararFormatos());
            g.add(areasYAvance());
            g.add(resguardosAlDia());
            g.add(encontrarYCorregir());
            g.add(formatosYReportes());
            g.add(transferenciasYBajas());
            g.add(masComodo());
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
                "Primero lo más nuevo: más espacio en el formulario y en Inicio, leer el XML de la factura, copiar lo del bien anterior, tomar datos de los del mismo nombre, el tipo de bien y el color que SIBIM ya sabe, y lo escrito de dos formas; luego lo de las versiones anteriores",
                "Cada paso te lleva a la pantalla y marca el botón; con «Probarlo ahora» la abres ya",
                "Nada se guarda hasta que tú lo confirmas dentro de cada ventana",
                "Lo de versiones más viejas ya no está aquí: está en las guías por tema (F2), como «Las áreas: cuentas, avance y avisos» o «Encontrar y corregir bienes»"));
            p.add(paso("mdi2v-view-column-outline", VERDE, VERDE_D, "Más espacio, menos bajar", "dashboard", "#porHacerSection",
                "En una pantalla ancha, el formulario del bien pone «Compra» junto a «El bien»: factura, folio, proveedor, costo, color, tipo y marca a la vista sin bajar",
                "Abajo, «Dónde está» junto a «Mantenimiento» y «Vehículos»; Tab sigue el orden de la factura",
                "En Bienes, la columna Nombre es la más ancha: el nombre se lee entero",
                "En Inicio, Por hacer va en tres columnas: lo que se arregla en un momento, lo que se captura y lo que sigue con las áreas",
                "En Resguardos, Bajas y las demás listas, los botones dicen «Exportar» y «Columnas» cuando caben",
                "Las ventanas grandes (Transferir, Completar datos, Cambiar datos, el detalle de un resguardo) se abren a su ancho: antes se cortaban a 600 px",
                "En Resguardos, los cancelados no se ven hasta que marcas «Ver cancelados», y el folio y el nombre se leen enteros; el detalle de un resguardo enseña más bienes a la vez",
                "En Movimientos, el nombre del bien es la columna más ancha",
                "El guinda es el del logo del Gobierno Municipal; el fondo es un gris cálido y los textos grises se leen mejor",
                "En Bienes, las categorías llevan el texto oscuro y su color en el ícono, y un bien sin precio dice «—» en vez de «$0.00»",
                "El buscador de Bienes también encuentra por número de factura o folio fiscal, con o sin espacios y guiones («A667» encuentra «A 667»)"));
            p.add(paso("mdi2f-file-code-outline", GUINDA, GUINDA_D, "El folio fiscal, sin teclearlo", "productos", "#btnEditar",
                "Bajo el folio fiscal, «Leer el XML de la factura…»: eliges el XML y se llenan el folio fiscal, el número de factura, la fecha, el proveedor y el costo por pieza con su IVA",
                "Solo llena lo que está vacío (un folio mal escrito sí lo cambia por el del XML) y dice abajo qué puso y qué dejó como estaba",
                "Si la factura trae varios conceptos, toma el que se llama como el bien o te pregunta cuál es",
                "Al escribir el folio a mano, la O se vuelve 0 y la I o la L se vuelven 1, y el aviso dice en qué grupo falta o sobra un carácter",
                "Al guardar un folio mal escrito, pregunta si lo corriges o lo guardas así"));
            p.add(paso("mdi2c-content-copy", VERDE, VERDE_D, "Copiar lo del bien anterior", "productos", "#btnEditar",
                "Al abrir el siguiente bien de una misma factura, junto a «Compra» dice «Copiar de TES/24: factura, folio fiscal, proveedor…»",
                "Un clic (o Ctrl+D) llena esos datos con los del bien que guardaste antes: factura, folio fiscal, proveedor, fecha, color, tipo y vida útil",
                "Solo llena lo que está vacío; lo que ya tiene el bien no se toca",
                "Si no era de la misma compra, «Quitar lo copiado» lo deja como estaba",
                "Va muy bien con Ctrl+Shift+S, que guarda y abre el siguiente"));
            p.add(probar(paso("mdi2c-content-duplicate", DORADO, DORADO_D, "Tomar de los del mismo nombre", "productos", "#btnMasBienes",
                "En Completar datos, en Sin factura, Sin proveedor, Sin precio, Sin fecha o Sin vida útil, una barra dice cuántos bienes pueden tomar ese dato de los demás bienes con su mismo nombre, cuando todos dicen lo mismo",
                "«Tomar de los del mismo nombre» enseña cada bien, lo que tomaría y de qué bienes sale; quita la marca a los que no sean de la misma compra",
                "Se guarda bien por bien, como en el formulario (con su historial de precios), y se deshace desde el aviso",
                "También sale en Inicio › Por hacer; con la factura puesta, «Copiar datos de la misma factura» llena lo demás de esa compra"),
                "Abrir Completar datos", abrir("productos", "mismonombre")));
            p.add(probar(paso("mdi2p-palette-outline", GUINDA, GUINDA_D, "El tipo y el color que ya se saben", "productos", "#btnMasBienes",
                "Inicio › Por hacer › «Poner MUEBLE»: el tipo de bien a todos los que no lo tienen y, por su clave CONAC, no son vehículo; se deshace desde el aviso",
                "Al abrir un bien sin tipo, ya viene escrito «MUEBLE» con la nota «Propuesto por su clave CONAC»; si es otro, escribe encima",
                "Completar datos › Sin color › «Tomar el color del nombre»: el color que ya dice su nombre o descripción («… COLOR NEGRO»); revisas la lista, quitas o cambias el que no sea, y se deshace desde el aviso",
                "En el formulario, el color y el tipo te sugieren lo ya escrito mientras tecleas, como la marca y el proveedor"),
                "Abrir Completar datos", abrir("productos", "completar:COLOR")));
            p.add(probar(paso("mdi2s-spellcheck", DORADO, DORADO_D, "Lo escrito de dos formas", "productos", "#btnMasBienes",
                "Bienes › Más › «Unir lo escrito de dos formas» (antes «Unir proveedores»): ahora también marcas, colores y tipos",
                "Por ejemplo «SMITH WESSON» y «SMITH & WESSON», «STHIL» y «STIHL», «AMARRILLO», «NEBRO», «MUELE» o «mueble»",
                "Quita la marca a lo que no sea lo mismo (dos marcas que se parecen); todo se deshace desde el aviso",
                "«Poner a todos» de Completar datos también se deshace ahora desde el aviso"),
                "Revisar", abrir("productos", "proveedores")));
            p.add(paso("mdi2p-pencil-box-multiple-outline", GUINDA, GUINDA_D, "Cambiar datos de varios bienes a la vez", "productos", "#table",
                "Marca varios bienes en la tabla (Ctrl o Shift + clic) y en la barra de abajo pulsa «Cambiar datos»",
                "Llena solo lo que cambia: proveedor, factura, folio fiscal, fecha, costo, vida útil, valor residual, color, tipo, marca, modelo o ubicación; lo vacío no se toca",
                "Junto a cada dato ves lo que tienen ahora; «Sumarle el IVA» y «El 15 % del costo» se calculan para cada bien",
                "Si marcaste toda la página (Ctrl+A) y el filtro tiene más, te pregunta si van también los demás; también está en el clic derecho",
                "Antes de guardar ves bien por bien qué cambia, y se deshace desde el aviso"));
            p.add(probar(paso("mdi2c-call-split", DORADO, DORADO_D, "Separar un área en una nueva", "configuracion", null,
                "Configuración › Áreas y prefijos: elige el área y pulsa «Separar en un área nueva…»",
                "Escribe el nombre (el prefijo se sugiere solo) y marca qué bienes pasan; «Marcar los de…» marca todos los de una persona o de una ubicación",
                "Cada quien se lleva sus bienes en un resguardo nuevo con su mismo cargo, y los resguardos que se quedan vacíos se cancelan solos",
                "Al terminar, «Imprimir resguardos» saca los nuevos para firmar"),
                "Ir a Configuración", ir("configuracion")));
            p.add(paso("mdi2f-format-size", VERDE, VERDE_D, "Menos tecleo y texto más grande", "productos", "#btnEditar",
                "Debajo del valor residual, «Poner el 15 % del costo» hace la cuenta con el costo que escribiste",
                "El tamaño del texto (Normal, Grande, Extra) está también en el menú de tu cuenta, arriba a la izquierda",
                "Ctrl + «+» agranda el texto y Ctrl + «−» lo achica, en cualquier pantalla",
                "En Datos de los formatos › Titulares, «Usar las sugerencias» escribe la primera sugerencia en todas las áreas sin titular: revisas, corriges y guardas",
                "En el formulario de un bien, Ctrl+S guarda y Ctrl+Shift+S guarda y abre el siguiente, sin soltar el teclado"));
            p.add(paso("mdi2c-check-all", GUINDA, GUINDA_D, "Detalles que se arreglaron", null, null,
                "Etiquetas, resguardos y reportes llevan el logo del Gobierno Municipal 2024-2027 aunque no se haya cargado uno en Configuración",
                "Los bienes de cada resguardo salen en orden de número (MM/01, MM/02… MM/10), en el PDF y en la pantalla",
                "En el resguardo, un bien sin precio capturado dice «—» en vez de «$0.00», y el total avisa que hay bienes sin precio",
                "Si la sesión se cierra por inactividad, al volver a entrar regresas a la pantalla donde estabas, con tu búsqueda de Bienes",
                "El aviso de inactividad ya no cierra la sesión dos veces ni te borra lo que empezabas a escribir al entrar",
                "Si la sesión se te cierra muy seguido, el mismo aviso tiene «Dale más tiempo»: te lleva al ajuste en Configuración",
                "Los textos grises y los porcentajes dorados son un poco más oscuros, para que se lean mejor"));
            p.add(probar(paso("mdi2c-clipboard-edit-outline", GUINDA, GUINDA_D, "Completar datos, con más datos", "productos", "#btnMasBienes",
                "Además de precio, fecha, vida útil y foto: proveedor, folio fiscal, factura, color y tipo de bien",
                "Se escribe en la lista y Enter pasa al siguiente, sin abrir el formulario de cada bien",
                "«Poner a todos» escribe lo mismo a los bienes de la lista que no lo tienen (busca, por ejemplo, «silla»)",
                "En Tipo de bien, «Revisar y aplicar» pone MUEBLE a todo lo que no es vehículo, según su clave CONAC"),
                "Abrir Completar datos", abrir("productos", "completar:PROVEEDOR")));
            p.add(probar(paso("mdi2c-call-merge", DORADO, DORADO_D, "Son el mismo bien", "productos", "#btnMasBienes",
                "Bienes › Más › «Son el mismo bien»: escribe los dos números (o el de la etiqueta vieja) y marca cuál se queda",
                "El que se queda toma lo que le falta del otro, sus fotos y su historial; si el otro estaba en un resguardo y este no, toma su lugar",
                "El otro se quita; su número queda anotado en la descripción y en Auditoría, y se puede deshacer desde el aviso"),
                "Abrir", abrir("productos", "juntar")));
            p.add(paso("mdi2f-form-select", GUINDA, GUINDA_D, "El formulario, en el orden de la factura", "productos", "#btnNuevoBien",
                "En «Datos Patrimoniales», arriba va la compra: factura, folio fiscal, proveedor, fecha, costo (con su IVA), vida útil y valor residual",
                "Luego el bien (color, tipo, marca, modelo, serie), dónde está, su mantenimiento y los datos de vehículo",
                "La segunda pestaña se llama «Cantidad y garantía»: el costo ya no está ahí"));
            p.add(paso("mdi2a-alert-outline", DORADO, DORADO_D, "¿Ya está registrado?", "productos", "#btnNuevoBien",
                "Al guardar un bien, SIBIM avisa si su serie ya la tiene otro bien, o si hace un momento se registró uno igual",
                "Tú decides: si es otro, se guarda; si es el mismo, regresas al formulario"));
            p.add(probar(paso("mdi2i-image-multiple-outline", VERDE, VERDE_D, "Fotos repetidas", "productos", "#btnMasBienes",
                "La misma foto en bienes que no son el mismo lote: casi siempre es la foto de otro bien",
                "Desde ahí ves cada bien, le quitas esa foto o juntas los dos registros si son el mismo",
                "En la PC de Patrimonio usa la copia del respaldo y tarda segundos; en otra, la primera vez baja las fotos"),
                "Revisar las fotos", abrir("productos", "fotos")));
            p.add(paso("mdi2o-order-bool-ascending-variant", GUINDA, GUINDA_D, "«Por hacer», en orden", "dashboard", "#porHacerSection",
                "Las tareas van por tipo: «Se arreglan en un momento» (SIBIM propone y tú confirmas), «Por capturar» (con la factura a la vista) y «Para seguir» (con las áreas)",
                "La primera dice «Empieza aquí»: en ese orden, cada una deja menos trabajo para la siguiente",
                "En «Completar datos» los filtros van en tres renglones (para los totales, de la factura, del bien), sin los que ya están en cero, y lo que hay que hacer va arriba de la lista"));
            p.add(paso("mdi2f-format-list-checks", VERDE, VERDE_D, "Más avisos en «Por hacer»", "dashboard", "#porHacerSection",
                "Si pasan más de 2 días sin respaldo de la base, lo dice primero",
                "Cuántas computadoras abrieron SIBIM con una versión anterior; un clic abre la lista de equipos"));
            p.add(paso("mdi2c-car-outline", GUINDA, GUINDA_D, "Un solo vehículo o una sola área", "reportes", "#listaFormatos .report-row",
                "Parque Vehicular › Generar: el anexo V.6 o el resguardo de uno o varios vehículos, no solo de todos",
                "Entrega-Recepción (anexo V.4) › Generar: de una o varias áreas, o de todo el inventario; sale un bien por hoja, como el formato oficial",
                "La ficha técnica y las hojas de un vehículo llevan su número de inventario anterior, no el de SIBIM"));
            p.add(probar(paso("mdi2r-receipt", GUINDA, GUINDA_D, "Lo mismo para toda la factura", "productos", "#btnMasBienes",
                "Al guardar un bien con su número de factura o su folio fiscal, SIBIM te ofrece ponerles lo que capturaste a los demás bienes de esa factura",
                "Proveedor, folio fiscal y fecha van a toda la factura; costo, vida útil, color, marca y modelo, solo a los del mismo nombre",
                "Solo llena los que no lo tienen o tenían lo mismo; un dato distinto que alguien ya capturó no se toca",
                "Puedes quitar la marca de lo que no quieras pasar, y deshacerlo desde el aviso",
                "Bienes › Más › «Copiar datos de la misma factura» llena de una vez lo que ya tiene un bien y a sus hermanos les falta"),
                "Ver qué se puede copiar", abrir("productos", "factura")));
            p.add(probar(paso("mdi2f-file-swap-outline", DORADO, DORADO_D, "Folios fiscales y proveedores en orden", "productos", "#btnMasBienes",
                "Bienes › Más › «Folio fiscal a su lugar»: el UUID que quedó escrito en «Factura» pasa a «Folio fiscal» (en los formatos se imprime igual)",
                "Completar datos › «Folio fiscal por revisar»: cada folio con una O por un cero o un carácter de menos dice qué tiene mal; lo corriges con la factura a la vista y Enter pasa al siguiente",
                "Si lo que quedó en «Folio fiscal» es el número de la factura («01329», «AAA4B»), «Pasarlos a N° Factura» lo pone en su lugar, y así «misma factura» reconoce esas compras",
                "En el formulario del bien, un folio fiscal con otra forma se avisa al escribirlo",
                "Bienes › Más › «Unir lo escrito de dos formas»: un mismo proveedor escrito de dos formas (con y sin «SA DE CV») queda escrito de una; todo se deshace desde el aviso"),
                "Revisar los folios", abrir("productos", "completar:FOLIO_REVISAR")));
            p.add(paso("mdi2c-cash-plus", VERDE, VERDE_D, "Menos tecleo", "productos", "#btnEditar",
                "Debajo del costo de un bien, «Sumarle el IVA (16 %)» hace la cuenta y la escribe; se usa una sola vez por costo",
                "Al abrir SIBIM, tu usuario ya está escrito: solo pones la contraseña"));
            // After the intro: 1.0.29, then 1.0.26–1.0.28. What came before is in the guides
            // (13-nov-2026: Novedades had 53 steps; «lo más viejo, en ayudas normales»).
            int anterior = indice(p, "Cambiar datos de varios bienes a la vez");
            enGrupo(p, 1, anterior, NUEVO);
            enGrupo(p, anterior, p.size(), ANTERIOR);
        } else {
            p.add(paso("mdi2s-star-outline", GUINDA, GUINDA_D, "Lo nuevo en esta versión", null, null,
                "Cada paso te lleva a la pantalla y marca dónde está",
                "Con «Probarlo ahora» abres la ventana de verdad"));
            p.add(paso("mdi2a-account-check-outline", GUINDA, GUINDA_D, "Tu usuario ya está escrito", null, null,
                "Al abrir SIBIM en esta computadora, el usuario con el que entraste la última vez ya está escrito: solo pones la contraseña",
                "Si es otra cuenta, escribe encima"));
            p.add(paso("mdi2c-clipboard-check-multiple-outline", VERDE, VERDE_D, "Tu avance, en tres pasos", "dashboard", "#miAreaSection",
                "En tu Inicio ves tus tres pasos: entrar a SIBIM, revisar tus bienes y confirmar sus etiquetas",
                "El paso que toca tiene su botón: «Revisar mis bienes» y después «Confirmar etiquetas»",
                "Al revisar, cada bien tiene sus botones: «Sí es» o «No es» (y escribes por qué); «Ya la tiene» para la etiqueta",
                "Para responder varios a la vez, márcalos con su casilla y usa los botones de abajo; cada respuesta se puede deshacer",
                "Ahí mismo «Completar» les pone foto, ubicación y estado físico; lo que les falta está en una sola tarjeta de tu Inicio",
                "Si Patrimonio te regresa un bien para revisarlo otra vez, junto a él ves su mensaje",
                "Patrimonio ve en el organigrama cómo va tu área"));
            p.add(paso("mdi2f-file-remove-outline", GUINDA, GUINDA_D, "Pide una baja", "productos", "#btnEliminar",
                "En Bienes selecciona el bien y pulsa «Solicitar baja»: escoge el motivo y di cómo está",
                "A Patrimonio le llega el aviso y sale el formato: fírmalo y entrégalo en Recursos Materiales y Patrimonio; sin él no se da de baja",
                "La respuesta la ves en tu Inicio, en «Tus solicitudes de baja»"));
            p.add(paso("mdi2m-magnify", VERDE, VERDE_D, "Escribe en los filtros", "productos", "#searchField",
                "En los filtros de área, persona y categoría ya puedes escribir para encontrarla rápido"));
            p.add(probar(paso("mdi2b-badge-account-outline", VERDE, VERDE_D, "Resguardos de una persona", "reportes", "#listaListados .report-row",
                "Es la primera fila de Reportes: busca a la persona y genera su PDF",
                "Escribe parte del nombre y Enter genera el PDF"),
                "Abrir el reporte", abrir("reportes", "resguardante")));
            p.add(paso("mdi2f-file-remove-outline", DORADO, DORADO_D, "Pedir una baja es más fácil", "reportes", "#listaFormatos .report-row",
                "En Reportes, Solicitud de Baja ya no pide escribir códigos separados por coma",
                "Búscalo en la lista: escribe el código, Enter lo marca y sigues con el siguiente"));
            p.add(paso("mdi2t-tag-text-outline", VERDE, VERDE_D, "Buscar por el número anterior", "productos", "#searchField",
                "En Bienes y con Ctrl+K encuentras un bien por el número que trae pegado, el del inventario anterior"));
        }
        return new Guia("novedades", "Novedades de esta versión", "Lo que cambió, con un clic para probar cada cosa", "mdi2s-star-outline", p);
    }

    private static int indice(List<Paso> p, String titulo) {
        for (int i = 0; i < p.size(); i++) if (p.get(i).title().equals(titulo)) return i;
        throw new IllegalStateException("No hay paso «" + titulo + "» en Novedades");
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
                "«Pendientes de tu área»: tus tres pasos, con el botón del que te toca",
                "«Datos por completar» abre «Revisar mis bienes»: cada bien con su botón «Completar»",
                "Si Patrimonio te transfirió bienes, ahí mismo confirmas que los recibiste"
            }));
        p.add(new Paso("mdi2p-package-variant", GUINDA, GUINDA_D, "Bienes", "Ctrl + 3", "productos", null, null, null, new String[]{
            SessionManager.isAdmin()
                ? "Ctrl+N registra un bien nuevo, en el área que tengas filtrada; el número se asigna solo"
                : "Selecciona un bien y «Actualizar datos»: ubicación, estado físico, descripción y fotos",
            SessionManager.isAdmin()
                ? "Doble clic en una fila: foto, código QR e historial; desde ahí mismo, «Editar»"
                : "Doble clic en una fila: foto, código QR e historial; desde ahí mismo, «Actualizar datos»",
            "Busca mientras escribes y combina los filtros de categoría, área y estado"
        }));
        if (SessionManager.isAdmin()) {
            p.add(new Paso("mdi2s-swap-vertical", VERDE, VERDE_D, "Movimientos", "Ctrl + 5", "movimientos", null, null, null, new String[]{
                "«Transferir bienes» los lleva a otra área, con su número nuevo y su resguardo",
                "«Agregar o quitar» suma o resta piezas a un lote",
                "Aquí queda el historial: en una transferencia, el número de antes y el nuevo, y a qué área fue"
            }));
            p.add(paso("mdi2c-clipboard-account-outline", DORADO, DORADO_D, "Documentos", "resguardos", null,
                "Resguardos: quién tiene un bien a su cargo, con PDF para firma; búscalo también por el código del bien",
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

    private static Guia variosSeguidos() {
        return new Guia("varios-seguidos", "Corregir o registrar varios bienes seguidos", "Sin volver a la lista por cada uno", "mdi2p-pencil", List.of(
            paso("mdi2p-pencil", GUINDA, GUINDA_D, "Corregir varios", "productos", "#btnEditar",
                "Filtra la lista, selecciona el primero y «Editar» (Ctrl+E); o doble clic y «Editar» en su detalle",
                "«Guardar cambios» está siempre a la vista, en cualquier sección",
                "«Guardar y abrir…» guarda y abre el de abajo en la misma sección, también al cambiar de página"),
            paso("mdi2p-plus-box-outline", GUINDA, GUINDA_D, "Registrar varios", "productos", "#btnNuevoBien",
                "«Nuevo Bien» (Ctrl+N) en el área que tengas filtrada",
                "En el último paso, «Guardar y registrar otro» abre el siguiente",
                "Conserva el área, la categoría, la ubicación y el resguardante; lo demás va en blanco")));
    }

    private static Guia completarDatos() {
        return new Guia("completar", "Completar los datos de los bienes", "Precio, fecha, vida útil y foto, sin abrir ficha por ficha", "mdi2c-clipboard-edit-outline", List.of(
            paso("mdi2c-clipboard-edit-outline", GUINDA, GUINDA_D, "1. Ábrelo y elige un área", "productos", "#btnMasBienes",
                "Está en Bienes › Más › Completar datos",
                "El número junto a cada área es lo que le falta del dato elegido",
                "Arriba eliges el dato: Sin precio, Sin fecha, Sin vida útil o Sin foto"),
            paso("mdi2k-keyboard-outline", GUINDA, GUINDA_D, "2. Escribe y pulsa Enter", "productos", "#btnMasBienes",
                "Escribe el valor en la fila: Enter guarda y pasa al siguiente bien",
                "El costo es por pieza, también en los lotes; al dar de alta un lote ves cuánto suma",
                "La fila queda en verde si se guardó; en amarillo con el motivo si no"),
            probar(paso("mdi2c-calendar-check-outline", GUINDA, GUINDA_D, "3. La vida útil, de una vez", "productos", "#btnMasBienes",
                "En «Sin vida útil» aparece «Revisar y aplicar»: la propone según la clave CONAC",
                "Te muestra cuántos bienes por años antes de aplicar; cambia la depreciación",
                "Equipo de defensa no tiene valor en el CONAC: busca «chaleco» y «Poner a todos» les pone los mismos años"),
                "Abrir Completar datos", abrir("productos", "completar"))));
    }

    private static Guia personasYSeries() {
        return new Guia("personas-series", "Corregir personas y series repetidas", "Limpia los nombres y las series que vinieron del Excel", "mdi2a-account-multiple-check-outline", List.of(
            probar(paso("mdi2a-account-multiple-check-outline", GUINDA, GUINDA_D, "Personas repetidas", "productos", "#btnMasBienes",
                "Bienes › Más › Unir personas repetidas: el mismo nombre en otro orden, con título o con una letra distinta",
                "Cuando cambia una letra te avisa: revisa que no sean dos personas (MARIO y MARIA también difieren en una)",
                "Si son la misma persona, marca cómo queda su nombre: se sugiere el que tiene más bienes, o escríbelo",
                "«Son la misma persona» cambia sus bienes y resguardos vigentes; mientras la ventana siga abierta, «Deshacer» lo regresa",
                "«Son personas distintas» las deja igual y no vuelve a preguntar"),
                "Abrir Unir personas", abrir("productos", "unir")),
            probar(paso("mdi2b-barcode-scan", GUINDA, GUINDA_D, "Series repetidas", "productos", "#btnMasBienes",
                "Bienes › Más › Series repetidas: cada serie que está en más de un bien",
                "Corrige la serie en su campo y Enter, o «Sin serie» si no tiene",
                "Si no es una serie de verdad (un modelo, algo de relleno), «Dejar los N sin serie» la quita a todos de una vez",
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
                "Está en Bienes › Más › Unir personas repetidas",
                "«2 personas» avisa que ese nombre trae a dos: el resguardo saldría a nombre de ambas"),
            probar(paso("mdi2c-clipboard-check-outline", DORADO, DORADO_D, "2. Marca y registra", "resguardos", "#btnMasResguardos",
                "Está en Resguardos › Más › Resguardos en lote",
                "Elige a la izquierda para ver los bienes de cada quien",
                "«Registrar y generar PDF» le da un folio RSG a cada persona y saca un solo PDF",
                "Si una falla, las demás siguen, y al final te dice cuál"),
                "Abrir Resguardos en lote", abrir("resguardos", "lote"))));
    }

    private static Guia transferir() {
        return new Guia("transferir", "Transferir bienes a otra área", "Con un clic, con número nuevo y resguardo", "mdi2d-drag-variant", List.of(
            paso("mdi2d-drag-variant", VERDE, VERDE_D, "1. Elige de dónde y qué", "movimientos", "#btnArrastrar",
                "Movimientos › «Transferir bienes»",
                "Elige el área de origen y marca los bienes: un clic en cada uno, o su código y Enter",
                "Lo marcado sigue marcado aunque busques otro: marca todos los que van a la misma persona, así salen en un solo resguardo"),
            probar(paso("mdi2s-swap-horizontal-bold", VERDE, VERDE_D, "2. Llévalos al área que recibe", "movimientos", "#btnArrastrar",
                "Da clic en la tarjeta del área, o arrástralos a ella",
                "Te pide el motivo y quién los recibe (el cargo se llena solo); salen de su resguardo anterior y se crea el nuevo",
                "Otra vuelta a la misma área ya trae el motivo y la persona",
                "Abajo queda lo transferido, con «Deshacer» por si fue un error"),
                "Abrir Transferir bienes", () -> { NavigationContext.setPendingTransferir(); ir("movimientos").run(); })));
    }

    private static Guia darDeBaja() {
        return new Guia("baja", "Dar de baja un bien", "Con la solicitud firmada del área", "mdi2a-archive-arrow-down-outline", List.of(
            paso("mdi2f-file-remove-outline", DORADO, DORADO_D, "1. La solicitud y el dictamen", "reportes", "#listaFormatos",
                "Reportes › Solicitud de Baja: búscalo en la lista; sale una página por bien",
                "Si el área técnica lo revisó, también el Dictamen Técnico de Baja",
                "El área los firma y los entrega a Patrimonio"),
            paso("mdi2a-archive-arrow-down-outline", DORADO, DORADO_D, "2. Regístrala", "productos", "#btnMasBienes",
                "En Bienes selecciona el bien y «Dar de baja»; anota el folio de la solicitud",
                "Si tiene un resguardo vigente, primero hay que quitarlo: Resguardos › Ver detalle › «Quitar del resguardo»",
                "Sale del inventario activo pero conserva su historial; se consulta en Bajas")));
    }

    private static Guia prepararFormatos() {
        return new Guia("formatos", "Preparar los formatos oficiales", "Los datos que los PDF necesitan y aún no se han capturado", "mdi2f-file-certificate-outline", List.of(
            probar(paso("mdi2c-cog-outline", GRIS, GRIS_D, "1. Datos del municipio y firmas", "configuracion", null,
                "En Configuración, «Completar los datos» abre una sola ventana: RFC, domicilio y quién firma cada formato",
                "Elaboró y Vo. Bo. van en casi todos; las firmas vacías salen como «Nombre y firma»"),
                "Abrir Datos de los formatos", abrir("configuracion", "formatos")),
            paso("mdi2o-office-building-outline", GRIS, GRIS_D, "2. Titular de cada área", "configuracion", null,
                "En la misma ventana, «Titulares de las áreas»: cada área con su campo, por secretaría",
                "Debajo salen sugerencias (quien firma resguardos ahí como director o titular); un clic lo escribe",
                "Es el responsable en el Anexo V.4: sin él, esa columna sale vacía")));
    }

    private static Guia misBienes() {
        return new Guia("mis-bienes", "Mantener al día los bienes de mi área", "Ubicación, estado físico, descripción y fotos", "mdi2p-package-variant", List.of(
            paso("mdi2v-view-dashboard-outline", GUINDA, GUINDA_D, "1. Ve qué falta", "dashboard", null,
                "En Inicio, «Pendientes de tu área» dice cuántos bienes no tienen foto, ubicación o estado físico",
                "Da clic en esa tarjeta: se abre «Revisar mis bienes» con los que les falta algo, cada uno con «Completar»"),
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

    /** Was in Novedades until 1.0.28 (steps of 1.0.25 and before), now a guide of its own. */
    private static Guia areasYAvance() {
        List<Paso> p = new ArrayList<>();
            p.add(paso("mdi2s-stairs-up", VERDE, VERDE_D, "Avance de las áreas", "organigrama", "#btnAvanceAreas",
                "Cada área da tres pasos: 1 entrar a SIBIM, 2 revisar sus bienes y llenar lo que falte, 3 confirmar sus etiquetas",
                "En el organigrama cada área lleva sus pasos 1·2·3; verde es hecho, dorado es a medias",
                "Un clic en los pasos de un área abre su detalle; «Avance de las áreas» las muestra todas, por dependencia",
                "Arriba, cinco tarjetas dicen cuántas van en cada paso (un clic filtra); cada área dice si ya tiene cuenta",
                "Si un área dice que un bien no es suyo, aparece en «Por hacer» para que lo revises"));
            p.add(probar(paso("mdi2s-stairs-up", VERDE, VERDE_D, "El avance de las áreas, más claro", "organigrama", "#btnAvanceAreas",
                "Arriba, cinco tarjetas: cuántas áreas faltan por entrar, cuántas revisan sus bienes, cuántas ponen etiquetas y cuántas terminaron; un clic filtra",
                "Las áreas van agrupadas por secretaría, cada una con sus tres pasos en una sola barra",
                "Cada área dice si ya tiene cuenta: sin cuenta no puede entrar; «Crear las cuentas» te lleva a hacerlo",
                "Un clic en un área abre su detalle, con el botón del paso que le toca resaltado",
                "En el detalle, el paso 3 abre «Etiquetas por área» ya en esa área: imprimes sus etiquetas y marcas las pegadas"),
                "Abrir el avance", abrir("organigrama", "avance")));
            p.add(paso("mdi2a-account-multiple-outline", GUINDA, GUINDA_D, "Cuentas de las áreas, antes de crearlas", "configuracion", null,
                "Configuración › Usuarios › «Cuentas por área» (o «Crear las cuentas» en el Avance) ahora abre una lista",
                "Cada área dice qué usuario le toca, qué podrá ver y si ya entró",
                "Marca con su casilla cuáles crear: todas de una vez o unas cuantas",
                "Sale un PDF con una hoja para entregar a cada área: su usuario, su contraseña y qué hacer el primer día; la primera es tu control",
                "Si un área pierde su contraseña, «Contraseña nueva» le da otra con su propia hoja"));
            p.add(paso("mdi2c-clipboard-check-multiple-outline", DORADO, DORADO_D, "Revisar bienes, más fácil para las áreas", "organigrama", "#btnAvanceAreas",
                "Cuando un área revisa sus bienes, cada renglón tiene «Sí es» / «No es», «Ya la tiene» y «Completar»",
                "Ya no hace falta Ctrl ni Shift: varios se marcan con su casilla",
                "Debajo del nombre sale marca, modelo, serie y ubicación, para reconocerlo",
                "Cada respuesta se puede deshacer; tú lo ves desde el detalle de cada área en «Avance de las áreas»"));
            p.add(probar(paso("mdi2a-alert-circle-outline", DORADO, DORADO_D, "Los avisos «No es de mi área», ahí mismo", "dashboard", null,
                "Cuando un área dice que un bien no es suyo, en «Por hacer» de tu Inicio sale «Revisar avisos», con lo que escribió",
                "Cada aviso tiene «Pasar a otra área»: abre Transferir con ese bien ya marcado y el aviso como motivo",
                "«Es suyo» se lo regresa a «Por revisar» con un mensaje tuyo, que el área lee junto al bien",
                "«Ver» abre su ficha: quién lo tenía, sus fotos y sus movimientos; cada cosa se puede deshacer"),
                "Abrir los avisos", abrir("productos", "observados")));
            p.add(paso("mdi2f-file-remove-outline", GUINDA, GUINDA_D, "Las áreas piden la baja por SIBIM", "bajas", null,
                "Cuando un área pide la baja de un bien te llega el aviso en la campana, en «Por hacer» y aquí en Bajas",
                "El área entrega el formato firmado en Recursos Materiales y Patrimonio: «Recibí el formato» anota su folio",
                "«Revisar» abre cada solicitud: «Ver el bien», «Dar de baja» (con su motivo y el folio ya escritos) o «No procede» con el porqué",
                "El área ve tu respuesta en su Inicio, y si le falta entregar el formato"));
        return new Guia("areas-avance", "Las áreas: cuentas, avance y avisos", "Crear sus cuentas, ver cómo van y atender lo que te avisan", "mdi2s-stairs-up", p);
    }

    /** Was in Novedades until 1.0.28 (steps of 1.0.25 and before), now a guide of its own. */
    private static Guia resguardosAlDia() {
        List<Paso> p = new ArrayList<>();
            p.add(paso("mdi2c-clipboard-search-outline", DORADO, DORADO_D, "Buscar en Resguardos", "resguardos", "#searchField",
                "Busca por persona, folio, área o el código de un bien: te dice en qué resguardo está",
                "Sin acentos también lo encuentra",
                "Abajo, al seleccionar uno: Ver detalle, Ver a la persona, PDF y Cancelar resguardo",
                "En el detalle ya se leen el código y el valor de cada bien, y puedes reimprimir el PDF"));
            p.add(probar(paso("mdi2c-call-merge", DORADO, DORADO_D, "Juntar resguardos de una persona", "resguardos", "#btnMasResguardos",
                "Está en Resguardos › Más › Juntar resguardos de una persona",
                "Quien tiene varios resguardos en la misma área aparece primero, con cuántos tiene",
                "Marca los que se juntan: quedan cancelados como se firmaron y se hace uno nuevo con todos sus bienes",
                "Imprime el nuevo para que lo firme"),
                "Abrir Juntar resguardos", abrir("resguardos", "juntar")));
            p.add(paso("mdi2a-account-switch-outline", GUINDA, GUINDA_D, "Pasar un resguardo a otra persona", "resguardos", "#btnMasResguardos",
                "Si alguien se da de baja y sus bienes se quedan en el área: selecciona su resguardo y ve a Más › Pasar a otra persona",
                "Elige a quien los recibe y escribe el motivo; también funciona con clic derecho sobre el resguardo",
                "Se cancelan todos sus resguardos de esa área, con una nota de a quién pasaron, y sale uno nuevo para firmar",
                "Si quien recibe ya tenía resguardo en esa área, queda todo en uno solo"));
            p.add(paso("mdi2f-file-document-outline", GUINDA, GUINDA_D, "Resguardo con marca, serie y precio", "productos", "#searchField",
                "La lista de bienes del resguardo trae Marca, Serie y Precio, con el Total al final",
                "Ya no lleva Tipo de bien, Asignación ni Color",
                "En un lote, el precio es el total y abajo dice cuántas piezas por cuánto",
                "Las áreas ven la marca y la serie, pero no el precio"));
            p.add(paso("mdi2c-car-outline", VERDE, VERDE_D, "El resguardo de un vehículo", "productos", "#searchField",
                "Busca el vehículo en Bienes, márcalo y pulsa «Resguardo PDF» en la barra de abajo",
                "Sale la hoja oficial del vehículo: sus datos, fotos, la responsiva y las tres firmas",
                "Antes solo salían los de todos los vehículos juntos, en Reportes › Parque Vehicular",
                "En Configuración del Sistema escribe el RFC, el domicilio y quién firma Elaboró y Vo. Bo.; el color se escribe a mano"));
        return new Guia("resguardos-al-dia", "Resguardos: buscar, juntar y pasar", "Encontrar un resguardo, juntar los de una persona o pasarlos a otra", "mdi2c-clipboard-search-outline", p);
    }

    /** Was in Novedades until 1.0.28 (steps of 1.0.25 and before), now a guide of its own. */
    private static Guia encontrarYCorregir() {
        List<Paso> p = new ArrayList<>();
            p.add(paso("mdi2t-tag-text-outline", VERDE, VERDE_D, "Buscar por el número anterior", "productos", "#searchField",
                "En Bienes y con Ctrl+K encuentras un bien por el número que trae pegado, el del inventario anterior",
                "En el Conteo físico, escanéalo o escríbelo y queda como visto",
                "En Transferir, el número anterior y Enter también lo marca",
                "Con el lector, en cualquier pantalla, abre su ficha"));
            p.add(paso("mdi2m-magnify", VERDE, VERDE_D, "Escribe en los filtros", "productos", "#resguardanteFilter",
                "En los filtros de área, persona y categoría ya puedes escribir para encontrarla rápido",
                "Cada palabra cuenta, sin acentos; Enter toma la primera",
                "Borra lo escrito para volver a ver todos"));
            p.add(paso("mdi2p-pencil", GUINDA, GUINDA_D, "Editar sin pasar por los pasos", "productos", "#btnEditar",
                "Al editar, «Guardar cambios» siempre está a la vista, en cualquier sección",
                "«Guardar y abrir…» pasa al bien de abajo en la misma sección: para corregir muchos seguidos",
                "El detalle del bien ya trae «Editar», y al registrar uno nuevo puedes seguir con «Guardar y registrar otro»",
                "Marca, modelo y serie tienen «No tiene»: ya no hace falta escribir «SIN MARCA»",
                "Al escribir el resguardante te sugiere los nombres que ya existen"));
            p.add(paso("mdi2c-calendar-clock", VERDE, VERDE_D, "La columna Garantía", "productos", "#statusChipsBar",
                "En Bienes, la que decía «Estado: Activo» en todos era la garantía: ahora se llama así",
                "Dice «Vencida» si ya venció y «—» si el bien no tiene garantía",
                "El filtro de abajo es «Garantía: Todos · Sin vencer · Vencida»"));
            p.add(paso("mdi2n-numeric", GUINDA, GUINDA_D, "Los números ya no se repiten", "productos", "#searchField",
                "El número de un bien dado de baja, transferido o borrado ya no se le da a otro bien",
                "El siguiente bien de un área toma el número que sigue al más alto que se ha usado",
                "Si deshaces una transferencia o reactivas un bien, vuelve con su mismo número"));
            p.add(probar(paso("mdi2c-calculator-variant-outline", GUINDA, GUINDA_D, "Costo de los lotes", "productos", "#btnMasBienes",
                "Bienes › Más › Costo de los lotes: los bienes de varias piezas, de mayor a menor total",
                "Si el costo capturado es el total de la factura, «Es el total del lote» lo divide entre las piezas",
                "Si te equivocas, «Deshacer» en el mismo renglón lo regresa"),
                "Abrir Costo de los lotes", abrir("productos", "lotes")));
            p.add(probar(paso("mdi2c-card-account-details-outline", GUINDA, GUINDA_D, "Solo el nombre, sin Lic. ni Ing.", "productos", "#btnMasBienes",
                "Bienes › Más › Quitar títulos de los nombres: ves cómo queda cada uno antes de cambiarlo",
                "Desde ahora los nombres se guardan sin título al escribirlos",
                "Los resguardos cancelados y los bienes dados de baja se quedan como se firmaron"),
                "Abrir Quitar títulos", abrir("productos", "titulos")));
            p.add(probar(paso("mdi2a-account-edit-outline", DORADO, DORADO_D, "Corregir el nombre de una persona", "reportes", "#listaListados .report-row",
                "Busca a la persona en «Resguardos por resguardante» y pulsa «Corregir nombre»",
                "Se corrige de una vez en sus bienes, sus resguardos vigentes y donde sea titular de área",
                "Al terminar, «Deshacer» lo regresa"),
                "Abrir el reporte", abrir("reportes", "resguardante")));
            p.add(paso("mdi2p-paw-outline", VERDE, VERDE_D, "Activos Biológicos", "categorias", null,
                "Categoría nueva para animales y plantas del municipio (cuenta CONAC 1.2.4.8)",
                "En la clave armonizada de cada bien puedes poner su subcuenta: perros 1.2.4.8.7, caballos 1.2.4.8.6…"));
        return new Guia("bienes-corregir", "Encontrar y corregir bienes", "Buscar por el número viejo, filtrar escribiendo y corregir datos de una vez", "mdi2m-magnify", p);
    }

    /** Was in Novedades until 1.0.28 (steps of 1.0.25 and before), now a guide of its own. */
    private static Guia formatosYReportes() {
        List<Paso> p = new ArrayList<>();
            p.add(paso("mdi2m-magnify", GUINDA, GUINDA_D, "Busca un reporte", "reportes", "#buscarReporte",
                "Arriba en Reportes escribe lo que necesitas: vehículos, baja, resguardo, CONAC…",
                "Encuentra también lo que está dentro de un menú, como el resguardo de cada vehículo",
                "Sin acentos también lo encuentra"));
            p.add(paso("mdi2d-draw", DORADO, DORADO_D, "Firmas: el nombre y abajo el cargo", "configuracion", "#tfFirmaElabora",
                "En todos los formatos que se firman va el nombre de la persona y debajo su cargo",
                "En Configuración del Sistema escribe quién firma: Elaboró, Vo. Bo., Síndico, Jefe de Inventarios, Tesorero, Contador y Presidente",
                "Si un nombre queda vacío, el formato dice «Nombre y firma» para escribirlo a mano"));
            p.add(paso("mdi2s-scale-balance", VERDE, VERDE_D, "Reportes para Tesorería y Transparencia", "reportes", "#listaFormatos .report-row",
                "«Saldos por cuenta contable (CONAC)»: costo, depreciación y valor en libros por cuenta, para cuadrar con Tesorería",
                "«Relación de Bienes Muebles (para publicar)»: el formato CONAC con código, descripción y valor en libros, sin datos personales",
                "Los dos salen en PDF y en Excel"));
        return new Guia("reportes", "Formatos y reportes", "Encontrar un reporte, quién firma y los de Tesorería", "mdi2f-file-chart-outline", p);
    }

    /** Was in Novedades until 1.0.28 (steps of 1.0.25 and before), now a guide of its own. */
    private static Guia transferenciasYBajas() {
        List<Paso> p = new ArrayList<>();
            p.add(probar(paso("mdi2d-drag-variant", VERDE, VERDE_D, "Transferir varios de una vez", "movimientos", "#btnArrastrar",
                "Un clic marca cada bien, ya no hace falta Ctrl; lo marcado sigue marcado aunque busques otro",
                "Escribe el código y Enter lo marca: así juntas varios y salen en un solo resguardo",
                "A la misma área, la siguiente vuelta ya trae el motivo y la persona",
                "Si la persona ya tiene resguardo en esa área, se agregan al suyo de más bienes en lugar de hacer uno más, aunque escribas su nombre con otro título",
                "Si escribes un nombre casi igual a uno que ya existe, te pregunta si es esa persona"),
                "Abrir Transferir", () -> { NavigationContext.setPendingTransferir(); ir("movimientos").run(); }));
            p.add(paso("mdi2i-inbox-arrow-down-outline", DORADO, DORADO_D, "Por recibir, por área", "movimientos", "#btnPorRecibir",
                "Las transferencias que el área no ha confirmado, juntas por área",
                "Cuando te llegue su acuse firmado en papel, «Confirmar las N» las da por recibidas de una vez",
                "Queda registrado con tu nombre y «acuse en papel»"));
            p.add(paso("mdi2l-lock-check-outline", DORADO, DORADO_D, "Bajas definitivas", "bajas", "#chipDefinitivas",
                "En Bajas: «En proceso» son las dadas de baja; «Definitivas», las que ya se destruyeron, donaron o subastaron",
                "Selecciona una o varias y «Baja definitiva» pide la fecha y el documento que la autoriza",
                "Una baja definitiva ya no se puede reactivar"));
        return new Guia("transferencias-bajas", "Transferencias y bajas", "Varios bienes de una vez, lo que falta por recibir y las bajas definitivas", "mdi2s-swap-horizontal-bold", p);
    }

    /** Was in Novedades until 1.0.28 (steps of 1.0.25 and before), now a guide of its own. */
    private static Guia masComodo() {
        List<Paso> p = new ArrayList<>();
            p.add(paso("mdi2f-format-list-checks", GUINDA, GUINDA_D, "«Por hacer» en Inicio", "dashboard", "#porHacerSection",
                "Lo que le falta al inventario, con su número real",
                "Un clic te lleva a la pantalla que lo resuelve; lo que ya quedó deja de aparecer"));
            p.add(paso("mdi2g-gesture-tap-button", VERDE, VERDE_D, "Botones que dicen lo que hacen", null, null,
                "Al confirmar, el botón dice la acción: «Cancelar el resguardo», «Descartar cambios», «Abrir el PDF»",
                "Lo que no tiene vuelta sale en rojo, y Enter no lo aprieta por costumbre",
                "Si algo falla, el aviso lo dice en palabras: sin conexión, dato repetido, sin permiso",
                "El «Deshacer» de los avisos también funciona con Ctrl+Z, y el aviso espera mientras tengas el mouse encima"));
            p.add(paso("mdi2s-speedometer", DORADO, DORADO_D, "Modo ligero para computadoras lentas", "configuracion", "#chkModoLigero",
                "En una computadora sin buena tarjeta de video quita sombras y animaciones: las pantallas y el tutorial ya no se traban",
                "SIBIM lo enciende solo si nota que la computadora va lenta",
                "Está en Configuración › Accesibilidad y rendimiento, por si lo quieres encender o apagar"));
        return new Guia("comodo", "SIBIM más cómodo", "Por hacer, avisos que se deshacen y una computadora lenta", "mdi2g-gesture-tap-button", p);
    }
}
