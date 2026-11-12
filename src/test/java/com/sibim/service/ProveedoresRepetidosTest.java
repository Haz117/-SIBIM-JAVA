package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService.ProveedorRepetido;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** «Unir proveedores» with the cases of 12-nov-2026. */
class ProveedoresRepetidosTest {

    private static List<Producto> con(String proveedor, int cuantos, List<Producto> en) {
        for (int i = 0; i < cuantos; i++) {
            Producto p = new Producto();
            p.setId(proveedor + i);
            p.setProveedor(proveedor);
            en.add(p);
        }
        return en;
    }

    @Test
    void elMismoProveedorConYSinSociedad_oConElNombreIncompleto_seUne_alNombreMasCompleto() {
        List<Producto> b = new ArrayList<>();
        con("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION SA DE CV", 24, b);
        con("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION", 23, b);
        con("SIMON PRUEBA SALVADOR SERRANO", 41, b);
        con("SIMON PRUEBA SALVADOR", 1, b);
        con("Hercom Computadoras de Hidalgo S.A. de C.V", 14, b);
        con("HERCOM COMPUTADORAS DE HIDALGO", 2, b);
        // Not the same: another company, or a name too short to tell.
        con("COMERCIALIZADORA KARACOM, S.A DE C.V", 2, b);
        con("COMERCIALIZADORA ZUCREL SA DE CV", 1, b);
        con("GRUPO META", 8, b);
        con("GRUPO META SOLUCIONES", 1, b);

        List<ProveedorRepetido> r = CalidadDatosService.proveedoresRepetidos(b);

        assertEquals(List.of(
            new ProveedorRepetido("HERCOM COMPUTADORAS DE HIDALGO", 2, "Hercom Computadoras de Hidalgo S.A. de C.V"),
            new ProveedorRepetido("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION", 23, "QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION SA DE CV"),
            new ProveedorRepetido("SIMON PRUEBA SALVADOR", 1, "SIMON PRUEBA SALVADOR SERRANO")), r);
    }

    @Test
    void laSociedadNoCuentaAlComparar() {
        assertEquals(CalidadDatosService.claveProveedor("GEIS SAN COSME S.A DE C.V."),
            CalidadDatosService.claveProveedor("Geis San Cosme"));
        assertEquals("JOSE FICTICIO DE LA LOMA LARA", CalidadDatosService.claveProveedor("José Ficticio de la Loma Lara"));
    }
}
