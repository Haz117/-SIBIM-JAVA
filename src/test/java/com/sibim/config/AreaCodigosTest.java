package com.sibim.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
}
