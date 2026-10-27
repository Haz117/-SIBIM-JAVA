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

    /** Undoing a transfer: the bien gets its own número back while it is free. */
    @Test
    void codigoPreferido_elSuyoSiSigueLibre_siNoElSiguiente() {
        String tics = "Dirección de Tecnologías de la Información";
        List<String> activos = List.of("TICS/01", "TICS/02", "TICS/04");
        assertEquals("TICS/07", AreaCodigos.codigoPreferido(tics, "TICS/07", activos));
        assertEquals("TICS/03", AreaCodigos.codigoPreferido(tics, "tics/04", activos), "ya lo tiene otro bien");
        assertEquals("TICS/03", AreaCodigos.codigoPreferido(tics, null, activos));
        assertEquals("EC-002", AreaCodigos.codigoPreferido(tics, "EC-002", activos), "un código heredado también regresa");
    }

    @Test
    void normalizar_rechazaLoQueNoEsPrefijoYNumeroDeEsaArea() {
        String tics = "Dirección de Tecnologías de la Información";
        for (String codigo : new String[]{ "TICS/00", "TICS/", "TICS-05", "TICS/5A", "RMP/05", "TICSX/05", "05", "", null })
            assertTrue(AreaCodigos.normalizar(tics, codigo).isEmpty(), "debería rechazarse: " + codigo);
    }
}
