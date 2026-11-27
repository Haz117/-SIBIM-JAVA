package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 17-nov-2026: 8 of 8 sillas, 2 of 2 and 2 of 2 renamed one by one to what their factura
 *  says. The new name of one is offered to the bienes that had its old one. */
class NombreNuevoTest {

    private static final String ANTES = "SILLA EJECUTIVA PIEL IMITACION CON DESCANSABRAZOS";
    private static final String AHORA = "SILLA EJECUTIVA 4 TUNS POLIURETANO NEGRO.";

    private static Producto bien(String codigo, String nombre) {
        Producto p = new Producto();
        p.setId("id-" + codigo);
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setStockActual(1);
        return p;
    }

    @Test
    void losQueSeLlamabanIgual_sinElMismo_niLosDeBaja_niOtrosNombres() {
        Producto antes = bien("SIL/16", ANTES), guardado = antes.copia();
        guardado.setNombre(AHORA);
        Producto conAcentoYEspacios = bien("SIL/07", "  Silla ejecutiva piel imitación con descansabrazos ");
        Producto deBaja = bien("SIL/99", ANTES);
        deBaja.setFechaBaja(LocalDate.of(2026, 10, 1));
        Producto yaCambiada = bien("SIL/02", AHORA);
        Producto otra = bien("SIL/50", "SILLA EJECUTIVA");
        Producto otraAntes = bien("SIL/28", ANTES);

        List<Producto> todos = List.of(guardado, otraAntes, conAcentoYEspacios, deBaja, yaCambiada, otra);

        assertEquals(List.of("SIL/07", "SIL/28"), NombreNuevo.seLlamabanIgual(antes, guardado, todos).stream()
            .map(Producto::getCodigo).toList(), "en orden de número, sin acentos ni mayúsculas que los separen");
        assertTrue(NombreNuevo.seLlamabanIgual(null, guardado, todos).isEmpty(), "un bien nuevo no tenía nombre de antes");
        Producto soloMayusculas = antes.copia();
        soloMayusculas.setNombre(ANTES.toLowerCase());
        assertTrue(NombreNuevo.seLlamabanIgual(antes, soloMayusculas, todos).isEmpty(), "no cambió el nombre");
    }

    /** 18-nov-2026: «IMPRESORA MULTIFUNCIONAL» was the name of 10 bienes of 8 facturas. */
    @Test
    void losDeOtraCompraVanSinMarca_ySinDatosNadaDiceQueSeanDeOtra() {
        Producto guardado = bien("IMP/33", "IMPRESORA MULTIFUNCIONAL");
        guardado.setFolioFiscal("A0000000-0000-4000-8000-000000000001");
        guardado.setProveedor("PROVEEDOR FICTICIO");
        guardado.setFechaAdquisicion(LocalDate.of(2026, 5, 20));

        Producto sinDatos = bien("IMP/1", "IMPRESORA MULTIFUNCIONAL");
        Producto mismoFolioEnFactura = bien("IMP/2", "IMPRESORA MULTIFUNCIONAL");
        mismoFolioEnFactura.setNumeroFactura("a0000000-0000-4000-8000-000000000001");
        Producto otroFolio = bien("IMP/3", "IMPRESORA MULTIFUNCIONAL");
        otroFolio.setFolioFiscal("B0000000-0000-4000-8000-000000000002");
        Producto otroProveedorYFecha = bien("IMP/4", "IMPRESORA MULTIFUNCIONAL");
        otroProveedorYFecha.setProveedor("OTRO FICTICIO");
        otroProveedorYFecha.setFechaAdquisicion(LocalDate.of(2025, 1, 2));
        Producto soloOtraFecha = bien("IMP/5", "IMPRESORA MULTIFUNCIONAL");
        soloOtraFecha.setProveedor("Proveedor Ficticio");
        soloOtraFecha.setFechaAdquisicion(LocalDate.of(2026, 5, 24));

        assertFalse(NombreNuevo.deOtraCompra(guardado, sinDatos));
        assertFalse(NombreNuevo.deOtraCompra(guardado, mismoFolioEnFactura));
        assertTrue(NombreNuevo.deOtraCompra(guardado, otroFolio));
        assertTrue(NombreNuevo.deOtraCompra(guardado, otroProveedorYFecha));
        assertFalse(NombreNuevo.deOtraCompra(guardado, soloOtraFecha), "la misma factura con dos fechas pasa (5514: 24 y 27 de mayo)");

        Producto conNumero = bien("IMP/6", "X");
        conNumero.setNumeroFactura("B 5630");
        Producto otroNumero = bien("IMP/7", "X");
        otroNumero.setNumeroFactura("B5669");
        Producto mismoNumero = bien("IMP/8", "X");
        mismoNumero.setNumeroFactura("b-5630");
        assertTrue(NombreNuevo.deOtraCompra(conNumero, otroNumero));
        assertFalse(NombreNuevo.deOtraCompra(conNumero, mismoNumero), "el mismo número escrito de otra forma");

        // A generic name: only those of its factura come ticked, not the ones without data.
        List<Producto> otros = List.of(sinDatos, mismoFolioEnFactura, otroFolio, otroProveedorYFecha, soloOtraFecha);
        assertEquals(Set.of("IMP/2"), codigos(NombreNuevo.marcados(guardado, otros)));
        // No one says another purchase (the 8 sillas): all of them.
        assertEquals(Set.of("IMP/1", "IMP/2", "IMP/5"), codigos(NombreNuevo.marcados(guardado, List.of(sinDatos, mismoFolioEnFactura, soloOtraFecha))));
    }

