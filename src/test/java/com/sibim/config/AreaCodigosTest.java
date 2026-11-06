package com.sibim.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AreaCodigosTest {

    @Test
    void todasLasAreasReales_tienenPrefijo() {
        for (String area : Areas.getAllAreaNames()) {
            assertTrue(AreaCodigos.tienePrefijo(area), "Falta prefijo para el área: " + area);
        }
    }

    @Test
    void noHayPrefijosDuplicados() {
        Map<String, String> vistos = new HashMap<>();
        for (String area : Areas.getAllAreaNames()) {
            String prefijo = AreaCodigos.prefijo(area);
            String previo = vistos.put(prefijo, area);
            assertNull(previo, "Prefijo duplicado \"" + prefijo + "\" entre \"" + previo + "\" y \"" + area + "\"");
        }
    }

    @Test
    void cantidadDeAreas_coincideConElSistemaReal() {
        Set<String> areas = Areas.getAllAreaNames();
        assertEquals(areas.size(), (int) areas.stream().filter(AreaCodigos::tienePrefijo).count());
    }

    @Test
    void areaDesconocida_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> AreaCodigos.prefijo("Área que no existe"));
    }

    @Test
    void recursosMaterialesYPatrimonio_tienePrefijoRMP() {
        assertEquals("RMP", AreaCodigos.prefijo("Recursos Materiales y Patrimonio"));
    }

    @Test
    void tecnologiasDeLaInformacion_tienePrefijoTICS() {
        assertEquals("TICS", AreaCodigos.prefijo("Dirección de Tecnologías de la Información"));
    }

    @Test
    void normalizar_loEscribeComoLaApp() {
        String tics = "Dirección de Tecnologías de la Información";
        assertEquals("TICS/05", AreaCodigos.normalizar(tics, "TICS/05").orElseThrow());
        assertEquals("TICS/05", AreaCodigos.normalizar(tics, " tics / 5 ").orElseThrow());
        assertEquals("TICS/07", AreaCodigos.normalizar(tics, "TICS/007").orElseThrow());
        assertEquals("TICS/123", AreaCodigos.normalizar(tics, "TICS/123").orElseThrow());
    }

    /** Undoing a transfer or reactivating: the bien gets its own número back unless an active one has it. */
    @Test
    void codigoPreferido_elSuyoSiNingunActivoLoTiene_siNoElSiguienteNuncaUsado() {
        String tics = "Dirección de Tecnologías de la Información";
        List<String> activos = List.of("TICS/01", "TICS/02", "TICS/04");
        List<String> usados = List.of("TICS/01", "TICS/02", "TICS/03", "TICS/04", "TICS/07");
        assertEquals("TICS/07", AreaCodigos.codigoPreferido(tics, "TICS/07", activos, usados));
        assertEquals("TICS/08", AreaCodigos.codigoPreferido(tics, "tics/04", activos, usados), "ya lo tiene otro bien");
        assertEquals("TICS/08", AreaCodigos.codigoPreferido(tics, null, activos, usados));
        assertEquals("EC-002", AreaCodigos.codigoPreferido(tics, "EC-002", activos, usados), "un código heredado también regresa");
    }

    /** 5-nov-2026: a número that was used is never handed out again, gaps included. */
    @Test
    void siguienteCodigo_esElQueSigueAlMasAlto_sinRellenarHuecos() {
        String tics = "Dirección de Tecnologías de la Información";
        assertEquals("TICS/01", AreaCodigos.siguienteCodigo(tics, List.of()));
        assertEquals("TICS/05", AreaCodigos.siguienteCodigo(tics, List.of("TICS/01", "TICS/04")), "el 02 y el 03 no se rellenan");
        assertEquals("TICS/13", AreaCodigos.siguienteCodigo(tics, List.of("tics/12", "RMP/40", "EC-002", "TICS/X")),
            "solo cuentan los de su prefijo y con número");
        assertEquals("TICS/100", AreaCodigos.siguienteCodigo(tics, List.of("TICS/99")));
    }

    @Test
    void normalizar_rechazaLoQueNoEsPrefijoYNumeroDeEsaArea() {
        String tics = "Dirección de Tecnologías de la Información";
        for (String codigo : new String[]{ "TICS/00", "TICS/", "TICS-05", "TICS/5A", "RMP/05", "TICSX/05", "05", "", null })
            assertTrue(AreaCodigos.normalizar(tics, codigo).isEmpty(), "debería rechazarse: " + codigo);
    }
}
