package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.model.enums.Rol;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoFiltro;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoServiceFiltroTest {

    @Mock ProductoRepository mockRepo;
    @Mock AuditLogRepository mockAuditRepo;

    private ProductoService service;

    @BeforeEach
    void setUp() {
        service = new ProductoService(mockRepo, mockAuditRepo);
        Usuario usuario = new Usuario();
        usuario.setId("u1");
        usuario.setNombre("Test");
        usuario.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(usuario);
    }

    @AfterEach
    void tearDown() {
        SessionManager.setCurrentUser(null);
    }

    // ── getPaginated con ProductoFiltro ──────────────────────────────────────

    @Test
    void getPaginated_filtro_delegaAlRepo() throws SQLException {
        ProductoFiltro filtro = ProductoFiltro.vacio().conBusqueda("laptop").conArea("TICS");
        when(mockRepo.findPaginated(eq(filtro), eq(20), eq(0))).thenReturn(List.of(producto("p1")));

        List<Producto> result = service.getPaginated(filtro, 20, 0);

        assertEquals(1, result.size());
        verify(mockRepo).findPaginated(filtro, 20, 0);
    }

    @Test
    void getPaginated_filtroVacio_delegaSinFiltros() throws SQLException {
        ProductoFiltro filtro = ProductoFiltro.vacio();
        when(mockRepo.findPaginated(eq(filtro), eq(10), eq(5))).thenReturn(List.of());

        List<Producto> result = service.getPaginated(filtro, 10, 5);

        assertTrue(result.isEmpty());
        verify(mockRepo).findPaginated(filtro, 10, 5);
    }

    // ── countFiltrado con ProductoFiltro ─────────────────────────────────────

    @Test
    void countFiltrado_filtro_delegaAlRepo() throws SQLException {
        ProductoFiltro filtro = ProductoFiltro.vacio().conEstado(EstadoProducto.AGOTADO);
        when(mockRepo.countFiltrado(filtro)).thenReturn(7);

        int result = service.countFiltrado(filtro);

        assertEquals(7, result);
        verify(mockRepo).countFiltrado(filtro);
    }

    @Test
    void countFiltrado_filtroConRango_pasaFechasCorrectamente() throws SQLException {
        LocalDate desde = LocalDate.of(2025, 1, 1);
        LocalDate hasta = LocalDate.of(2025, 6, 30);
        ProductoFiltro filtro = ProductoFiltro.vacio().conRango(desde, hasta);
        when(mockRepo.countFiltrado(filtro)).thenReturn(42);

        int result = service.countFiltrado(filtro);

        assertEquals(42, result);
        ArgumentCaptor<ProductoFiltro> captor = ArgumentCaptor.forClass(ProductoFiltro.class);
        verify(mockRepo).countFiltrado(captor.capture());
        assertEquals(desde, captor.getValue().desdeReg());
        assertEquals(hasta, captor.getValue().hastaReg());
    }

    // ── getAllFiltrado con ProductoFiltro ─────────────────────────────────────

    @Test
    void getAllFiltrado_filtro_delegaAlRepo() throws SQLException {
        ProductoFiltro filtro = ProductoFiltro.vacio().conArea("PRES").conBusqueda("silla");
        when(mockRepo.findAllFiltrado(filtro)).thenReturn(List.of(producto("p1"), producto("p2")));

        List<Producto> result = service.getAllFiltrado(filtro);

        assertEquals(2, result.size());
        verify(mockRepo).findAllFiltrado(filtro);
    }

    // ── Overloads legacy construyen el filtro correctamente ──────────────────

    @Test
    void countFiltrado_legacyConSinEtiquetar_propagaBandera() throws SQLException {
        when(mockRepo.countFiltrado(any(ProductoFiltro.class))).thenReturn(3);

        LocalDate desde = LocalDate.of(2025, 3, 1);
        service.countFiltrado(null, null, null, null, null, true, desde, null);

        ArgumentCaptor<ProductoFiltro> captor = ArgumentCaptor.forClass(ProductoFiltro.class);
        verify(mockRepo).countFiltrado(captor.capture());
        assertTrue(captor.getValue().soloSinEtiquetar());
        assertEquals(desde, captor.getValue().desdeReg());
    }

    @Test
    void getPaginated_legacyConFechas_propagaRango() throws SQLException {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = LocalDate.of(2024, 12, 31);
        when(mockRepo.findPaginated(any(ProductoFiltro.class), eq(25), eq(0))).thenReturn(List.of());

        service.getPaginated("silla", null, null, null, null, false, 25, 0, desde, hasta);

        ArgumentCaptor<ProductoFiltro> captor = ArgumentCaptor.forClass(ProductoFiltro.class);
        verify(mockRepo).findPaginated(captor.capture(), eq(25), eq(0));
        assertEquals("silla", captor.getValue().busqueda());
        assertEquals(desde, captor.getValue().desdeReg());
        assertEquals(hasta, captor.getValue().hastaReg());
        assertFalse(captor.getValue().incluirBaja());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Producto producto(String id) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto " + id);
        p.setCodigo("COD-" + id);
        p.setArea("TICS");
        return p;
    }
}
