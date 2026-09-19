package com.sibim.db.integration;

import com.sibim.repository.ProductoMantenimientoRepository;
import com.sibim.repository.ProductoMantenimientoRepository.Alerta;
import com.sibim.repository.ProductoMantenimientoRepository.AlertaGlobal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductoMantenimientoRepositoryIntegrationTest extends IntegrationTestBase {

    private final ProductoMantenimientoRepository repo = new ProductoMantenimientoRepository();

    private static final String CAT_ID  = "cat-mant-test";
    private static final String PROD_ID = UUID.randomUUID().toString();
    private static final String PROD_2  = UUID.randomUUID().toString();

    @BeforeEach
    void insertarProductos() throws SQLException {
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#3B82F6')")) {
                ps.setString(1, CAT_ID);
                ps.setString(2, "Categoría Mantenimiento Test");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                for (String[] row : new String[][]{
                        {PROD_ID, "Producto Mantenimiento 1", "MNT-001"},
                        {PROD_2,  "Producto Mantenimiento 2", "MNT-002"}}) {
                    ps.setString(1, row[0]);
                    ps.setString(2, row[1]);
                    ps.setString(3, row[2]);
                    ps.setString(4, CAT_ID);
                    ps.setString(5, "Área Test");
                    ps.setString(6, "pieza");
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    void agregar_y_findByProducto_retornaAlerta() throws Exception {
        repo.agregar(PROD_ID, "Cambiar aceite", LocalDate.of(2026, 12, 1));

        List<Alerta> result = repo.findByProducto(PROD_ID);

        assertEquals(1, result.size());
        assertEquals("Cambiar aceite", result.get(0).descripcion());
        assertEquals(LocalDate.of(2026, 12, 1), result.get(0).fecha());
        assertFalse(result.get(0).completada());
        assertNotNull(result.get(0).id());
    }

    @Test
    void findByProducto_ordenaPorFechaAscendente() throws Exception {
        repo.agregar(PROD_ID, "Revisión tardía", LocalDate.of(2027, 1, 1));
        repo.agregar(PROD_ID, "Revisión próxima", LocalDate.of(2026, 3, 1));

        List<Alerta> result = repo.findByProducto(PROD_ID);

        assertEquals(2, result.size());
        assertEquals("Revisión próxima", result.get(0).descripcion());
        assertEquals("Revisión tardía", result.get(1).descripcion());
    }

    @Test
    void findByProducto_soloDevuelveDelProductoPedido() throws Exception {
        repo.agregar(PROD_ID, "Alerta producto 1", LocalDate.of(2026, 6, 1));
        repo.agregar(PROD_2,  "Alerta producto 2", LocalDate.of(2026, 6, 1));

        assertEquals(1, repo.findByProducto(PROD_ID).size());
        assertEquals(1, repo.findByProducto(PROD_2).size());
    }

    @Test
    void findByProducto_sinAlertas_retornaVacio() {
        assertTrue(repo.findByProducto(PROD_ID).isEmpty());
    }

    @Test
    void marcarCompletada_actualizaFlag() throws Exception {
        repo.agregar(PROD_ID, "Calibración", LocalDate.of(2026, 5, 1));
        String id = repo.findByProducto(PROD_ID).get(0).id();

        repo.marcarCompletada(id);

        assertTrue(repo.findByProducto(PROD_ID).get(0).completada());
    }

    @Test
    void eliminar_quitaLaAlerta() throws Exception {
        repo.agregar(PROD_ID, "Alerta a borrar", LocalDate.of(2026, 5, 1));
        String id = repo.findByProducto(PROD_ID).get(0).id();

        repo.eliminar(id);

        assertTrue(repo.findByProducto(PROD_ID).isEmpty());
    }

    @Test
    void findProximas_incluyeVencidasYDentroDelRango_excluyeCompletadasYLejanas() throws Exception {
        repo.agregar(PROD_ID, "Vencida",       LocalDate.now().minusDays(5));
        repo.agregar(PROD_ID, "Dentro rango",  LocalDate.now().plusDays(10));
        repo.agregar(PROD_2,  "Fuera de rango", LocalDate.now().plusDays(90));
        repo.agregar(PROD_2,  "Completada",     LocalDate.now().minusDays(1));
        String completadaId = repo.findByProducto(PROD_2).stream()
            .filter(a -> a.descripcion().equals("Completada")).findFirst().orElseThrow().id();
        repo.marcarCompletada(completadaId);

        List<AlertaGlobal> proximas = repo.findProximas(30);

        assertEquals(2, proximas.size());
        assertTrue(proximas.stream().anyMatch(a -> a.descripcion().equals("Vencida")));
        assertTrue(proximas.stream().anyMatch(a -> a.descripcion().equals("Dentro rango")));
    }

    @Test
    void findProximas_sinAlertas_retornaVacio() {
        assertTrue(repo.findProximas(365).isEmpty());
    }
}
