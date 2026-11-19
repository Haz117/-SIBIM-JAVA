package com.sibim.service;

import com.sibim.model.Producto;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the vehicle list of October 2026 said of each vehicle and the inventory keeps only as
 * text in its descripción — «Placas: HG043A8. Estatus: Funcionando. Tarjeta de circulación: si.
 * Tenencia pagada: no. Seguro: no. Verificación: no.» — read back as data (19-nov-2026: of 113
 * vehicles, 45 without seguro, 70 without verificación, 64 without the tenencia paid, 16 broken
 * down, 6 retained and 19 without placas, and no screen said so). A tarjeta de circulación or a
 * póliza captured in their fields counts as having it.
 */
public final class SituacionVehiculos {

    private SituacionVehiculos() {}

    /** Sí, no, or not said. */
    public enum Tiene { SI, NO, SIN_DATO;
        public String texto() { return this == SI ? "Sí" : this == NO ? "No" : "—"; }
    }

    public record Situacion(Producto vehiculo, String placas, String estatus,
                            Tiene tarjeta, Tiene seguro, Tiene tenencia, Tiene verificacion) {
        public boolean sinPlacas() { return "SIN PLACAS".equals(placas); }
        public boolean descompuesto() { return estatus.startsWith("DESCOMPUEST") || estatus.contains("MAL ESTADO"); }
        public boolean retenido() { return estatus.startsWith("RETENID"); }
        /** Something to attend: a paper missing, no placas, broken down or retained. */
        public boolean pendiente() {
            return seguro == Tiene.NO || tarjeta == Tiene.NO || tenencia == Tiene.NO || verificacion == Tiene.NO
                || sinPlacas() || descompuesto() || retenido();
        }
    }

    private static final Pattern PLACAS = Pattern.compile("(?i)placas\\s*:\\s*([^.]*)\\.");
    private static final Pattern ESTATUS = Pattern.compile("(?i)estatus\\s*:\\s*([^.]*)\\.");
    private static final Pattern TARJETA = Pattern.compile("(?i)tarjeta de circulaci[oó]n\\s*:\\s*([^.]*)\\.");
    private static final Pattern SEGURO = Pattern.compile("(?i)seguro\\s*:\\s*([^.]*)\\.");
    private static final Pattern TENENCIA = Pattern.compile("(?i)tenencia(?:\\s+pagada)?\\s*:\\s*([^.]*)\\.");
    private static final Pattern VERIFICACION = Pattern.compile("(?i)verificaci[oó]n\\s*:\\s*([^.]*)\\.");
    private static final Locale ES = Locale.forLanguageTag("es-MX");

    public static Situacion de(Producto p) {
        String d = Objects.requireNonNullElse(p.getDescripcion(), "") + " ";
        String placas = grupo(PLACAS, d).toUpperCase(ES);
        if (placas.equals("NO") || placas.equals("SIN PLACAS") || placas.equals("S/P")) placas = "SIN PLACAS";
        Tiene tarjeta = hay(p.getNoTarjetaCirculacion()) ? Tiene.SI : siNo(grupo(TARJETA, d));
        Tiene seguro = hay(p.getNoPolizaSeguro()) ? Tiene.SI : siNo(grupo(SEGURO, d));
        return new Situacion(p, placas, grupo(ESTATUS, d).toUpperCase(ES), tarjeta, seguro,
            siNo(grupo(TENENCIA, d)), siNo(grupo(VERIFICACION, d)));
    }

    /** The vehicles in force (as the V.6 counts them), the ones with something to attend first,
     *  then by área and número. */
    public static List<Situacion> de(List<Producto> bienes) {
        List<Situacion> out = new ArrayList<>();
        for (Producto p : bienes)
            if (p.getFechaBaja() == null && ReporteParqueVehicularService.esVehiculo(p)) out.add(de(p));
        out.sort(Comparator.comparing((Situacion s) -> !s.pendiente())
            .thenComparing(s -> Objects.requireNonNullElse(s.vehiculo().getArea(), "~"))
            .thenComparing(s -> Objects.requireNonNullElse(s.vehiculo().getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO));
        return out;
    }

    /** The counts, as said at the top of the report and on Inicio. */
    public record Resumen(int vehiculos, int sinSeguro, int sinTarjeta, int sinTenencia, int sinVerificacion,
                          int sinPlacas, int descompuestos, int retenidos, int sinDatos) {}

    public static Resumen resumen(List<Situacion> s) {
        int seg = 0, tar = 0, ten = 0, ver = 0, pla = 0, des = 0, ret = 0, sin = 0;
        for (Situacion x : s) {
            if (x.seguro() == Tiene.NO) seg++;
            if (x.tarjeta() == Tiene.NO) tar++;
            if (x.tenencia() == Tiene.NO) ten++;
            if (x.verificacion() == Tiene.NO) ver++;
            if (x.sinPlacas()) pla++;
            if (x.descompuesto()) des++;
            if (x.retenido()) ret++;
            if (x.seguro() == Tiene.SIN_DATO && x.tarjeta() == Tiene.SIN_DATO && x.estatus().isEmpty()) sin++;
        }
        return new Resumen(s.size(), seg, tar, ten, ver, pla, des, ret, sin);
    }

    private static Tiene siNo(String v) {
        String t = v.trim().toUpperCase(ES).replace("Í", "I");
        if (t.startsWith("SI") || t.equals("S") || t.startsWith("VIGENTE") || t.startsWith("PAGAD")) return Tiene.SI;
        if (t.startsWith("NO") || t.equals("N") || t.startsWith("VENCID")) return Tiene.NO;
        return Tiene.SIN_DATO;
    }

    private static String grupo(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1).trim() : "";
    }

    private static boolean hay(String s) {
        return s != null && !s.isBlank();
    }
}
