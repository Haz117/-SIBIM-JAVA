package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CompletarPorFactura.Campo;
import com.sibim.service.CompletarPorFactura.Factura;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** «Completar por factura» and «Lo seguro, de una vez», with cases like the inventory of 24-nov-2026. */
class CompletarPorFacturaTest {

    private static final String UUID_C2 = "3822637C-8B6A-410A-B1C2-0123456789AB";
    private int n;

    private Producto bien(String nombre, String area, String factura, String folio) {
        Producto p = new Producto();
        p.setId("id" + (++n));
        p.setCodigo("SSPC/" + n);
        p.setNombre(nombre);
        p.setArea(area);
        p.setNumeroFactura(factura);
        p.setFolioFiscal(folio);
        p.setStockActual(1);
        return p;
    }

    private static Factura factura(List<Factura> fs, String etiqueta) {
        return fs.stream().filter(f -> f.etiqueta().equals(etiqueta)).findFirst().orElseThrow(() -> new AssertionError(etiqueta + " en " + fs));
    }

    @Test
    void cadaCompraEsUnRenglon_yLoQueTienenOtrosDeLaFacturaSePropone() {
        List<Producto> todos = new ArrayList<>();
        for (int i = 0; i < 3; i++) todos.add(bien("BALA IP 4 MEGAPIXEL", "Seguridad Pública — C-2", "FAC 12345", null));
        todos.add(bien("POSTE DE MONITOREO", "Seguridad Pública — C-2", "FAC12345", null));
        todos.get(0).setProveedor("PROVEEDORA DE SEGURIDAD SA DE CV");
        todos.get(0).setFechaAdquisicion(LocalDate.of(2026, 1, 20));
        todos.get(1).setFechaAdquisicion(LocalDate.of(2026, 1, 20));
        todos.add(bien("SILLA", "Tesorería Municipal", null, null));   // no purchase: no row

        List<Factura> fs = CompletarPorFactura.facturas(todos);
        assertEquals(1, fs.size(), "«FAC 12345» y «FAC12345» son la misma factura");
        Factura f = fs.get(0);
        assertEquals("FAC 12345", f.etiqueta(), "como la escribe la mayoría");
        assertEquals(4, f.bienes().size());
        assertEquals(Map.of(Campo.PROVEEDOR, 3, Campo.FECHA, 2, Campo.FOLIO, 4), f.faltan());
        assertEquals("PROVEEDORA DE SEGURIDAD SA DE CV", f.propuestas().get(Campo.PROVEEDOR).valor());
        assertEquals("como SSPC/1", f.propuestas().get(Campo.PROVEEDOR).porQue());
        assertEquals(LocalDate.of(2026, 1, 20), f.propuestas().get(Campo.FECHA).valor());
        assertNull(f.propuestas().get(Campo.FOLIO));
        assertEquals("BALA IP 4 MEGAPIXEL (3), POSTE DE MONITOREO", f.nombres());
    }

    @Test
    void unNumeroCortoDeDosProveedores_sonDosCompras() {
        Producto grua = bien("GRUA", "Obras Públicas", "139", null);
        grua.setProveedor("GRUAS DEL CENTRO");
        grua.setFechaAdquisicion(LocalDate.of(2024, 3, 1));
        Producto cpu = bien("CPU", "Tesorería Municipal", "139", null);
        cpu.setFechaAdquisicion(LocalDate.of(2025, 8, 9));
        List<Factura> fs = CompletarPorFactura.facturas(List.of(grua, cpu));
        assertEquals(2, fs.size(), "«139» fue una grúa y un CPU (11-nov-2026)");
        assertTrue(fs.stream().noneMatch(f -> f.propuestas().containsKey(Campo.PROVEEDOR)), "al CPU no se le propone el de la grúa");
    }

    @Test
    void elFolioEscritoEnFactura_noFalta_loPasaLoSeguro() {
        List<Producto> camaras = new ArrayList<>();
        for (int i = 0; i < 3; i++) camaras.add(bien("BALA IP", "Seguridad Pública — C-2", UUID_C2, null));
        Factura f = CompletarPorFactura.facturas(camaras).get(0);
        assertEquals("Folio 3822637C…", f.etiqueta());
        assertEquals(MismaFactura.uuid(UUID_C2), f.uuid());
        assertFalse(f.faltan().containsKey(Campo.FOLIO), "«Folio fiscal a su lugar» lo mueve");
        assertEquals(3, f.faltan().get(Campo.PROVEEDOR));

        List<LoSeguro.Propuesta> seguro = LoSeguro.proponer(camaras);
        assertEquals(LoSeguro.Arreglo.FOLIO, seguro.get(0).arreglo());
        assertEquals(3, seguro.get(0).bienes().size());
    }

    @Test
    void conUnArea_soloSusBienes_yLaFechaOElFolioMalEscritoLoDice() {
        List<Producto> todos = new ArrayList<>();
        todos.add(bien("ESCRITORIO", "Tesorería Municipal", "F-MR-003343", null));
        todos.add(bien("ESCRITORIO", "Contraloría Municipal", "F-MR-03343", null));
        List<Factura> fs = CompletarPorFactura.facturas(todos, p -> "Tesorería Municipal".equals(p.getArea()), EnumSet.of(Campo.PROVEEDOR));
        assertEquals(1, fs.size());
        assertEquals(1, fs.get(0).bienes().size());
        assertEquals(2, fs.get(0).enLaCompra());
        assertEquals(Set.of(Campo.PROVEEDOR), fs.get(0).faltan().keySet());

        assertEquals("ACME SA DE CV", CompletarPorFactura.leer(Campo.PROVEEDOR, " acme  sa de cv "));
        assertNull(CompletarPorFactura.leer(Campo.FOLIO, "  "));
        IllegalArgumentException mal = assertThrows(IllegalArgumentException.class, () -> CompletarPorFactura.leer(Campo.FOLIO, "3822637C-8B6A-410A"));
        assertTrue(mal.getMessage().startsWith("El folio fiscal"), mal.getMessage());
        assertEquals(UUID_C2, CompletarPorFactura.leer(Campo.FOLIO, UUID_C2.toLowerCase()));
        assertThrows(IllegalArgumentException.class, () -> CompletarPorFactura.leerFecha(LocalDate.now().plusDays(3)));
    }

