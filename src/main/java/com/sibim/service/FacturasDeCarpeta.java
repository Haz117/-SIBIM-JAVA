package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FacturaPdf;
import com.sibim.util.FacturaXml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bienes › Más › «Leer las facturas de una carpeta»: every factura (PDF or XML) of a folder,
 * matched to its bienes by the folio fiscal, to fill what they lack — proveedor, fecha and the
 * whole folio where only its end was written (19-nov-2026: 1,091 bienes carry a UUID of 341
 * facturas; 415 of them without proveedor, and Patrimonio has the facturas in PDF). Only what is
 * empty is filled; a proveedor written otherwise stays.
 */
public final class FacturasDeCarpeta {

    private FacturasDeCarpeta() {}

    public record Leida(Path archivo, FacturaXml.Factura factura) {}
    public record NoLeida(Path archivo, String motivo) {}
    public record Lectura(List<Leida> leidas, List<NoLeida> noLeidas) {}

    /** One factura with bienes to fill: what each gets. */
    public record Propuesta(Leida leida, String proveedor, List<Producto> bienes,
                            long sinProveedor, long sinFecha, long folioIncompleto) {
        public boolean llenaAlgo() { return (proveedor != null && sinProveedor > 0) || sinFecha > 0 || folioIncompleto > 0; }
    }

    /** The municipio as the receptor of its facturas, so that a PDF's reading doesn't take it for
     *  the proveedor: {its RFC (Configuración › Datos de los formatos, may be empty), the name of
     *  the municipio («Ixmiquilpan»)}. */
    public static String[] receptor() {
        try {
            var cr = new com.sibim.repository.ConfiguracionRepository();
            String municipio = cr.get("municipio", "Ixmiquilpan, Hidalgo");
            return new String[]{cr.get("rfc_municipio", "").trim(), municipio.split(",")[0].trim()};
        } catch (Exception e) {
            return new String[]{"", "Ixmiquilpan"};
        }
    }

