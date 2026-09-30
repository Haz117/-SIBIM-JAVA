package com.sibim.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaCatalogTest {

    private static final AreaCatalog BASE = AreaCatalog.PREDETERMINADO;

    @AfterEach
    void restaurar() { Areas.usar(AreaCatalog.PREDETERMINADO); }

    @Test
    void predeterminado_tieneLas44AreasDeIxmiquilpan() {
        assertEquals(44, BASE.entradas().size());
        assertEquals(Areas.PRESIDENCIA, BASE.presidencia());
        assertEquals(7, BASE.secretarias().size());
        assertEquals(7, BASE.autonomos().size());
        assertEquals("TICS", BASE.buscar("Dirección de Tecnologías de la Información").orElseThrow().prefijo());
    }

    /** V24 seeds the table from this same list: if one changes without the other,
     *  a fresh database and the offline fallback would disagree. */
    @Test
    void semillaDeV24_coincideConElPredeterminado() throws Exception {
        String sql;
        try (var in = getClass().getResourceAsStream("/db/migration/V24__areas.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (AreaCatalog.Entrada e : BASE.entradas()) {
            String padre = e.padre() == null ? "NULL" : "'" + e.padre() + "'";
            String fila = "('" + e.nombre() + "', '" + e.grupo().name() + "', " + padre + ", '" + e.prefijo() + "'";
            assertTrue(sql.contains(fila), "V24 no siembra: " + fila);
        }
    }

    @Test
    void con_agregaUnaDireccionYAreasLaVe() {
        AreaCatalog nuevo = BASE.con(new AreaCatalog.Entrada(
            "Dirección de Prueba", AreaCatalog.Grupo.DIRECCION, "Secretaría de Planeación", "DPRU"));
        Areas.usar(nuevo);

        assertTrue(Areas.getAllAreaNames().contains("Dirección de Prueba"));
        assertTrue(Areas.getDireccionesDeSecretaria("Secretaría de Planeación").contains("Dirección de Prueba"));
        assertEquals("DPRU", AreaCodigos.prefijo("Dirección de Prueba"));
        assertEquals("DPRU/01", AreaCodigos.siguienteCodigo("Dirección de Prueba", List.of()));
    }

    @Test
    void con_cambiaElPrefijoDeUnAreaExistente() {
        AreaCatalog nuevo = BASE.con(new AreaCatalog.Entrada(
            "Dirección de Tecnologías de la Información", AreaCatalog.Grupo.DIRECCION,
            "Secretaría de Planeación", "TI"));
        assertEquals(44, nuevo.entradas().size());
        assertEquals("TI", nuevo.buscar("Dirección de Tecnologías de la Información").orElseThrow().prefijo());
    }

    @Test
    void prefijoRepetido_seRechaza() {
        var ex = assertThrows(IllegalArgumentException.class, () -> BASE.con(new AreaCatalog.Entrada(
            "Otra área", AreaCatalog.Grupo.AUTONOMO, null, "TICS")));
        assertTrue(ex.getMessage().contains("TICS"));
    }

    @Test
    void prefijoInvalido_seRechaza() {
        assertThrows(IllegalArgumentException.class, () -> BASE.con(new AreaCatalog.Entrada(
            "Otra área", AreaCatalog.Grupo.AUTONOMO, null, "ti/1")));
        assertThrows(IllegalArgumentException.class, () -> BASE.con(new AreaCatalog.Entrada(
            "Otra área", AreaCatalog.Grupo.AUTONOMO, null, "DEMASIADOLARGO")));
    }

    @Test
    void direccionSinPadreValido_seRechaza() {
        assertThrows(IllegalArgumentException.class, () -> BASE.con(new AreaCatalog.Entrada(
            "Dirección huérfana", AreaCatalog.Grupo.DIRECCION, null, "DHUE")));
        assertThrows(IllegalArgumentException.class, () -> BASE.con(new AreaCatalog.Entrada(
            "Dirección bajo autónomo", AreaCatalog.Grupo.DIRECCION, "Control Canino", "DBA")));
    }

    @Test
    void secretariaConDirecciones_noPuedeVolverseAutonoma() {
        // Its direcciones would be left hanging from a non-secretaría.
        assertThrows(IllegalArgumentException.class, () -> BASE.con(new AreaCatalog.Entrada(
            "Secretaría de Planeación", AreaCatalog.Grupo.AUTONOMO, null, "SPLAN")));
    }
}
