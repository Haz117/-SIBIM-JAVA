package com.sibim.service;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Fotos repetidas: which shared pictures are worth a look. */
class FotosRepetidasTest {

    private static Producto bien(String id, String nombre, String area, String resguardante) {
        Producto p = new Producto();
        p.setId(id);
        p.setCodigo(id);
        p.setNombre(nombre);
        p.setArea(area);
        p.setResguardante(resguardante);
        return p;
    }

    @Test
    void nombresQueDicenLoMismoSonParecidos() {
        assertTrue(FotosRepetidas.parecidos("SWITCH ADMINISTRABLE 8 PUERTOS", "REDES SWITCH ADMINISTRABLE 8 PUERTOS"));
        assertTrue(FotosRepetidas.parecidos("Silla secretarial", "SILLA SECRETARIAL"));
        assertFalse(FotosRepetidas.parecidos("VENTILADOR DE PEDESTAL", "VENTILADOR INDUSTRIAL DE PISO NEGRO"), "«DE» no los hace parecidos");
        assertFalse(FotosRepetidas.parecidos("ARCHIVERO METALICO 4 GAVETAS", "ESCRITORIO SECRETARIAL"));
    }

    @Test
    void unLoteConUnaFotoNoSale_yLaFotoDeOtroBienSalePrimero() {
        Map<String, Producto> bienes = new LinkedHashMap<>();
        // 3 chairs of one person: one photo for the lot is fine.
        bienes.put("s1", bien("s1", "SILLA SECRETARIAL", "Tesorería", "ANA"));
        bienes.put("s2", bien("s2", "SILLA SECRETARIAL", "Tesorería", "ANA"));
        bienes.put("s3", bien("s3", "SILLA SECRETARIAL", "Tesorería", "ANA"));
        // The same chairs in two áreas.
        bienes.put("c1", bien("c1", "SILLA DE VISITA", "Presidencia", "LUIS"));
        bienes.put("c2", bien("c2", "SILLA DE VISITA", "Comunicación", "PEDRO"));
        // A fan with the picture of another bien.
        bienes.put("v1", bien("v1", "VENTILADOR DE PEDESTAL", "Rastro", "JUAN"));
        bienes.put("v2", bien("v2", "MODULO DE RECEPCION", "Rastro", "JUAN"));
        Map<String, List<String>> fotos = new LinkedHashMap<>();
        fotos.put("s1", List.of("u-silla")); fotos.put("s2", List.of("u-silla")); fotos.put("s3", List.of("u-silla2"));
        fotos.put("c1", List.of("u-visita")); fotos.put("c2", List.of("u-visita"));
        fotos.put("v1", List.of("u-vent")); fotos.put("v2", List.of("u-otro", "u-vent2"));
        // u-silla2 is the same file as u-silla under another name; u-vent2 the same as u-vent.
        Map<String, String> huellas = Map.of("u-silla", "A", "u-silla2", "A", "u-visita", "B", "u-vent", "C", "u-vent2", "C", "u-otro", "D");

        var grupos = FotosRepetidas.agrupar(fotos, huellas, bienes);

        assertEquals(2, grupos.size(), "las sillas del mismo lote no salen");
        assertEquals("C", grupos.get(0).huella(), "nombres distintos primero");
        assertEquals("B", grupos.get(1).huella());
    }

    @Test
    void laFotoSeBuscaEnElRespaldoConSuCarpeta() {
        Path r = Path.of("C:/respaldos/fotos");
        String base = "https://x.supabase.co/storage/v1/object/public/sibim-fotos/";
        assertEquals(r.resolve("abc_0.jpg"), FotosRepetidas.enRespaldo(base + "abc_0.jpg", r));
        assertEquals(r.resolve("bienes").resolve("mi foto.jpg"), FotosRepetidas.enRespaldo(base + "bienes/mi%20foto.jpg?t=1", r));
        assertNull(FotosRepetidas.enRespaldo(base + "../fuera.jpg", r), "no sale de la carpeta");
        assertNull(FotosRepetidas.enRespaldo("https://otro.sitio/foto.jpg", r));
        assertNull(FotosRepetidas.enRespaldo(base + "abc.jpg", null));
    }
}
