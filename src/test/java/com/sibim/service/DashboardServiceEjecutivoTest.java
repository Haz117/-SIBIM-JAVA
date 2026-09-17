package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests for DashboardService#calcularResumenEjecutivo — the panel ejecutivo
 *  (tesorería/presidencia) that shows % de depreciación acumulada del
 *  patrimonio y qué bienes cumplen su vida útil este año. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceEjecutivoTest {

    @Mock ProductoRepository   mockProductoRepo;
    @Mock MovimientoRepository mockMovimientoRepo;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(mockProductoRepo, mockMovimientoRepo);
    }

    @Test
    void sinBienes_retornaCeros() throws Exception {
        when(mockProductoRepo.findAll(false)).thenReturn(List.of());

        var r = service.calcularResumenEjecutivo();

        assertEquals(0, r.totalBienes());
        assertEquals(0, r.bienesConDatosDepreciacion());
        assertEquals(0.0, r.porcentajeDepreciado());
        assertEquals(0, r.bienesFinVidaUtilEsteAnio());
    }

    @Test
    void bienSinDatosDeDepreciacion_seExcluyeDelCalculo() throws Exception {
        Producto sinDatos = new Producto();
        sinDatos.setPrecioCompra(BigDecimal.valueOf(1000)); // sin fechaAdquisicion/vidaUtilAnios
        when(mockProductoRepo.findAll(false)).thenReturn(List.of(sinDatos));

        var r = service.calcularResumenEjecutivo();

        assertEquals(1, r.totalBienes());
        assertEquals(0, r.bienesConDatosDepreciacion());
        assertEquals(BigDecimal.ZERO, r.valorCompraTotal());
    }

    @Test
    void bienTotalmenteDepreciado_suma100PorCiento() throws Exception {
        Producto p = new Producto();
        p.setPrecioCompra(BigDecimal.valueOf(10000));
        p.setValorResidual(BigDecimal.ZERO);
        p.setFechaAdquisicion(LocalDate.now().minusYears(10));
        p.setVidaUtilAnios(2); // vida útil vencida hace años -> valorDepreciado = residual = 0
        when(mockProductoRepo.findAll(false)).thenReturn(List.of(p));

        var r = service.calcularResumenEjecutivo();

        assertEquals(1, r.bienesConDatosDepreciacion());
        assertEquals(100.0, r.porcentajeDepreciado(), 0.01);
    }

    @Test
    void bienReciente_depreciacionCercanaACero() throws Exception {
        Producto p = new Producto();
        p.setPrecioCompra(BigDecimal.valueOf(10000));
        p.setValorResidual(BigDecimal.ZERO);
        p.setFechaAdquisicion(LocalDate.now()); // recién adquirido
        p.setVidaUtilAnios(10);
        when(mockProductoRepo.findAll(false)).thenReturn(List.of(p));

        var r = service.calcularResumenEjecutivo();

        assertEquals(0.0, r.porcentajeDepreciado(), 0.5);
    }

    @Test
    void bienQueCumpleVidaUtilEsteAnio_seCuentaYSeLista() throws Exception {
        Producto p = new Producto();
        p.setNombre("Laptop vieja");
        p.setPrecioCompra(BigDecimal.valueOf(10000));
        p.setFechaAdquisicion(LocalDate.now().minusYears(3));
        p.setVidaUtilAnios(3); // termina exactamente este año
        when(mockProductoRepo.findAll(false)).thenReturn(List.of(p));

        var r = service.calcularResumenEjecutivo();

        assertEquals(1, r.bienesFinVidaUtilEsteAnio());
        assertEquals(1, r.bienesFinVidaUtil().size());
        assertEquals("Laptop vieja", r.bienesFinVidaUtil().get(0).getNombre());
    }

    @Test
    void bienQueCumpleVidaUtilOtroAnio_noSeCuenta() throws Exception {
        Producto p = new Producto();
        p.setPrecioCompra(BigDecimal.valueOf(10000));
        p.setFechaAdquisicion(LocalDate.now());
        p.setVidaUtilAnios(15); // termina en 15 años, no este año
        when(mockProductoRepo.findAll(false)).thenReturn(List.of(p));

        var r = service.calcularResumenEjecutivo();

        assertEquals(0, r.bienesFinVidaUtilEsteAnio());
        assertTrue(r.bienesFinVidaUtil().isEmpty());
    }

    @Test
    void valorCompraYDepreciadoTotal_sumanSoloBienesConDatos() throws Exception {
        Producto conDatos = new Producto();
        conDatos.setPrecioCompra(BigDecimal.valueOf(5000));
        conDatos.setValorResidual(BigDecimal.ZERO);
        conDatos.setFechaAdquisicion(LocalDate.now().minusYears(1));
        conDatos.setVidaUtilAnios(5);

        Producto sinDatos = new Producto();
        sinDatos.setPrecioCompra(BigDecimal.valueOf(99999)); // no debe sumar: falta vidaUtilAnios

        when(mockProductoRepo.findAll(false)).thenReturn(List.of(conDatos, sinDatos));

        var r = service.calcularResumenEjecutivo();

        assertEquals(0, BigDecimal.valueOf(5000).compareTo(r.valorCompraTotal()));
    }
}
