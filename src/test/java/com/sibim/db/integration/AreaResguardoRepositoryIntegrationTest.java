package com.sibim.db.integration;

import com.sibim.repository.AreaResguardoRepository;
import com.sibim.repository.AreaResguardoRepository.AreaResguardo;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaResguardoRepositoryIntegrationTest extends IntegrationTestBase {

    private final AreaResguardoRepository repo = new AreaResguardoRepository();

    private static final String AREA_A = "Secretaría General";
    private static final String AREA_B = "Dirección de Obras";

    @Test
    void save_y_findByArea_retornaRegistro() throws SQLException {
        repo.save(AREA_A, "https://example.com/resguardo.pdf", "Resguardo anual", LocalDate.of(2024, 1, 15));

        List<AreaResguardo> result = repo.findByArea(AREA_A);
        assertEquals(1, result.size(), "debe retornar exactamente el registro guardado");
        assertEquals(AREA_A, result.get(0).area());
        assertEquals("https://example.com/resguardo.pdf", result.get(0).pdfUrl());
        assertEquals("Resguardo anual", result.get(0).descripcion());
        assertEquals(LocalDate.of(2024, 1, 15), result.get(0).fecha());
    }

    @Test
    void findByArea_otraArea_retornaVacio() throws SQLException {
        repo.save(AREA_A, "https://example.com/a.pdf", null, LocalDate.now());

        List<AreaResguardo> result = repo.findByArea(AREA_B);
        assertTrue(result.isEmpty(), "findByArea para área distinta debe retornar lista vacía");
    }

    @Test
    void save_multiplesParaMismaArea_retornaTodas() throws SQLException {
        repo.save(AREA_A, "https://example.com/r1.pdf", "Primer resguardo", LocalDate.of(2023, 1, 1));
        repo.save(AREA_A, "https://example.com/r2.pdf", "Segundo resguardo", LocalDate.of(2024, 1, 1));

        List<AreaResguardo> result = repo.findByArea(AREA_A);
        assertEquals(2, result.size(), "ambos registros del área deben aparecer");
    }

    @Test
    void delete_eliminaRegistro() throws SQLException {
        repo.save(AREA_A, "https://example.com/del.pdf", "Para eliminar", LocalDate.now());
        List<AreaResguardo> antes = repo.findByArea(AREA_A);
        assertEquals(1, antes.size());

        repo.delete(antes.get(0).id());

        List<AreaResguardo> despues = repo.findByArea(AREA_A);
        assertTrue(despues.isEmpty(), "el registro eliminado no debe aparecer en findByArea");
    }

    @Test
    void findByArea_areaVacia_retornaVacio() throws SQLException {
        List<AreaResguardo> result = repo.findByArea("area-que-no-existe");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
