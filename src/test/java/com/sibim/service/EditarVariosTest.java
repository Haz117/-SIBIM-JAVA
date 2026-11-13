package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.EditarVarios.Cambio;
import com.sibim.service.EditarVarios.Dato;
import com.sibim.service.EditarVarios.Renglon;
import com.sibim.util.ProductoUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Bienes › several selected › «Cambiar datos»: only what is filled changes, the review
 *  says bien by bien what, each bien is saved once, and it is undone. */
class EditarVariosTest {

    private static Producto bien(String codigo, String costo) {
        Producto p = new Producto();
        p.setId("id-" + codigo);
        p.setCodigo(codigo);
        p.setNombre("SILLA");
        p.setPrecioCompra(costo == null ? null : new BigDecimal(costo));
        p.setPrecioVenta(p.getPrecioCompra());
        p.setStockActual(1);
        return p;
    }

    @Test
    void revisar_soloLosQueCambian_yDiceQue() {
        Producto a = bien("SOP/1", "1000"), b = bien("SOP/2", "1000");
        a.setColor("NEGRO");
        b.setColor("BLANCO");
        b.setTipoBien("MUEBLE");

        List<Renglon> r = EditarVarios.revisar(List.of(a, b),
            List.of(Cambio.fijo(Dato.TIPO_BIEN, "MUEBLE"), Cambio.fijo(Dato.COLOR, "BLANCO")));

        assertEquals(1, r.size(), "SOP/2 ya tenía todo");
        assertEquals("SOP/1", r.get(0).bien().getCodigo());
        assertEquals(List.of(Dato.COLOR, Dato.TIPO_BIEN), r.get(0).cambian(), "en el orden del formulario");
        assertEquals("Color: NEGRO → BLANCO · Tipo de bien: (vacío) → MUEBLE", r.get(0).resumen());
        assertNull(a.getTipoBien(), "revisar no toca los bienes");
    }

    @Test
    void ivaYQuincePorCiento_seSacanDeCadaBien_yElResidualDelCostoNuevo() {
        Producto a = bien("TES/1", "1000"), b = bien("TES/2", "2500"), sinCosto = bien("TES/3", "0");

        List<Renglon> r = EditarVarios.revisar(List.of(a, b, sinCosto),
            List.of(EditarVarios.residualDeCadaUno(), EditarVarios.ivaACadaUno()));   // in any order

        assertEquals(2, r.size(), "sin costo no se le inventa nada");
        assertEquals(0, new BigDecimal("1160.00").compareTo(r.get(0).nuevo().getPrecioCompra()));
        assertEquals(0, new BigDecimal("174.00").compareTo(r.get(0).nuevo().getValorResidual()), "15 % del costo con IVA");
        assertEquals(0, new BigDecimal("2900.00").compareTo(r.get(1).nuevo().getPrecioCompra()));
        assertEquals(0, new BigDecimal("2900.00").compareTo(r.get(1).nuevo().getPrecioVenta()), "como el formulario: venta = costo");
        assertEquals(0, new BigDecimal("435.00").compareTo(r.get(1).nuevo().getValorResidual()));
    }

    @Test
    void aplicar_unGuardadoPorBien_lasFallasNoDetienenAlResto_yDeshacerRegresaSoloEsosDatos() {
        List<Producto> guardados = new ArrayList<>();
        ProductoService servicio = new ProductoService(mock(ProductoRepository.class), mock(AuditLogRepository.class)) {
            @Override public Producto save(Producto p) throws ProductoService.ValidationException {
                if ("SOP/falla".equals(p.getCodigo())) throw new ProductoService.ValidationException("modificado por otro");
                guardados.add(p.copia());
                return p.copia();
            }
        };
        Producto a = bien("SOP/1", "500"), falla = bien("SOP/falla", "500");
        a.setUbicacion("BODEGA");
        List<Renglon> r = EditarVarios.revisar(List.of(a, falla),
            List.of(Cambio.fijo(Dato.PROVEEDOR, "RAUL FICTICIO LOPEZ"), Cambio.fijo(Dato.VIDA_UTIL, 10)));

        EditarVarios.Resultado res = EditarVarios.aplicar(servicio, r);

        assertEquals(1, guardados.size(), "un guardado por bien, con todos sus datos");
        assertEquals("RAUL FICTICIO LOPEZ", guardados.get(0).getProveedor());
        assertEquals(10, guardados.get(0).getVidaUtilAnios());
        assertEquals(List.of("SOP/falla: modificado por otro"), res.fallas());

        guardados.clear();
        assertEquals(1, EditarVarios.deshacer(servicio, res));
        assertNull(guardados.get(0).getProveedor());
        assertNull(guardados.get(0).getVidaUtilAnios());
        assertEquals("BODEGA", guardados.get(0).getUbicacion(), "lo que no se cambió, sigue");
    }

    @Test
    void aplicar_diceCuantoLleva() {
        ProductoService servicio = new ProductoService(mock(ProductoRepository.class), mock(AuditLogRepository.class)) {
            @Override public Producto save(Producto p) { return p.copia(); }
        };
        List<Renglon> r = EditarVarios.revisar(List.of(bien("A/1", "1"), bien("A/2", "1"), bien("A/3", "1"), bien("A/4", "1")),
            List.of(Cambio.fijo(Dato.COLOR, "NEGRO")));
        List<Double> avance = new ArrayList<>();

        EditarVarios.aplicar(servicio, r, avance::add);

        assertEquals(List.of(0.0, 0.25, 0.5, 0.75), avance);
    }

    @Test
    void ahora_diceLoQueTienenLosSeleccionados() {
        Producto a = bien("A/1", "1"), b = bien("A/2", "1"), c = bien("A/3", "1");
        assertEquals("Todos vacíos", EditarVarios.ahora(Dato.COLOR, List.of(a, b, c)));
        a.setColor("NEGRO"); b.setColor("negro "); c.setColor("NEGRO");
        assertEquals("Todos: NEGRO", EditarVarios.ahora(Dato.COLOR, List.of(a, b, c)));
        c.setColor(null);
        assertEquals("NEGRO en 2 · vacío en 1", EditarVarios.ahora(Dato.COLOR, List.of(a, b, c)));
        c.setColor("GRIS");
        assertEquals("2 distintos", EditarVarios.ahora(Dato.COLOR, List.of(a, b, c)));
    }

    @Test
    void residualSugerido_esElQuincePorCientoAlCentavo() {
        assertEquals(new BigDecimal("106837.50"), ProductoUtils.residualSugerido(new BigDecimal("712250")));
        assertEquals(new BigDecimal("0.15"), ProductoUtils.residualSugerido(new BigDecimal("0.99")));
        assertNull(ProductoUtils.residualSugerido(BigDecimal.ZERO));
        assertNull(ProductoUtils.residualSugerido(null));
    }
}