    /** Reads the .pdf and .xml of {@code carpeta} and its subfolders. An RFC that most facturas of
     *  the folder repeat is the receptor's (the municipio's), not a proveedor's.
     *  @param rfcMunicipio may be empty  @param municipio «Ixmiquilpan» */
    public static Lectura leer(Path carpeta, String rfcMunicipio, String municipio, DoubleConsumer avance) throws IOException {
        List<Path> archivos;
        try (Stream<Path> s = Files.walk(carpeta, 4)) {
            archivos = s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".pdf") || n.endsWith(".xml");
            }).sorted().toList();
        }
        Map<Path, String> textos = new LinkedHashMap<>();
        List<NoLeida> noLeidas = new ArrayList<>();
        List<Leida> leidas = new ArrayList<>();
        int i = 0;
        for (Path p : archivos) {
            avance.accept(0.8 * i++ / Math.max(1, archivos.size()));
            try {
                if (p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")) leidas.add(new Leida(p, FacturaXml.leer(p)));
                else textos.put(p, FacturaPdf.texto(p));
            } catch (FacturaXml.NoEsFactura e) {
                noLeidas.add(new NoLeida(p, e.getMessage()));
            } catch (Exception e) {
                noLeidas.add(new NoLeida(p, "No se pudo abrir: " + Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName())));
            }
        }
        Set<String> ajenos = new HashSet<>(repetidos(textos.values()));
        if (rfcMunicipio != null && !rfcMunicipio.isBlank()) ajenos.add(rfcMunicipio.trim().toUpperCase(Locale.ROOT));
        for (var e : textos.entrySet()) {
            try {
                leidas.add(new Leida(e.getKey(), FacturaPdf.leerTexto(e.getValue(), ajenos, List.of(municipio))));
            } catch (FacturaXml.NoEsFactura ex) {
                noLeidas.add(new NoLeida(e.getKey(), ex.getMessage()));
            }
        }
        avance.accept(1);
        return new Lectura(sinRepetir(leidas), noLeidas);
    }

    private static final java.util.regex.Pattern RFC = java.util.regex.Pattern.compile("(?<![A-ZÑ&0-9])([A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3})(?![A-Z0-9])");

    /** The RFCs in more than half of the PDFs, when there are four or more: the receptor's. */
    static Set<String> repetidos(Collection<String> textos) {
        if (textos.size() < 4) return Set.of();
        Map<String, Integer> cuenta = new HashMap<>();
        for (String t : textos) {
            Set<String> en = new HashSet<>();
            var m = RFC.matcher(t.toUpperCase(Locale.ROOT));
            while (m.find()) en.add(m.group(1));
            en.forEach(r -> cuenta.merge(r, 1, Integer::sum));
        }
        return cuenta.entrySet().stream().filter(e -> e.getValue() * 2 > textos.size()).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /** One per folio fiscal (the XML before its PDF); an emisor without name takes the one another
     *  factura of its RFC gives. */
    static List<Leida> sinRepetir(List<Leida> leidas) {
        Map<String, Leida> porUuid = new LinkedHashMap<>();
        for (Leida l : leidas) {
            String k = MismaFactura.uuid(l.factura().uuid());
            if (k == null) continue;
            Leida ya = porUuid.get(k);
            boolean xml = l.archivo().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
            if (ya == null || (xml && !ya.archivo().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))) porUuid.put(k, l);
        }
        Map<String, String> nombrePorRfc = new HashMap<>();
        for (Leida l : porUuid.values())
            if (l.factura().rfcEmisor() != null && l.factura().emisor() != null) nombrePorRfc.putIfAbsent(l.factura().rfcEmisor(), l.factura().emisor());
        List<Leida> out = new ArrayList<>();
        for (Leida l : porUuid.values()) {
            FacturaXml.Factura f = l.factura();
            if (f.emisor() == null && f.rfcEmisor() != null && nombrePorRfc.containsKey(f.rfcEmisor()))
                f = new FacturaXml.Factura(f.uuid(), f.numero(), f.fecha(), nombrePorRfc.get(f.rfcEmisor()), f.rfcEmisor(),
                    f.moneda(), f.total(), f.conceptos());
            out.add(new Leida(l.archivo(), f));
        }
        return out;
    }

    /** The UUID of a bien: its folio fiscal, or the one typed in «Factura». */
    static String uuidDe(Producto p) {
        String u = MismaFactura.uuid(p.getFolioFiscal());
        return u != null ? u : MismaFactura.uuid(p.getNumeroFactura());
    }

    /** For each factura, its bienes in force and what they lack of it, the ones with more first.
     *  A bien with only the end of the folio («5BA08») goes with the one factura of the folder
     *  that ends so. The proveedor is written as the inventory already writes it, if it does. */
    public static List<Propuesta> proponer(Lectura lectura, List<Producto> inventario) {
        Map<String, List<Producto>> porUuid = new HashMap<>();
        List<Producto> cortos = new ArrayList<>();
        Map<String, String> proveedores = new HashMap<>();
        for (Producto p : inventario) {
            if (p.getFechaBaja() != null) continue;
            if (p.getProveedor() != null && !p.getProveedor().isBlank())
                proveedores.putIfAbsent(CalidadDatosService.claveTexto(p.getProveedor()), p.getProveedor().trim());
            String u = uuidDe(p);
            if (u != null) porUuid.computeIfAbsent(u, k -> new ArrayList<>()).add(p);
            else if (CalidadDatosService.finalDeFolio(p.getFolioFiscal()) != null) cortos.add(p);
        }
        Map<String, List<String>> porFinal = new HashMap<>();
        for (Leida l : lectura.leidas()) {
            String u = MismaFactura.uuid(l.factura().uuid());
            for (Producto c : cortos) {
                String fin = CalidadDatosService.finalDeFolio(c.getFolioFiscal()).replace("-", "");
                if (u.endsWith(fin)) porFinal.computeIfAbsent(c.getId(), k -> new ArrayList<>()).add(u);
            }
        }
        List<Propuesta> out = new ArrayList<>();
        for (Leida l : lectura.leidas()) {
            String u = MismaFactura.uuid(l.factura().uuid());
            List<Producto> bienes = new ArrayList<>(porUuid.getOrDefault(u, List.of()));
            for (Producto c : cortos) if (List.of(u).equals(porFinal.get(c.getId()))) bienes.add(c);
            if (bienes.isEmpty()) continue;
            String prov = l.factura().emisor() == null ? null
                : proveedores.getOrDefault(CalidadDatosService.claveTexto(l.factura().emisor()),
                    l.factura().emisor().trim().toUpperCase(Locale.forLanguageTag("es-MX")));
            long sinProv = bienes.stream().filter(b -> vacio(b.getProveedor())).count();
            long sinFecha = l.factura().fecha() == null ? 0 : bienes.stream().filter(b -> b.getFechaAdquisicion() == null).count();
            long incompleto = bienes.stream().filter(FacturasDeCarpeta::folioIncompleto).count();
            out.add(new Propuesta(l, prov, bienes, sinProv, sinFecha, incompleto));
        }
        out.sort(Comparator.comparingLong((Propuesta p) -> -(p.sinProveedor() + p.sinFecha() + p.folioIncompleto()))
            .thenComparing(p -> p.leida().archivo().getFileName().toString()));
        return out;
    }

    /** What to save for the chosen facturas: each bien, only what it lacks.
     *  @param proveedor the proveedor of each factura as it was left in the review (may be blank) */
    public static List<EditarVarios.Renglon> renglones(List<Propuesta> elegidas, Map<Propuesta, String> proveedor) {
        List<EditarVarios.Renglon> out = new ArrayList<>();
        for (Propuesta p : elegidas) {
            String prov = Objects.requireNonNullElse(proveedor.getOrDefault(p, p.proveedor()), "").trim();
            String folio = conGuiones(MismaFactura.uuid(p.leida().factura().uuid()));
            var fecha = p.leida().factura().fecha();
            List<EditarVarios.Cambio> cambios = List.of(
                new EditarVarios.Cambio(EditarVarios.Dato.FOLIO_FISCAL, b -> folioIncompleto(b) ? folio : null, folio),
                new EditarVarios.Cambio(EditarVarios.Dato.PROVEEDOR, b -> !prov.isEmpty() && vacio(b.getProveedor()) ? prov : null, prov),
                new EditarVarios.Cambio(EditarVarios.Dato.FECHA, b -> fecha != null && b.getFechaAdquisicion() == null ? fecha : null,
                    fecha == null ? "" : com.sibim.util.FormatUtils.formatDate(fecha)));
            out.addAll(EditarVarios.revisar(p.bienes(), cambios));
        }
        return out;
    }

    /** Without its whole folio fiscal: empty or only its end. One whose UUID was typed in «Factura»
     *  is moved by «Folio fiscal a su lugar», which also empties «Factura». */
    static boolean folioIncompleto(Producto b) {
        return MismaFactura.uuid(b.getFolioFiscal()) == null && MismaFactura.uuid(b.getNumeroFactura()) == null;
    }

    static String conGuiones(String hex) {
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
