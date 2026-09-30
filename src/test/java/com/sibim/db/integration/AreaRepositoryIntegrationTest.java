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
    void laFirma_cambiaAlAgregarOEditarUnArea() throws Exception {
        String antes = repo.firma();
        assertEquals(antes, repo.firma(), "sin cambios, la firma es la misma");
        repo.guardar(new AreaCatalog.Entrada("Dirección de Prueba", AreaCatalog.Grupo.DIRECCION,
            "Secretaría de Planeación", "DPRU"));
        String despuesDeAgregar = repo.firma();
        assertNotEquals(antes, despuesDeAgregar);
        Thread.sleep(5);
        repo.guardar(new AreaCatalog.Entrada("Dirección de Prueba", AreaCatalog.Grupo.DIRECCION,
            "Secretaría de Planeación", "DPRX"));
        assertNotEquals(despuesDeAgregar, repo.firma(), "editar también cambia la firma");
    }

    private static final String TICS = "Dirección de Tecnologías de la Información";

    private void bien(String id, String codigo, String area, boolean baja) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO categories (id, nombre, color) VALUES ('cat-a', 'Cat', '#3B82F6') "
                + "ON CONFLICT DO NOTHING");
            st.execute("INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad"
                + (baja ? ", fecha_baja, motivo_baja" : "") + ") VALUES ('" + id + "', 'Bien', '" + codigo
                + "', 'cat-a', '" + area + "', 'pza'" + (baja ? ", CURRENT_DATE, 'x'" : "") + ")");
        }
    }

    private String codigo(String id) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT codigo FROM products WHERE id = '" + id + "'")) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void renumerar_mueveLosBienesActivosAlNuevoPrefijoConservandoElNumero() throws Exception {
        bien("b1", "TICS/01", TICS, false);
        bien("b2", "TICS/07", TICS, false);
        bien("b3", "TICS/02", TICS, true);                 // dado de baja: no se toca
        bien("b4", "TICS/03", "Dirección de Cultura", false);  // otra área: no se toca
        assertEquals(2, repo.contarBienesConPrefijo(TICS, "TICS"));

        int n = repo.guardar(new AreaCatalog.Entrada(TICS, AreaCatalog.Grupo.DIRECCION,
            "Secretaría de Planeación", "TI"), "TICS");

        assertEquals(2, n);
        assertEquals("TI/01", codigo("b1"));
        assertEquals("TI/07", codigo("b2"));
        assertEquals("TICS/02", codigo("b3"));
        assertEquals("TICS/03", codigo("b4"));
    }

    @Test
    void renumerar_conCodigoOcupado_noGuardaNada() throws Exception {
        bien("b1", "TICS/01", TICS, false);
        bien("b2", "TI/01", "Dirección de Cultura", false);   // TI/01 ya lo usa otro bien activo

        assertThrows(SQLException.class, () -> repo.guardar(new AreaCatalog.Entrada(TICS,
            AreaCatalog.Grupo.DIRECCION, "Secretaría de Planeación", "TI"), "TICS"));

        assertEquals("TICS/01", codigo("b1"));
        assertEquals("TICS", repo.cargar().buscar(TICS).orElseThrow().prefijo(), "el prefijo tampoco cambia");
    }

    @Test
    void laBase_rechazaUnPrefijoRepetido() {
        assertThrows(SQLException.class, () -> repo.guardar(new AreaCatalog.Entrada(
            "Dirección de Prueba", AreaCatalog.Grupo.AUTONOMO, null, "TICS")));
    }
}
