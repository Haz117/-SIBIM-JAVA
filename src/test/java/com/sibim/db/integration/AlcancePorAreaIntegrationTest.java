package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What an área account reads is limited to its áreas in EVERY query, not only
 * in the listings: the maintenance alerts had no área filter at all, and the
 * statistics treated "no áreas" as "every área".
 */
class AlcancePorAreaIntegrationTest extends IntegrationTestBase {

    private static final String CATASTRO  = "Dirección de Catastro";
    private static final String TESORERIA = "Tesorería Municipal";
    private static final String CAT_ID    = "cat-alcance";

    private final ProductoRepository repo = new ProductoRepository();
    private Usuario admin;

    @BeforeEach
    void dosBienesEnDosAreas() throws SQLException {
        admin = SessionManager.getCurrentUser();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO categories (id, nombre, color) VALUES (?, 'Mobiliario de prueba', '#3B82F6')")) {
            ps.setString(1, CAT_ID);
            ps.executeUpdate();
        }
        repo.saveOnline(bien("bien-catastro", "Estación total", "CAT-001", CATASTRO));
        repo.saveOnline(bien("bien-tesoreria", "Caja fuerte", "TES-001", TESORERIA));
    }

    @AfterEach
    void volverAPatrimonio() {
        SessionManager.setCurrentUser(admin);
    }

    @Test
    void proximasRevisiones_patrimonioVeLasDeTodasLasAreas() throws SQLException {
        assertEquals(2, repo.findProximasRevisiones(30).size());
    }

    @Test
    void proximasRevisiones_unaDireccionSoloVeLasDeSuArea() throws SQLException {
        SessionManager.setCurrentUser(cuenta(Rol.DIRECCION, CATASTRO));

        List<Producto> revisiones = repo.findProximasRevisiones(30);

        assertEquals(1, revisiones.size());
        assertEquals("bien-catastro", revisiones.get(0).getId());
    }

    @Test
    void cuentaSinArea_noVeNadaEnEstadisticasNiListados() throws SQLException {
        SessionManager.setCurrentUser(cuenta(Rol.DIRECCION, null));

        assertEquals(0, repo.getStats().total());
        assertEquals(0, repo.countAll());
        assertTrue(repo.getValorPorCategoria().isEmpty());
        assertTrue(repo.getValorPorCategoria(5, null, null).isEmpty());
        assertTrue(repo.countByArea(5).isEmpty());
        assertTrue(repo.findVencidosProximos(30).isEmpty());
        assertTrue(repo.findByDateRange(null, null).isEmpty());
        assertTrue(repo.findProximasRevisiones(30).isEmpty());
    }

    @Test
    void unaDireccion_susEstadisticasSonLasDeSuArea() throws SQLException {
        SessionManager.setCurrentUser(cuenta(Rol.DIRECCION, TESORERIA));

        assertEquals(1, repo.getStats().total());
        assertEquals(1, repo.countAll());
        assertEquals(List.of(TESORERIA), List.copyOf(repo.countByArea(5).keySet()));
        assertEquals(1, repo.findVencidosProximos(30).size());
    }

    @Test
    void bienNuevo_tomaSusFechasDelServidor() throws SQLException {
        Producto guardado = repo.saveOnline(bien("bien-fechas", "Archivero", "CAT-002", CATASTRO));

        assertNotNull(guardado.getCreadoEn(), "el alta devuelve la fecha que puso el servidor");
        assertNotNull(guardado.getActualizadoEn());
        assertEquals(guardado.getCreadoEn(), repo.findById("bien-fechas").orElseThrow().getCreadoEn());
    }

    private static Usuario cuenta(Rol rol, String area) {
        Usuario u = new Usuario();
        u.setId("u-alcance");
        u.setUsername("alcance");
        u.setNombre("Cuenta de prueba");
        u.setRol(rol);
        u.setArea(area);
        return u;
    }

    private static Producto bien(String id, String nombre, String codigo, String area) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setCodigo(codigo);
        p.setCategoriaId(CAT_ID);
        p.setPrecioCompra(BigDecimal.valueOf(1000));
        p.setPrecioVenta(BigDecimal.valueOf(1000));
        p.setStockActual(1);
        p.setStockMaximo(1);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(area);
        p.setProximaRevision(LocalDate.now().plusDays(10));
        p.setFechaVencimiento(LocalDate.now().plusDays(5));
        return p;
    }
}