    @Test
    void alGuardar_soloLoQueFalta_ySuPdfDeOtraFacturaNoSeUsa() {
        List<Producto> todos = new ArrayList<>();
        for (int i = 0; i < 2; i++) todos.add(bien("SILLA", "Tesorería Municipal", "B 5630", null));
        todos.get(0).setProveedor("MUEBLES DEL VALLE");
        todos.add(bien("SILLA", "Tesorería Municipal", "B 5630", null));
        Factura f = CompletarPorFactura.facturas(todos).get(0);
        Map<Campo, Object> valores = new EnumMap<>(Campo.class);
        valores.put(Campo.PROVEEDOR, "OTRO PROVEEDOR");
        valores.put(Campo.FECHA, LocalDate.of(2025, 5, 2));
        Map<String, Producto> nuevo = new HashMap<>();
        CompletarPorFactura.renglones(f, valores).forEach(r -> nuevo.put(r.bien().getId(), r.nuevo()));
        assertEquals("MUEBLES DEL VALLE", nuevo.get(todos.get(0).getId()).getProveedor(), "el que ya tenía se queda");
        assertEquals("OTRO PROVEEDOR", nuevo.get(todos.get(1).getId()).getProveedor());
        assertEquals(LocalDate.of(2025, 5, 2), nuevo.get(todos.get(0).getId()).getFechaAdquisicion());

        var leida = new com.sibim.util.FacturaXml.Factura(UUID_C2, "B5630", LocalDate.of(2025, 5, 2), "Muebles del Valle SA de CV",
            "MVA010101AAA", "MXN", new BigDecimal("15080.00"), List.of());
        var d = CompletarPorFactura.deSuFactura(f, leida);
        assertNull(d.otraFactura(), "la factura no tenía folio: no hay con qué compararlo");
        assertEquals("MUEBLES DEL VALLE SA DE CV", d.valores().get(Campo.PROVEEDOR));
        assertEquals(UUID_C2, d.valores().get(Campo.FOLIO));
        assertTrue(d.total().contains("15,080.00"), d.total());

        todos.forEach(p -> p.setFolioFiscal("11111111-2222-4333-8444-555555555555"));
        todos.get(2).setFolioFiscal(null);
        Factura conFolio = CompletarPorFactura.facturas(todos).get(0);
        assertNotNull(CompletarPorFactura.deSuFactura(conFolio, leida).otraFactura(), "un PDF de otra factura se dice");
    }

    @Test
    void loSeguro_soloElResidualDeCategoriasConCriterio_yCadaArregloConSuEjemplo() {
        List<Producto> todos = new ArrayList<>();
        Producto conQuince = bien("ARCHIVERO", "Tesorería Municipal", null, null);
        conQuince.setCategoriaNombre("Muebles de Oficina y Estantería");
        conQuince.setPrecioCompra(new BigDecimal("1000"));
        conQuince.setValorResidual(new BigDecimal("150"));
        todos.add(conQuince);
        Producto sinResidual = bien("ESCRITORIO COLOR NOGAL", "Tesorería Municipal", null, null);
        sinResidual.setCategoriaNombre("Muebles de Oficina y Estantería");
        sinResidual.setPrecioCompra(new BigDecimal("2000"));
        sinResidual.setClaveArmonizada("1.2.4.1.1.511");
        todos.add(sinResidual);
        Producto defensa = bien("CHALECO", "Seguridad Pública — Área Operativa", null, null);
        defensa.setCategoriaNombre("Equipo de Defensa y Seguridad");
        defensa.setPrecioCompra(new BigDecimal("8000"));
        todos.add(defensa);

        Map<LoSeguro.Arreglo, LoSeguro.Propuesta> p = new EnumMap<>(LoSeguro.Arreglo.class);
        LoSeguro.proponer(todos).forEach(x -> p.put(x.arreglo(), x));
        assertEquals(Set.of(LoSeguro.Arreglo.TIPO, LoSeguro.Arreglo.RESIDUAL, LoSeguro.Arreglo.COLOR), p.keySet());
        assertEquals(List.of(sinResidual), p.get(LoSeguro.Arreglo.RESIDUAL).bienes(), "el chaleco no: en Defensa ninguno tiene el 15 %");
        assertTrue(p.get(LoSeguro.Arreglo.RESIDUAL).fuera().startsWith("1 bien queda fuera"), p.get(LoSeguro.Arreglo.RESIDUAL).fuera());
        assertTrue(p.get(LoSeguro.Arreglo.RESIDUAL).ejemplo().contains("$300.00"), p.get(LoSeguro.Arreglo.RESIDUAL).ejemplo());
        assertEquals("NOGAL", p.get(LoSeguro.Arreglo.COLOR).colores().get(sinResidual));
        assertEquals(3, LoSeguro.cambios(new ArrayList<>(p.values())));
    }
}
