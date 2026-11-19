package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.AvanceAreasService;

import java.util.*;

/**
 * What the Organigrama's cards and table say of a secretaría besides its bienes, value and
 * resguardos (18-nov-2026, Patrimonio: «siento que es muy simple»): how many of its bienes have
 * the data the depreciation needs, how many have no resguardo in force, how its áreas go in their
 * three steps, and its dependencias with their bienes. Over the secretaría and every área under it.
 */
record ResumenSecretaria(int bienes, List<Producto> faltanDatos, List<Producto> sinResguardo,
                         int areas, int conCuenta, int entraron, int revisados, int bienesDeAreas,
                         List<Map.Entry<String, Integer>> dependencias) {

    /** Costo, fecha de adquisición and vida útil: what the book value is worked out from. */
    static boolean datosCompletos(Producto p) {
        return p.getPrecioCompra() != null && p.getPrecioCompra().signum() > 0
            && p.getFechaAdquisicion() != null && p.getVidaUtilAnios() != null;
    }

    static ResumenSecretaria de(String secretaria, List<Producto> suyos, List<String> dependencias,
                                Set<String> enResguardo, Map<String, AvanceAreasService.Avance> avance,
                                Map<String, List<Producto>> porArea) {
        List<Producto> vigentes = suyos.stream().filter(p -> p.getFechaBaja() == null).toList();
        List<Producto> faltan = vigentes.stream().filter(p -> !datosCompletos(p)).toList();
        List<Producto> sinRes = enResguardo == null ? List.of()
            : vigentes.stream().filter(p -> !enResguardo.contains(p.getId())).toList();
        int areas = 0, conCuenta = 0, entraron = 0, revisados = 0, bienesAreas = 0;
        List<String> todas = new ArrayList<>();
        todas.add(secretaria);
        todas.addAll(dependencias);
        for (String a : todas) {
            AvanceAreasService.Avance av = avance == null ? null : avance.get(a);
            if (av == null || av.bienes() == 0) continue;
            areas++;
            if (!av.sinCuenta()) conCuenta++;
            if (av.fase1()) entraron++;
            revisados += av.revisados();
            bienesAreas += av.bienes();
        }
        List<Map.Entry<String, Integer>> deps = dependencias.stream()
            .map(d -> Map.entry(d, porArea.getOrDefault(d, List.of()).size()))
            .filter(e -> e.getValue() > 0)
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .toList();
        return new ResumenSecretaria(vigentes.size(), faltan, sinRes, areas, conCuenta, entraron, revisados, bienesAreas, deps);
    }

    /** 0 to 100. */
    int pctCompletos() {
        return bienes == 0 ? 100 : (int) Math.round(100.0 * (bienes - faltanDatos.size()) / bienes);
    }

    int pctRevisado() {
        return bienesDeAreas == 0 ? 0 : (int) Math.round(100.0 * revisados / bienesDeAreas);
    }

    /** «Ninguna tiene cuenta», «2 de 5 con cuenta · 1 entró», «5 entraron · 40 % revisado». */
    String avanceEnPalabras() {
        if (areas == 0) return "Sin áreas con bienes";
        if (conCuenta == 0 && entraron == 0) return areas == 1 ? "Sin cuenta todavía" : "Ninguna de sus " + areas + " áreas tiene cuenta";
        if (entraron == 0) return conCuenta + " de " + areas + " con cuenta, sin entrar";
        return entraron + " de " + areas + (entraron == 1 ? " entró" : " entraron") + " · " + pctRevisado() + " % revisado";
    }

    /** For the table's narrow column: «0 de 10 con cuenta», «3 de 7 entraron · 40 %». */
    String avanceCorto() {
        if (areas == 0) return "—";
        if (entraron == 0) return conCuenta + " de " + areas + " con cuenta";
        return entraron + " de " + areas + (entraron == 1 ? " entró" : " entraron") + " · " + pctRevisado() + " %";
    }
}
