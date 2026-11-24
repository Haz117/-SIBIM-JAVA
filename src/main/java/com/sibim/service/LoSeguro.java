package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.util.*;

/**
 * Bienes › Más and Inicio › Por hacer › «Lo seguro, de una vez»: the fixes SIBIM makes without a
 * doubt, together. From 20 to 24-nov-2026 Patrimonio wrote MUEBLE by hand on 441 bienes and the 15 %
 * of valor residual on 174 while the tools that put them at once waited in four cards of Por hacer
 * (1,693 and 1,421 left; 375 folios fiscales in «Factura»; 720 colors the names say). Each one is
 * applied as its own tool applies it and undone apart.
 */
public final class LoSeguro {

    private LoSeguro() {}

    public enum Arreglo {
        // First: it saves bien by bien (ProductoService, its change time checked), and the others
        // change many at once and move that time.
        FACTURA("Lo que ya tiene su factura", "Proveedor, folio fiscal y fecha que tienen otros bienes de la misma compra, a los que no los "
            + "tienen; costo, vida útil, color, marca, modelo y tipo, solo entre los del mismo nombre. Solo cuando todos los que lo tienen dicen lo mismo"),
        FOLIO("El folio fiscal, a su lugar", "El UUID que quedó escrito en «Factura» pasa a «Folio fiscal»; en los formatos se imprime igual"),
        TIPO("MUEBLE a lo que no es vehículo", "El tipo de bien de los que no lo tienen y, por su clave CONAC, no son vehículo; los vehículos no se tocan"),
        RESIDUAL("El valor residual, el 15 % del costo", "A los que tienen costo y el residual en $0, de las categorías donde ya se usa el 15 %"),
        COLOR("El color que dice su nombre", "A los que no tienen color y su nombre o descripción lo dice («… COLOR NEGRO»)");

        public final String titulo, explica;
        Arreglo(String titulo, String explica) { this.titulo = titulo; this.explica = explica; }
    }

    /**
     * One fix: its bienes, an example and what is left out (the residual of the categorías where no
     * bien has the 15 % yet: there is no criterion to follow there).
     * @param colores for {@link Arreglo#COLOR}, each bien's color; empty for the others
     * @param deSuFactura for {@link Arreglo#FACTURA}, what each dato puts on which bienes; empty for the others
     */
    public record Propuesta(Arreglo arreglo, List<Producto> bienes, Map<Producto, String> colores,
                            List<MismaFactura.Cambio> deSuFactura, String ejemplo, String fuera) {

        Propuesta(Arreglo arreglo, List<Producto> bienes, Map<Producto, String> colores, String ejemplo, String fuera) {
            this(arreglo, bienes, colores, List.of(), ejemplo, fuera);
        }

        /** The datos it puts: one per bien, or each dato of each bien for «lo de su factura». */
        public int cambios() {
            return arreglo == Arreglo.FACTURA ? deSuFactura.stream().mapToInt(c -> c.bienes().size()).sum() : bienes.size();
        }
    }

    /** The fixes with something to do, in the order they are applied. */
    public static List<Propuesta> proponer(List<Producto> bienes) {
        List<Propuesta> out = new ArrayList<>();
        // 24-nov-2026: «235 datos que se pueden copiar de otro bien de la misma factura» waited in Por hacer.
        List<MismaFactura.Cambio> deSuFactura = MismaFactura.pendientes(bienes);
        if (!deSuFactura.isEmpty()) {
            Map<String, Producto> suyos = new LinkedHashMap<>();
            deSuFactura.forEach(c -> c.bienes().forEach(p -> suyos.putIfAbsent(p.getId(), p)));
            MismaFactura.Cambio e = deSuFactura.get(0);
            out.add(new Propuesta(Arreglo.FACTURA, new ArrayList<>(suyos.values()), Map.of(), deSuFactura,
                e.dato().etiqueta.toLowerCase(Locale.forLanguageTag("es-MX")) + " " + e.dato().texto(e.nuevo()) + " a "
                    + FormatUtils.plural(e.bienes().size(), "bien", "bienes") + " de la factura " + e.factura(), null));
        }
        List<Producto> folios = CalidadDatosService.folioEnFactura(bienes);
        if (!folios.isEmpty()) {
            Producto e = folios.get(0);
            out.add(new Propuesta(Arreglo.FOLIO, folios, Map.of(), e.getCodigo() + ": «" + e.getNumeroFactura().trim() + "»", null));
        }
        List<Producto> tipo = CalidadDatosService.sinTipoConSugerencia(bienes);
        if (!tipo.isEmpty())
            out.add(new Propuesta(Arreglo.TIPO, tipo, Map.of(), tipo.get(0).getCodigo() + " · " + tipo.get(0).getNombre(), null));
        List<Producto> residual = new ArrayList<>();
        List<String> sinCriterio = new ArrayList<>();
        int fueraDeCriterio = 0;
        for (CalidadDatosService.ResidualDeCategoria c : CalidadDatosService.residualPorCategoria(bienes)) {
            if (c.conElQuince() > 0) residual.addAll(c.sinResidual());
            else {
                fueraDeCriterio += c.sinResidual().size();
                if (sinCriterio.size() < 2) sinCriterio.add(com.sibim.util.CategoriaCorta.de(c.categoria()) + " " + c.sinResidual().size());
            }
        }
        if (!residual.isEmpty()) {
            Producto e = residual.get(0);
            out.add(new Propuesta(Arreglo.RESIDUAL, residual, Map.of(),
                e.getCodigo() + ": " + FormatUtils.formatCurrency(e.getPrecioCompra()) + " → "
                    + FormatUtils.formatCurrency(com.sibim.util.ProductoUtils.residualSugerido(e.getPrecioCompra())),
                fueraDeCriterio == 0 ? null : FormatUtils.plural(fueraDeCriterio, "bien queda", "bienes quedan")
                    + " fuera: en su categoría ninguno tiene el 15 % todavía (" + String.join(", ", sinCriterio)
                    + "); se ponen en Bienes › Más › «Valor residual: el 15 % del costo»"));
        }
        Map<Producto, String> colores = CalidadDatosService.coloresDelNombre(bienes);
        if (!colores.isEmpty()) {
            var e = colores.entrySet().iterator().next();
            out.add(new Propuesta(Arreglo.COLOR, new ArrayList<>(colores.keySet()), colores, e.getKey().getCodigo() + ": " + e.getValue(), null));
        }
        return out;
    }

