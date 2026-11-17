package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.CalidadDatosService.Dato;
import com.sibim.service.DelMismoNombre.Propuesta;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** «Tomar de los del mismo nombre» with cases like the inventory of 17-nov-2026. */
class DelMismoNombreTest {

    private int n;

    private Producto bien(String nombre, String area) {
        Producto p = new Producto();
        p.setId("id" + (++n));
        p.setCodigo("ARCH/" + n);
        p.setNombre(nombre);
        p.setArea(area);
        p.setStockActual(1);
        return p;
    }

    @Test
    void todosLosDelNombreDicenLoMismo_losQueNoLoTienenLoToman() {
        Producto con = bien("ANAQUELES COLOR AZUL, PARA CAJAS DEL ARCHIVO MUNICIPAL", "Archivo Municipal");
        con.setPrecioCompra(new BigDecimal("24743.60"));
        List<Producto> sin = new ArrayList<>();
        for (int i = 0; i < 5; i++) sin.add(bien("Anaqueles color azul para cajas del archivo municipal", "Archivo Municipal"));
        List<Producto> todos = new ArrayList<>(sin);
        todos.add(con);

        List<Propuesta> p = DelMismoNombre.propuestas(todos, Dato.PRECIO);
        assertEquals(5, p.size(), "el nombre se compara sin mayúsculas ni comas");
        assertTrue(p.stream().allMatch(x -> new BigDecimal("24743.60").compareTo((BigDecimal) x.valor()) == 0));
        assertEquals(List.of(con.getCodigo()), p.get(0).de());
    }

    @Test
    void siDicenDistinto_oEsUnoSolo_noSePropone() {
        Producto a = bien("VENTILADOR DE TORRE", "Despacho de Presidencia");
        a.setFechaAdquisicion(LocalDate.of(2026, 6, 10));
        Producto b = bien("VENTILADOR DE TORRE", "Dirección Jurídica");
        b.setFechaAdquisicion(LocalDate.of(2024, 2, 1));
        Producto c = bien("VENTILADOR DE TORRE", "Contraloría Municipal");
        Producto solo = bien("PODIUM CON ATRIL DE MADERA", "Dirección de Cultura");
        assertTrue(DelMismoNombre.propuestas(List.of(a, b, c, solo), Dato.FECHA).isEmpty());
    }

    @Test
    void unaFacturaQueNoEsFactura_noSePasa_niDeUnBienDadoDeBaja() {
        Producto a = bien("TELEVISION DE 65", "Comunicación Social");
        a.setNumeroFactura("SIN DOCUMENTO");
        Producto b = bien("TELEVISION DE 65", "Oficialía Mayor");
        assertTrue(DelMismoNombre.propuestas(List.of(a, b), Dato.FACTURA).isEmpty());
        a.setNumeroFactura("F12F37AA-7DBD-441C-A07C-B9F475956592");
        assertTrue(DelMismoNombre.propuestas(List.of(a, b), Dato.FACTURA).isEmpty(), "un folio fiscal en «Factura» no se pasa");

        Producto c = bien("SILLA SEMI EJECUTIVA", "Tesorería");
        c.setProveedor("MUEBLES DE EJEMPLO SA DE CV");
        c.setFechaBaja(LocalDate.of(2026, 11, 3));
        Producto d = bien("SILLA SEMI EJECUTIVA", "Tesorería");
        assertTrue(DelMismoNombre.propuestas(List.of(c, d), Dato.PROVEEDOR).isEmpty(), "uno de baja no cuenta");
        assertTrue(DelMismoNombre.propuestas(List.of(c, d), Dato.COLOR).isEmpty(), "el color no es de esta herramienta");
    }

    @Test
    void elProveedorSeComparaSinAcentos_yQuedaComoLoEscribieron() {
        Producto a = bien("SILLA OPERATIVA", "Archivo Municipal");
        a.setProveedor("GABRIELA PRUEBA JUÁREZ");
        Producto b = bien("SILLA OPERATIVA", "Tesorería");
        b.setProveedor("gabriela prueba juarez ");
        Producto c = bien("SILLA OPERATIVA", "Contraloría");
        List<Propuesta> p = DelMismoNombre.propuestas(List.of(a, b, c), Dato.PROVEEDOR);
        assertEquals(1, p.size());
        assertEquals(c, p.get(0).bien());
        assertEquals("GABRIELA PRUEBA JUÁREZ", p.get(0).valor());
    }

    @Test
    void seGuardaBienPorBien_yDeshacerLoRegresa() {
        List<Producto> guardados = new ArrayList<>();
        ProductoService servicio = new ProductoService(mock(ProductoRepository.class), mock(AuditLogRepository.class)) {
            @Override public Producto save(Producto p) {
                guardados.add(p.copia());
                return p.copia();
            }
        };
        Producto con = bien("ESCRITORIO 1.20 M", "Contraloría");
        con.setVidaUtilAnios(5);
        con.setUbicacion("OFICINA");
        Producto sin1 = bien("ESCRITORIO 1.20 M", "Dirección Jurídica");
        sin1.setUbicacion("BODEGA");
        Producto sin2 = bien("ESCRITORIO 1.20 M", "Dirección Jurídica");
        List<Propuesta> p = DelMismoNombre.propuestas(List.of(con, sin1, sin2), Dato.VIDA_UTIL);

        var res = EditarVarios.aplicar(servicio, DelMismoNombre.renglones(Dato.VIDA_UTIL, List.of(p.get(0))));
        assertEquals(1, guardados.size(), "solo el que quedó marcado");
        assertEquals(5, guardados.get(0).getVidaUtilAnios());
        assertEquals("BODEGA", guardados.get(0).getUbicacion());

        guardados.clear();
        assertEquals(1, EditarVarios.deshacer(servicio, res));
        assertNull(guardados.get(0).getVidaUtilAnios());
    }

    @Test
    void porHacer_losCuenta_yAbreEnElQueMasTiene() {
        List<Producto> bienes = new ArrayList<>();
        Producto f = bien("PIZARRON BLANCO", "Catastro");
        f.setNumeroFactura("5531");
        f.setPrecioCompra(new BigDecimal("5853.50"));
        bienes.add(f);
        for (int i = 0; i < 3; i++) bienes.add(bien("PIZARRON BLANCO", "Jurídico"));
        Producto g = bien("GPS", "Catastro");
        g.setNumeroFactura("290");
        bienes.add(g);
        Producto h = bien("GPS", "Catastro");
        h.setNumeroFactura("290");
        h.setPrecioCompra(new BigDecimal("100"));
        bienes.add(h);

        assertEquals(Dato.PRECIO, DelMismoNombre.conMas(bienes), "4 costos contra 3 facturas");
        var item = CalidadDatosService.porHacer(bienes, List.of(), Set.of(), Map.of()).stream()
            .filter(x -> "mismonombre".equals(x.abrir())).findFirst().orElseThrow();
        assertEquals(7, item.cantidad());
    }
}
