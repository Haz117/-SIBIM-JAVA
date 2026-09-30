package com.sibim.db.integration;

import com.sibim.config.AreaCatalog;
import com.sibim.repository.AreaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class AreaRepositoryIntegrationTest extends IntegrationTestBase {

    private final AreaRepository repo = new AreaRepository();

    @AfterEach
    void deshacer() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM areas WHERE nombre = 'Dirección de Prueba'");
            st.execute("UPDATE areas SET prefijo = 'TICS' WHERE nombre = 'Dirección de Tecnologías de la Información'");
        }
    }

    @Test
    void laMigracion_siembraElMismoOrganigramaQueElCodigo() throws Exception {
        assertEquals(AreaCatalog.PREDETERMINADO.entradas(), repo.cargar().entradas());
    }

    @Test
    void guardar_agregaYActualizaSinTocarElNombre() throws Exception {
        repo.guardar(new AreaCatalog.Entrada("Dirección de Prueba", AreaCatalog.Grupo.DIRECCION,
            "Secretaría de Planeación", "DPRU"));
        repo.guardar(new AreaCatalog.Entrada("Dirección de Tecnologías de la Información",
            AreaCatalog.Grupo.DIRECCION, "Secretaría de Planeación", "TI"));

        AreaCatalog c = repo.cargar();
        assertEquals(45, c.entradas().size());
        assertEquals("Dirección de Prueba", c.entradas().get(44).nombre(), "una área nueva va al final");
        assertEquals("TI", c.buscar("Dirección de Tecnologías de la Información").orElseThrow().prefijo());
    }

    @Test
    void laBase_rechazaUnPrefijoRepetido() {
        assertThrows(SQLException.class, () -> repo.guardar(new AreaCatalog.Entrada(
            "Dirección de Prueba", AreaCatalog.Grupo.AUTONOMO, null, "TICS")));
    }
}
