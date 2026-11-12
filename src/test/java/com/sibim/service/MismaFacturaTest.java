package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.MismaFactura.Cambio;
import com.sibim.service.MismaFactura.Dato;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MismaFacturaTest {

    private static int n = 0;

    private static Producto bien(String nombre, String factura) {
        Producto p = new Producto();
        p.setId("id-" + (++n));
        p.setCodigo("SOP/" + n);
        p.setNombre(nombre);
        p.setNumeroFactura(factura);
        p.setStockActual(1);
        return p;
    }

    private static Producto con(Producto p, String proveedor, String costo) {
        p.setProveedor(proveedor);
        p.setPrecioCompra(costo == null ? null : new BigDecimal(costo));
        return p;
    }

    private static Cambio de(List<Cambio> cambios, Dato d) {
        return cambios.stream().filter(c -> c.dato() == d).findFirst().orElse(null);
    }

    private static Set<String> codigos(Cambio c) {
        Set<String> s = new TreeSet<>();
        c.bienes().forEach(p -> s.add(p.getCodigo()));
        return s;
    }

    @Test
    void laFacturaSeComparaSinEspaciosGuionesNiCerosAlInicio_yLoQueNoEsFacturaNoCuenta() {
        assertEquals(MismaFactura.clave("AA362"), MismaFactura.clave("  aa   362 "));
        assertEquals(MismaFactura.clave("F-MR-003343"), MismaFactura.clave("F-MR-03343"));
        assertNotEquals(MismaFactura.clave("F-MR-3343"), MismaFactura.clave("F-MR-3344"));
        assertNull(MismaFactura.clave("SIN DOCUMENTO"));
        assertNull(MismaFactura.clave("SIN FACTURA , COMODATO"));
        assertNull(MismaFactura.clave("s/f"));
        assertNull(MismaFactura.clave(" "));
        assertNull(MismaFactura.clave(null));
    }

    @Test
    void unNumeroDeFacturaCortoNoJuntaCosasQueNoSeCompraronJuntas() {
        Producto grua = bien("GRUA INTERNATIONAL", "139");
        grua.setFechaAdquisicion(LocalDate.of(2026, 1, 23));
        grua.setFolioFiscal("2950AE2E-79A1");
        Producto cpu = bien("C.P.U.", "139");
        assertFalse(MismaFactura.mismaCompra(grua, cpu));
        assertTrue(MismaFactura.pendientes(List.of(grua, cpu)).isEmpty());

        Producto silla = bien("SILLA", "F-MR-003343");
        silla.setFechaAdquisicion(LocalDate.of(2023, 1, 20));
        Producto anaquel = bien("ANAQUEL", "F-MR-03343");
        assertTrue(MismaFactura.mismaCompra(silla, anaquel), "una factura larga basta, aunque sean cosas distintas");
        anaquel.setFechaAdquisicion(LocalDate.of(2019, 5, 2));
        assertFalse(MismaFactura.mismaCompra(silla, anaquel), "otra fecha es otra compra");
    }

    /** 12-nov-2026: the body cams of factura A 87, «BODY CAM» typed one by one. Marca and
     *  modelo go to the pieces of the same name; «SIN MARCA» counts as empty. */
    @Test
    void laMarcaYElModeloVanALosDelMismoNombre() {
        Producto antes = bien("BODY CAM", "A 87");
        Producto despues = antes.copia();
        despues.setMarca("HIKVISION");
        despues.setModelo("DS-MCW406");
        Producto otra = bien("BODY CAM", "A87");
        Producto conPlaceholder = bien("BODY CAM", "A 87");
        conPlaceholder.setMarca("SIN MARCA");
        Producto otraCosa = bien("RADIO", "A 87");

        List<Cambio> cambios = MismaFactura.proponer(antes, despues, List.of(antes, otra, conPlaceholder, otraCosa));
        assertEquals(Set.of(otra.getCodigo(), conPlaceholder.getCodigo()), codigos(de(cambios, Dato.MARCA)));
        assertEquals(Set.of(otra.getCodigo(), conPlaceholder.getCodigo()), codigos(de(cambios, Dato.MODELO)));
    }

    /** 12-nov-2026: 581 bienes had only the UUID of their CFDI. The same UUID is one
     *  purchase, with or without a factura number; two UUIDs are two purchases. */
    @Test
    void elMismoFolioFiscalEsLaMismaCompra_aunqueNoTenganNumeroDeFactura() {
        Producto a = bien("CAMARA BALA", null);
        a.setFolioFiscal("3822637c-8b6a-410a-b7c9-6c29c9eeb359");
        a.setProveedor("HIKVISION MEXICO");
        Producto b = bien("CAMARA BALA", null);
        b.setFolioFiscal("3822637C8B6A410AB7C96C29C9EEB359");
        Producto conNumero = bien("SWITCH", "A 912");
        conNumero.setFolioFiscal("3822637C-8B6A-410A-B7C9-6C29C9EEB359");
        Producto otraCompra = bien("CAMARA BALA", "A 912");
        otraCompra.setFolioFiscal("11111111-2222-3333-4444-555555555555");

        assertTrue(MismaFactura.mismaCompra(a, b));
        assertTrue(MismaFactura.mismaCompra(a, conNumero), "el número de factura no hace falta si el CFDI es el mismo");
        assertFalse(MismaFactura.mismaCompra(conNumero, otraCompra), "misma factura, otro CFDI: otra compra");
        assertTrue(MismaFactura.tieneCompra(a));
        assertTrue(MismaFactura.etiqueta(a).startsWith("con folio fiscal 3822637c"), MismaFactura.etiqueta(a));

        Cambio proveedor = de(MismaFactura.pendientes(List.of(a, b, conNumero, otraCompra)), Dato.PROVEEDOR);
        assertEquals(Set.of(b.getCodigo(), conNumero.getCodigo()), codigos(proveedor));
    }

    @Test
    void corregirElFolioLlegaALosQueTeniaElEquivocado() {
        Producto antes = bien("LAPTOP", "F-AG-010192");
        antes.setFolioFiscal("FOLIO-MAL");
        Producto despues = antes.copia();
        despues.setFolioFiscal("961DEBE0-8C81-4994-BF92-8506FC57C003");
        Producto otra = bien("LAPTOP", "F-AG-010192");
        otra.setFolioFiscal("FOLIO-MAL");

        Cambio folio = de(MismaFactura.proponer(antes, despues, List.of(antes, otra)), Dato.FOLIO_FISCAL);
        assertEquals(Set.of(otra.getCodigo()), codigos(folio));
    }

    @Test
    void elProveedorVaATodaLaFactura_yElCostoSoloALosDelMismoNombreQueCostabanLoMismo() {
        Producto antes = con(bien("SILLA OPERATIVA", "A667"), null, "1590.52");
        Producto despues = antes.copia();
        despues.setProveedor("RAUL FICTICIO LOPEZ");
        despues.setPrecioCompra(new BigDecimal("1845.00"));

        Producto mismaSilla = con(bien("Silla operativa", "a667"), null, "1590.52");
        Producto sillaOtroCosto = con(bien("SILLA OPERATIVA", "A667"), null, "1700");
        Producto sillaSinCosto = con(bien("SILLA OPERATIVA", "A667"), null, "0");
        Producto escritorio = con(bien("ESCRITORIO", "A667"), null, "1590.52");
        Producto conOtroProveedor = con(bien("ESCRITORIO", "A667"), "OFFICE DEPOT", null);
        Producto otraFactura = con(bien("SILLA OPERATIVA", "B100"), null, "1590.52");
        Producto deBaja = con(bien("SILLA OPERATIVA", "A667"), null, "1590.52");
        deBaja.setFechaBaja(LocalDate.of(2026, 1, 1));
        // A667 is a short number: the date says they were bought together.
        for (Producto p : List.of(antes, despues, mismaSilla, sillaOtroCosto, sillaSinCosto, escritorio, conOtroProveedor, otraFactura, deBaja))
            p.setFechaAdquisicion(LocalDate.of(2023, 12, 8));

        List<Cambio> cambios = MismaFactura.proponer(antes, despues, List.of(antes, mismaSilla, sillaOtroCosto,
            sillaSinCosto, escritorio, conOtroProveedor, otraFactura, deBaja));

        assertEquals(Set.of(mismaSilla.getCodigo(), sillaOtroCosto.getCodigo(), sillaSinCosto.getCodigo(), escritorio.getCodigo()),
            codigos(de(cambios, Dato.PROVEEDOR)), "un proveedor distinto ya capturado no se toca");
        assertEquals(Set.of(mismaSilla.getCodigo(), sillaSinCosto.getCodigo()), codigos(de(cambios, Dato.COSTO)),
            "el costo: mismo nombre, y vacío o igual al de antes");
        assertEquals(new BigDecimal("1845.00"), de(cambios, Dato.COSTO).nuevo());
        assertNull(de(cambios, Dato.FOLIO_FISCAL), "lo que no cambió no se propone");
    }

    @Test
    void unBienNuevoPasaLoQueTraeSoloALosQueNoLoTienen() {
        Producto nuevo = con(bien("LAPTOP", "F-9"), "HERCOM", "20000");
        nuevo.setFolioFiscal("7EA05B42-4508-4C36-80EE-990E7A5D0D66");
        Producto sinNada = bien("LAPTOP", "F-9");
        Producto conFolio = bien("LAPTOP", "F-9");
        conFolio.setFolioFiscal("OTRO-FOLIO");

        List<Cambio> cambios = MismaFactura.proponer(null, nuevo, List.of(nuevo, sinNada, conFolio));

        // Another folio fiscal is another purchase, whatever its factura number says.
        assertEquals(Set.of(sinNada.getCodigo()), codigos(de(cambios, Dato.PROVEEDOR)));
        assertEquals(Set.of(sinNada.getCodigo()), codigos(de(cambios, Dato.FOLIO_FISCAL)));
        assertEquals(Set.of(sinNada.getCodigo()), codigos(de(cambios, Dato.COSTO)));
    }

    @Test
    void sinFacturaNoSeProponeNada() {
        Producto a = con(bien("SILLA", "SIN DOCUMENTO"), null, null);
        Producto b = a.copia();
        b.setProveedor("X");
        assertTrue(MismaFactura.proponer(a, b, List.of(a, bien("SILLA", "SIN DOCUMENTO"))).isEmpty());
    }

    @Test
    void loPendienteSeTomaSoloCuandoLosQueLoTienenDicenLoMismo() {
        Producto a = con(bien("SILLA", "F-MR-003343"), "RAUL FICTICIO LOPEZ", "3092.56");
        Producto b = con(bien("SILLA", "F-MR-03343"), null, null);     // la misma factura escrita de otra forma
        Producto c = con(bien("MESA", "f-mr-003343"), null, null);
        Producto d = con(bien("MESA", "F-MR-003343"), "raúl ficticio lópez", "900");
        Producto e = con(bien("SILLA", "B-0001-X"), "UNO", null);
        Producto f = con(bien("SILLA", "B-0001-X"), "OTRO", null);
        Producto g = con(bien("SILLA", "B-0001-X"), null, null);

        List<Cambio> cambios = MismaFactura.pendientes(List.of(a, b, c, d, e, f, g));

        Cambio prov = cambios.stream().filter(x -> x.dato() == Dato.PROVEEDOR && x.factura().startsWith("F-MR")).findFirst().orElseThrow();
        assertEquals(Set.of(b.getCodigo(), c.getCodigo()), codigos(prov), "solo los vacíos; acentos y mayúsculas no hacen otro proveedor");
        assertEquals("RAUL FICTICIO LOPEZ", prov.nuevo());
        Map<String, Object> costoDe = new HashMap<>();
        cambios.stream().filter(x -> x.dato() == Dato.COSTO).forEach(x -> x.bienes().forEach(p -> costoDe.put(p.getCodigo(), x.nuevo())));
        assertEquals(Map.of(b.getCodigo(), new BigDecimal("3092.56"), c.getCodigo(), new BigDecimal("900")), costoDe,
            "el costo, de los del mismo nombre");
        assertTrue(cambios.stream().noneMatch(x -> x.factura().equals("B-0001-X") && x.dato() == Dato.PROVEEDOR),
            "dos proveedores distintos en la factura: no se adivina");
    }

    @Test
    void aplicarGuardaCadaBienUnaVezConTodosSusDatos_yDeshacerLosRegresa() {
        List<Producto> guardados = new ArrayList<>();
        ProductoService servicio = new ProductoService(mock(ProductoRepository.class), mock(AuditLogRepository.class)) {
            @Override public Producto save(Producto p) throws ProductoService.ValidationException {
                if ("SOP/falla".equals(p.getCodigo())) throw new ProductoService.ValidationException("modificado por otro");
                guardados.add(p.copia());
                return p.copia();
            }
        };
        Producto s1 = bien("SILLA", "A1");
        Producto s2 = bien("SILLA", "A1");
        s2.setCodigo("SOP/falla");
        List<Cambio> cambios = List.of(
            new Cambio(Dato.PROVEEDOR, "A1", "RAUL", List.of(s1, s2)),
            new Cambio(Dato.VIDA_UTIL, "A1", 10, List.of(s1)));

        MismaFactura.Resultado r = MismaFactura.aplicar(servicio, cambios);

        assertEquals(1, guardados.size(), "un guardado por bien, no uno por dato");
        assertEquals("RAUL", guardados.get(0).getProveedor());
        assertEquals(10, guardados.get(0).getVidaUtilAnios());
        assertEquals(1, r.fallas().size());
        assertTrue(r.fallas().get(0).startsWith("SOP/falla"));

        guardados.clear();
        assertEquals(1, MismaFactura.deshacer(servicio, r, cambios));
        assertNull(guardados.get(0).getProveedor());
        assertNull(guardados.get(0).getVidaUtilAnios());
    }
}
