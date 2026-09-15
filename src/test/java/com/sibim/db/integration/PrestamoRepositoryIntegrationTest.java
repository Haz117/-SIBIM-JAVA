package com.sibim.db.integration;

import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.PrestamoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link PrestamoRepository} against an embedded PostgreSQL.
 *
 * prestamos.producto_id is a NOT NULL FK to products(id) ON DELETE RESTRICT,
 * so a product row must exist before inserting a prestamo.
 */
class PrestamoRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String CAT_ID = "cat-prs-test";
    private static final String AREA   = "Secretaría de Finanzas";

    private final PrestamoRepository repo        = new PrestamoRepository();
    private final ProductoRepository productoRepo = new ProductoRepository();

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void insertCategoria(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#22C55E')")) {
            ps.setString(1, id);
            ps.setString(2, "Categoría Préstamo Test");
            ps.executeUpdate();
        }
    }

    private Producto buildProducto(String id) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Laptop de Préstamo");
        p.setCodigo("PRS-PRD-" + id.substring(0, 6));
        p.setCategoriaId(CAT_ID);
        p.setPrecioCompra(BigDecimal.valueOf(15000));
        p.setPrecioVenta(BigDecimal.valueOf(18000));
        p.setStockActual(1);
        p.setStockMinimo(1);
        p.setStockMaximo(3);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(AREA);
        p.setResguardante("Sin resguardante");
        return p;
    }

    private Prestamo buildPrestamo(String productoId, LocalDate devolucionPrevista) {
        Prestamo p = new Prestamo();
        p.setProductoId(productoId);
        p.setProductoNombre("Laptop de Préstamo");
        p.setProductoCodigo("PRS-001");
        p.setAreaOrigen(AREA);
        p.setAreaDestino("Presidencia Municipal");
        p.setResponsableNombre("Juan Martínez");
        p.setResponsableCargo("Presidente");
        p.setMotivo("Uso temporal para evento");
        p.setFechaPrestamo(LocalDate.now());
        p.setFechaDevolucionPrevista(devolucionPrevista);
        return p;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * A saved prestamo must be retrievable via findAll() with estado ACTIVO
     * and the correct responsableNombre.
     */
    @Test
    void save_nuevoPrestamo_persisteEnDB() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        Prestamo saved = repo.save(buildPrestamo(pid, LocalDate.now().plusDays(7)));

        assertNotNull(saved.getId(), "save debe asignar un id");
        assertNotNull(saved.getNumero(), "save debe asignar un número de folio");
        assertTrue(saved.getNumero().startsWith("PRS-"), "el folio debe empezar con PRS-");

        List<Prestamo> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals("Juan Martínez", all.get(0).getResponsableNombre());
        assertEquals(Prestamo.ESTADO_ACTIVO, all.get(0).getEstado());
    }

    /**
     * devolver() must set estado = DEVUELTO and persist the real return date.
     */
    @Test
    void devolver_cambiaEstado_yGuardaFecha() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        Prestamo saved = repo.save(buildPrestamo(pid, LocalDate.now().plusDays(5)));
        LocalDate hoy = LocalDate.now();
        repo.devolver(saved.getId(), hoy);

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT estado, fecha_devolucion_real FROM prestamos WHERE id = ?")) {
            ps.setString(1, saved.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(Prestamo.ESTADO_DEVUELTO, rs.getString("estado"));
                assertEquals(hoy, rs.getDate("fecha_devolucion_real").toLocalDate());
            }
        }
    }

    /**
     * updateVencidos() must flip estado to VENCIDO for active prestamos whose
     * fecha_devolucion_prevista is in the past.
     */
    @Test
    void updateVencidos_marcaPrestamoVencido() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        Prestamo saved = repo.save(buildPrestamo(pid, LocalDate.now().plusDays(30)));

        // Backdate the devolución to yesterday so updateVencidos picks it up
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE prestamos SET fecha_devolucion_prevista = ? WHERE id = ?")) {
            ps.setDate(1, Date.valueOf(LocalDate.now().minusDays(1)));
            ps.setString(2, saved.getId());
            ps.executeUpdate();
        }

        int updated = repo.updateVencidos();
        assertEquals(1, updated, "debe marcar exactamente 1 préstamo como vencido");

        List<Prestamo> vencidos = repo.findVencidos();
        assertEquals(1, vencidos.size());
        assertEquals(saved.getId(), vencidos.get(0).getId());
    }

    /**
     * findActivos() must not return prestamos in estado DEVUELTO.
     */
    @Test
    void findActivos_noRetornaDevueltos() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid1 = UUID.randomUUID().toString();
        String pid2 = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid1));
        productoRepo.saveOnline(buildProducto(pid2));

        Prestamo activo   = repo.save(buildPrestamo(pid1, LocalDate.now().plusDays(10)));
        Prestamo devuelto = repo.save(buildPrestamo(pid2, LocalDate.now().plusDays(10)));
        repo.devolver(devuelto.getId(), LocalDate.now());

        List<Prestamo> activos = repo.findActivos();
        assertTrue(activos.stream().noneMatch(p -> p.getId().equals(devuelto.getId())),
            "findActivos no debe incluir préstamos devueltos");
        assertTrue(activos.stream().anyMatch(p -> p.getId().equals(activo.getId())),
            "findActivos debe incluir el préstamo activo");
    }

    /**
     * Two saves must produce distinct sequential folio numbers.
     */
    @Test
    void save_dosPrestamosMismoProducto_numerosDistintos() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        Prestamo p1 = repo.save(buildPrestamo(pid, LocalDate.now().plusDays(3)));
        Prestamo p2 = repo.save(buildPrestamo(pid, LocalDate.now().plusDays(5)));

        assertNotEquals(p1.getNumero(), p2.getNumero(),
            "cada préstamo debe tener un folio único");
    }

    /**
     * findByProductoId() must return only prestamos for the requested product.
     */
    @Test
    void findByProductoId_soloPrestamosDeeseProducto() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid1 = UUID.randomUUID().toString();
        String pid2 = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid1));
        productoRepo.saveOnline(buildProducto(pid2));

        repo.save(buildPrestamo(pid1, LocalDate.now().plusDays(7)));
        repo.save(buildPrestamo(pid1, LocalDate.now().plusDays(14)));
        repo.save(buildPrestamo(pid2, LocalDate.now().plusDays(7)));

        List<Prestamo> result = repo.findByProductoId(pid1);
        assertEquals(2, result.size(), "debe retornar solo los 2 préstamos del producto 1");
        assertTrue(result.stream().allMatch(p -> pid1.equals(p.getProductoId())));
    }
}
