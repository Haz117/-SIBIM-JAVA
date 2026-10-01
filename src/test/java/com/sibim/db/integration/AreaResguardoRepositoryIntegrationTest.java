package com.sibim.db.integration;

import com.sibim.db.DatabaseConfig;
import com.sibim.repository.AreaResguardoRepository;
import com.sibim.repository.AreaResguardoRepository.AreaResguardo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaResguardoRepositoryIntegrationTest extends IntegrationTestBase {

    private final AreaResguardoRepository repo = new AreaResguardoRepository();

    private static final String AREA_A = "Secretaría General";
    private static final String AREA_B = "Dirección de Obras";
    private static final byte[] PDF = "%PDF-1.4 resguardo firmado".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void vaciar() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM area_resguardos");
        }
    }

    @Test
    void save_guardaElPdfEnLaBase_yCualquierPcLoPuedeLeer() throws SQLException {
        repo.save(AREA_A, PDF, "resguardo.pdf", "Resguardo anual", LocalDate.of(2024, 1, 15));

        List<AreaResguardo> result = repo.findByArea(AREA_A);
        assertEquals(1, result.size());
        AreaResguardo r = result.get(0);
        assertEquals(AREA_A, r.area());
        assertTrue(r.tienePdf());
        assertNull(r.pdfUrl(), "ya no se guarda una ruta local");
        assertEquals("resguardo.pdf", r.pdfNombre());
        assertEquals("Resguardo anual", r.descripcion());
        assertEquals(LocalDate.of(2024, 1, 15), r.fecha());
        assertArrayEquals(PDF, repo.leerPdf(r.id()).orElseThrow());
    }

    @Test
    void filaAnteriorAV25_conservaSuRutaYNoTienePdf() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO area_resguardos (id, area, pdf_url) VALUES ('viejo', '" + AREA_A
                + "', 'C:/Users/x/.sibim/resguardos/a.pdf')");
        }
        AreaResguardo r = repo.findByArea(AREA_A).get(0);
        assertFalse(r.tienePdf());
        assertEquals("C:/Users/x/.sibim/resguardos/a.pdf", r.pdfUrl());
        assertTrue(repo.leerPdf("viejo").isEmpty());
    }

    @Test
    void findByArea_otraArea_retornaVacio() throws SQLException {
        repo.save(AREA_A, PDF, "a.pdf", null, LocalDate.now());
        assertTrue(repo.findByArea(AREA_B).isEmpty());
    }

    @Test
    void save_multiplesParaMismaArea_retornaTodas() throws SQLException {
        repo.save(AREA_A, PDF, "r1.pdf", "Primer resguardo", LocalDate.of(2023, 1, 1));
        repo.save(AREA_A, PDF, "r2.pdf", "Segundo resguardo", LocalDate.of(2024, 1, 1));
        assertEquals(2, repo.findByArea(AREA_A).size());
    }

    @Test
    void delete_eliminaRegistro() throws SQLException {
        String id = repo.save(AREA_A, PDF, "del.pdf", "Para eliminar", LocalDate.now());
        repo.delete(id);
        assertTrue(repo.findByArea(AREA_A).isEmpty());
    }

    @Test
    void save_pdfVacioODemasiadoGrande_seRechaza() {
        assertThrows(IllegalArgumentException.class, () -> repo.save(AREA_A, new byte[0], "x.pdf", null, null));
        assertThrows(IllegalArgumentException.class, () -> repo.save(AREA_A,
            new byte[AreaResguardoRepository.MAX_PDF_BYTES + 1], "x.pdf", null, null));
    }

    @Test
    void sinConexion_seGuardaEnLaPcYSeAbre() throws Exception {
        DatabaseConfig.setOfflineMode(true);
        try {
            String id = repo.save(AREA_A, PDF, "x.pdf", null, null);
            assertEquals(1, repo.findByArea(AREA_A).stream().filter(r -> r.id().equals(id)).count());
            assertArrayEquals(PDF, repo.leerPdf(id).orElseThrow());
        } finally {
            DatabaseConfig.setOfflineMode(false);
            limpiarColaLocal();
        }
    }

    /** The offline upload above waits in the PC's queue; other tests count it. */
    private static void limpiarColaLocal() throws Exception {
        var m = com.sibim.db.offline.OfflineStore.class.getDeclaredMethod("sharedConnection");
        m.setAccessible(true);
        try (java.sql.Statement st = ((java.sql.Connection) m.invoke(null)).createStatement()) {
            st.executeUpdate("DELETE FROM doc_outbox");
            st.executeUpdate("DELETE FROM doc_cache");
        }
    }

    @Test
    void findByArea_areaVacia_retornaVacio() throws SQLException {
        List<AreaResguardo> result = repo.findByArea("area-que-no-existe");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
