package com.sibim.service;

import com.sibim.model.ActaEntregaRecepcion;
import com.sibim.model.Producto;
import com.sibim.repository.ActaRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActaServiceTest {

    @Mock ActaRepository          mockActaRepo;
    @Mock ProductoRepository      mockProductoRepo;
    @Mock ConfiguracionRepository mockCfg;

    private ActaService service;
    private final LocalDate hoy = LocalDate.now();

    @BeforeEach
    void setUp() {
        service = new ActaService(mockActaRepo, mockProductoRepo, mockCfg);
    }

    // ── Validaciones de funcionario saliente ─────────────────────────────────

    @Test
    void generar_adminSalienteNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generar(null, null, "Luis Hdez", null, hoy, null));
    }

    @Test
    void generar_adminSalienteVacio_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generar("  ", null, "Luis Hdez", null, hoy, null));
    }

    // ── Validaciones de funcionario entrante ─────────────────────────────────

    @Test
    void generar_adminEntranteNulo_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generar("María Ríos", null, null, null, hoy, null));
    }

    @Test
    void generar_adminEntranteVacio_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generar("María Ríos", null, "  ", null, hoy, null));
    }

    // ── Validación de fecha ───────────────────────────────────────────────────

    @Test
    void generar_fechaNula_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> service.generar("María Ríos", null, "Luis Hdez", null, null, null));
    }

    // ── Éxito: generar acta ───────────────────────────────────────────────────

    @Test
    void generar_datosValidos_captura_inventario_yRetornaActa() throws Exception {
        List<Producto> inventario = List.of(productoVacio(), productoVacio());
        when(mockProductoRepo.findAll()).thenReturn(inventario);

        ActaEntregaRecepcion actaGuardada = new ActaEntregaRecepcion();
        actaGuardada.setNumero("ACT-0001");
        actaGuardada.setFechaEntrega(hoy);
        when(mockActaRepo.save(any(ActaEntregaRecepcion.class))).thenReturn(actaGuardada);

        ActaEntregaRecepcion resultado = service.generar(
            "María Ríos", "Presidenta Municipal",
            "Luis Hdez", "Nuevo Presidente",
            hoy, "Observación de prueba");

        assertSame(actaGuardada, resultado);
        verify(mockProductoRepo).findAll();
        verify(mockActaRepo).save(any(ActaEntregaRecepcion.class));
    }

    @Test
    void generar_inventarioVacio_totalBienesEsCero() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of());

        ActaEntregaRecepcion guardada = new ActaEntregaRecepcion();
        guardada.setTotalBienes(0);
        when(mockActaRepo.save(any(ActaEntregaRecepcion.class))).thenReturn(guardada);

        ActaEntregaRecepcion resultado = service.generar(
            "María Ríos", null, "Luis Hdez", null, hoy, null);

        assertNotNull(resultado);
        verify(mockActaRepo).save(argThat(a -> a.getTotalBienes() == 0));
    }

    @Test
    void generar_cargosOpcionales_noLanzaExcepcion() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of());
        when(mockActaRepo.save(any(ActaEntregaRecepcion.class)))
            .thenReturn(new ActaEntregaRecepcion());

        assertDoesNotThrow(() ->
            service.generar("María Ríos", null, "Luis Hdez", null, hoy, null));
    }

    // ── Mensajes de error legibles ────────────────────────────────────────────

    @Test
    void generar_salienteVacio_mensajeMencionaSaliente() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.generar("", null, "Luis", null, hoy, null));
        assertTrue(ex.getMessage().toLowerCase().contains("saliente"));
    }

    @Test
    void generar_entranteVacio_mensajeMencionaEntrante() {
        var ex = assertThrows(IllegalArgumentException.class,
            () -> service.generar("María", null, "", null, hoy, null));
        assertTrue(ex.getMessage().toLowerCase().contains("entrante"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Producto productoVacio() {
        Producto p = new Producto();
        p.setId("p-" + System.nanoTime());
        p.setNombre("Bien de prueba");
        p.setPrecioVenta(BigDecimal.ZERO);
        p.setPrecioCompra(BigDecimal.ZERO);
        p.setStockActual(1);
        return p;
    }
}
