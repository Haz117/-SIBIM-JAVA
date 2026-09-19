package com.sibim.service;

import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ComodatoRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.PrestamoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrestamoServiceTest {

    @Mock PrestamoRepository      mockRepo;
    @Mock ProductoRepository      mockProductoRepo;
    @Mock ComodatoRepository      mockComodatoRepo;
    @Mock ConfiguracionRepository mockCfg;
    @Mock AuditLogRepository      mockAudit;

    private PrestamoService service;
    private Producto producto;
    private final LocalDate manana = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() throws Exception {
        service = new PrestamoService(mockRepo, mockProductoRepo, mockComodatoRepo, mockCfg, mockAudit);

        producto = new Producto();
        producto.setId("p-01");
        producto.setNombre("Laptop Dell");
        producto.setCodigo("INV-001");
        producto.setArea("Presidencia");

        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(producto));
        when(mockProductoRepo.findById(argThat(id -> !"p-01".equals(id))))
            .thenReturn(Optional.empty());
    }

    // ── Validaciones de productoId ────────────────────────────────────────────

    @Test
    void crear_productoIdNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear(null, "Área B", "Juan Pérez", null, null, manana));
    }

    @Test
    void crear_productoIdVacio_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("  ", "Área B", "Juan Pérez", null, null, manana));
    }

    // ── Validaciones de área destino ──────────────────────────────────────────

    @Test
    void crear_areaDestinoNula_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", null, "Juan Pérez", null, null, manana));
    }

    @Test
    void crear_areaDestinoVacia_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", "  ", "Juan Pérez", null, null, manana));
    }

    // ── Validaciones de responsable ───────────────────────────────────────────

    @Test
    void crear_responsableNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", "Área B", null, null, null, manana));
    }

    @Test
    void crear_responsableVacio_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", "Área B", "   ", null, null, manana));
    }

    // ── Validaciones de fecha ─────────────────────────────────────────────────

    @Test
    void crear_fechaNula_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", "Área B", "Juan Pérez", null, null, null));
    }

    @Test
    void crear_fechaHoy_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", "Área B", "Juan Pérez", null, null, LocalDate.now()));
    }

    @Test
    void crear_fechaPasada_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", "Área B", "Juan Pérez", null, null,
                LocalDate.now().minusDays(1)));
    }

    // ── Validación: producto no encontrado ────────────────────────────────────

    @Test
    void crear_productoNoExiste_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.crear("no-existe", "Área B", "Juan Pérez", null, null, manana));
    }

    // ── Éxito: crear préstamo ────────────────────────────────────────────────

    @Test
    void crear_datosValidos_llamaRepoSave_yRetornaPrestamo() throws Exception {
        Prestamo guardado = new Prestamo();
        guardado.setNumero("PRE-0001");
        when(mockRepo.save(any(Prestamo.class))).thenReturn(guardado);

        Prestamo resultado = service.crear("p-01", "Área B", "Juan Pérez",
            "Jefe de Área", "Uso temporal", manana);

        assertSame(guardado, resultado);
        verify(mockRepo).save(any(Prestamo.class));
    }

    @Test
    void crear_sinCargo_noLanzaExcepcion() throws Exception {
        when(mockRepo.save(any(Prestamo.class))).thenReturn(new Prestamo());

        assertDoesNotThrow(() ->
            service.crear("p-01", "Área B", "Juan Pérez", null, null, manana));
    }

    // ── devolver ─────────────────────────────────────────────────────────────

    @Test
    void devolver_fechaExplicita_llama_repoDevolver() throws Exception {
        LocalDate hoy = LocalDate.now();
        service.devolver("pre-01", hoy);
        verify(mockRepo).devolver(eq("pre-01"), eq(hoy));
    }

    @Test
    void devolver_fechaNula_usaHoy() throws Exception {
        service.devolver("pre-01", null);
        verify(mockRepo).devolver(eq("pre-01"), eq(LocalDate.now()));
    }

    // ── Mensajes de error legibles ────────────────────────────────────────────

    @Test
    void crear_fechaHoy_mensajeMencionaFecha() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.crear("p-01", "Área B", "Juan", null, null, LocalDate.now()));
        assertTrue(ex.getMessage().toLowerCase().contains("fecha")
                || ex.getMessage().toLowerCase().contains("devolución"));
    }
}