    /** How many changes all of them make (a bien can be in several). */
    public static int cambios(List<Propuesta> propuestas) {
        return propuestas.stream().mapToInt(Propuesta::cambios).sum();
    }

    /** What was done, to undo it: ids for the tipo and the residual, the bienes as they were for the
     *  folios, each color's ids for the colors, what was saved for «lo de su factura» (and what failed). */
    public record Hecho(Arreglo arreglo, int cuantos, List<String> ids, List<Producto> antes, Map<String, List<String>> colores,
                        MismaFactura.Resultado deSuFactura, List<MismaFactura.Cambio> cambios, List<String> fallas) {
        Hecho(Arreglo arreglo, int cuantos, List<String> ids, List<Producto> antes, Map<String, List<String>> colores) {
            this(arreglo, cuantos, ids, antes, colores, null, List.of(), List.of());
        }
    }

    /** @param servicio saves «lo de su factura» bien by bien, as «Copiar datos de la misma factura» does */
    public static Hecho aplicar(CalidadDatosService calidad, ProductoService servicio, Propuesta p) throws java.sql.SQLException {
        return switch (p.arreglo()) {
            case FACTURA -> {
                MismaFactura.Resultado r = MismaFactura.aplicar(servicio, p.deSuFactura());
                yield new Hecho(p.arreglo(), r.guardados().size(), List.of(), List.of(), Map.of(), r, p.deSuFactura(), r.fallas());
            }
            case FOLIO -> {
                List<Producto> antes = p.bienes().stream().map(Producto::copia).toList();
                yield new Hecho(p.arreglo(), calidad.pasarFolioFiscal(p.bienes()), List.of(), antes, Map.of());
            }
            case TIPO -> {
                List<String> ids = calidad.asignarTextoIds(CalidadDatosService.Dato.TIPO, "MUEBLE", p.bienes());
                yield new Hecho(p.arreglo(), ids.size(), ids, List.of(), Map.of());
            }
            case RESIDUAL -> {
                List<String> ids = calidad.ponerResidual(p.bienes());
                yield new Hecho(p.arreglo(), ids.size(), ids, List.of(), Map.of());
            }
            case COLOR -> {
                Map<String, List<String>> hecho = calidad.asignarTextos(CalidadDatosService.Dato.COLOR, p.colores());
                yield new Hecho(p.arreglo(), hecho.values().stream().mapToInt(List::size).sum(), List.of(), List.of(), hecho);
            }
        };
    }

    /** @return how many went back */
    public static int deshacer(CalidadDatosService calidad, ProductoService servicio, Hecho h) throws java.sql.SQLException {
        return switch (h.arreglo()) {
            case FACTURA -> MismaFactura.deshacer(servicio, h.deSuFactura(), h.cambios());
            case FOLIO -> calidad.regresarFolioFiscal(h.antes());
            case TIPO -> calidad.quitarTexto(CalidadDatosService.Dato.TIPO, "MUEBLE", h.ids());
            case RESIDUAL -> calidad.quitarResidual(h.ids());
            case COLOR -> calidad.quitarTextos(CalidadDatosService.Dato.COLOR, h.colores());
        };
    }
}