    private static Set<String> codigos(Collection<Producto> bienes) {
        Set<String> out = new HashSet<>();
        bienes.forEach(p -> out.add(p.getCodigo()));
        return out;
    }

    @Test
    void seGuardaBienPorBien_yDeshacerRegresaElNombreDeAntes() {
        List<Producto> guardados = new ArrayList<>();
        ProductoService servicio = new ProductoService(mock(ProductoRepository.class), mock(AuditLogRepository.class)) {
            @Override public Producto save(Producto p) {
                guardados.add(p.copia());
                return p.copia();
            }
        };
        Producto a = bien("SIL/07", ANTES), b = bien("SIL/28", ANTES);
        a.setColor("NEGRO");

        var renglones = NombreNuevo.renglones(List.of(a, b), AHORA);
        assertEquals(List.of(EditarVarios.Dato.NOMBRE), renglones.get(0).cambian());
        var r = EditarVarios.aplicar(servicio, renglones);

        assertEquals(2, r.guardados().size());
        assertEquals(AHORA, guardados.get(0).getNombre());
        assertEquals("NEGRO", guardados.get(0).getColor(), "solo cambia el nombre");
        assertEquals(ANTES, a.getNombre(), "el bien de la lista no se toca");

        guardados.clear();
        assertEquals(2, EditarVarios.deshacer(servicio, r));
        assertEquals(ANTES, guardados.get(1).getNombre());
    }

    /** 24 and 25-nov-2026: «ESPOSAS SEMIRIGIDAS (MANO)» → «ESPOSAS (MANO)» offered for the other
     *  70 and declined six times, one esposas at a time as its photo showed. */
    @Test
    void soloAEste_seRecuerdaParaEseMismoCambio_yNoParaOtros() {
        String era = "ESPOSAS SEMIRIGIDAS (MANO)", ahora = "ESPOSAS (MANO)";
        assertFalse(NombreNuevo.yaDijoQueNo(null, era, ahora));
        String r = NombreNuevo.conRechazo(null, era, ahora);
        assertTrue(NombreNuevo.yaDijoQueNo(r, "esposas semirígidas  (mano)", "Esposas (mano)"),
            "el mismo cambio, escrito con otras mayúsculas, acentos o espacios");
        assertFalse(NombreNuevo.yaDijoQueNo(r, ahora, era), "el cambio de regreso es otro");
        assertFalse(NombreNuevo.yaDijoQueNo(r, era, "ESPOSAS (MANOS)"), "otro nombre nuevo sí se pregunta");

        String dos = NombreNuevo.conRechazo(r, "SILLA", "SILLA EJECUTIVA");
        assertTrue(NombreNuevo.yaDijoQueNo(dos, era, ahora) && NombreNuevo.yaDijoQueNo(dos, "SILLA", "SILLA EJECUTIVA"));
        assertEquals(dos, NombreNuevo.conRechazo(dos, "SILLA", "SILLA EJECUTIVA"), "repetirlo no lo duplica");
        assertEquals(r, NombreNuevo.conRechazo(r, "SILLA", "silla"), "si el nombre no cambió no hay nada que recordar");

        String muchos = null;
        for (int i = 0; i < NombreNuevo.RECORDADOS + 20; i++) muchos = NombreNuevo.conRechazo(muchos, "BIEN " + i, "COSA " + i);
        assertEquals(NombreNuevo.RECORDADOS, muchos.split("\n").length, "se quedan los más nuevos");
        assertTrue(NombreNuevo.yaDijoQueNo(muchos, "BIEN " + (NombreNuevo.RECORDADOS + 19), "COSA " + (NombreNuevo.RECORDADOS + 19)));
        assertFalse(NombreNuevo.yaDijoQueNo(muchos, "BIEN 0", "COSA 0"));
        assertTrue(muchos.length() <= java.util.prefs.Preferences.MAX_VALUE_LENGTH);
    }
}
