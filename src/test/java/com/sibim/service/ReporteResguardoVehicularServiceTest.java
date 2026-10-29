package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** RESGUARDO INDIVIDUAL · PARQUE VEHICULAR, the municipio's vehicle sheet. */
class ReporteResguardoVehicularServiceTest {

    @BeforeEach
    void entrar() {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario(); u.setId("rv-admin"); u.setNombre("Administrador"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
    }

    @AfterEach
    void salir() {
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    private static Producto frontier() {
        Producto p = new Producto();
        p.setId("rv-1"); p.setCodigo("PRES/73"); p.setNombre("CAMIONETA NISSAN FRONTIER 2023");
        p.setArea(com.sibim.config.Areas.PRESIDENCIA); p.setResguardante("T.S.U. Persona De Ejemplo");
        p.setTipoBien("Camioneta"); p.setMarca("NISSAN"); p.setModelo("FRONTIER 2023");
        p.setNumeroSerie("3N6AD00A0XX000000"); p.setNoMotor("QR00000000X"); p.setNumeroFactura("FAC001");
        p.setDescripcion("Inventario anterior: PM-000. Placas: XX0000X. Estatus: Funcionando. Tarjeta de circulación: sí.");
        p.setEstadoFisico("BUENO"); p.setPrecioCompra(new BigDecimal("100000.00")); p.setStockActual(1);
        return p;
    }

    /** The vehicle list put submarca and año together in modelo, and placas, estatus
     *  and tarjeta in the descripción: the sheet has a box for each. */
    @Test
    void separaLoQueLaListaDeVehiculosJunto() {
        var v = ReporteResguardoVehicularService.Vehiculo.de(frontier());
        assertEquals("FRONTIER", v.submarca());
        assertEquals("2023", v.anio());
        assertEquals("XX0000X", v.placas());
        assertEquals("TARJETA DE CIRCULACIÓN", v.documentos());
        assertEquals("ESTADO FÍSICO: BUENO · ESTATUS: FUNCIONANDO", v.observaciones(frontier()));

        Producto sinPlacas = frontier();
        sinPlacas.setModelo("GRUA CHASSIS CABINA 3500 2002");
        sinPlacas.setDescripcion("Placas: NO. Estatus: Descompuesta. Tarjeta de circulación: no.");
        var w = ReporteResguardoVehicularService.Vehiculo.de(sinPlacas);
        assertEquals("GRUA CHASSIS CABINA 3500", w.submarca());
        assertEquals("2002", w.anio());
        assertEquals("SIN PLACAS", w.placas());
        assertEquals("", w.documentos());
    }

    @Test
    void laFechaYLaSecretariaComoEnElFormato() {
        assertEquals("a los 15 días del mes de Octubre de 2026",
            ReporteResguardoVehicularService.fechaEnTexto(LocalDate.of(2026, 10, 15)));
        assertEquals("a los 1 día del mes de Noviembre de 2026",
            ReporteResguardoVehicularService.fechaEnTexto(LocalDate.of(2026, 11, 1)));
        assertEquals("PRESIDENCIA MUNICIPAL",
            ReporteResguardoVehicularService.secretariaYArea(com.sibim.config.Areas.PRESIDENCIA)[0]);
    }

    /** One sheet per vehicle, with the folio and the date of the resguardo printed. */
    @Test
    void unaHojaPorVehiculo() throws Exception {
        Producto otro = frontier(); otro.setId("rv-2"); otro.setCodigo("PRES/74");
        Resguardo r = new Resguardo();
        r.setNumero("RSG-2026-000001"); r.setResguardanteNombre("T.S.U. Persona De Ejemplo");
        r.setResguardanteCargo("Directora de Ejemplo"); r.setEstado(Resguardo.ESTADO_ACTIVO);
        r.setCreadoEn(LocalDateTime.of(2026, 10, 15, 9, 0));
        File pdf = new ReporteResguardoVehicularService().exportar(List.of(frontier(), otro), r);
        try (var d = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            assertEquals(2, d.getNumberOfPages(), "cada vehículo en su hoja, sin pasarse a otra");
            String t = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(1));
            assertTrue(t.contains("RESGUARDO INDIVIDUAL") && t.contains("PARQUE VEHICULAR"), t);
            assertTrue(t.contains("RSG-2026-000001") && t.contains("15-oct-26"), t);
            assertTrue(t.contains("XX0000X") && t.contains("FRONTIER") && t.contains("DIRECTORA DE EJEMPLO"), t);
            assertTrue(t.contains("a los 15 días del mes de Octubre de 2026"), t);
        }
        assertNull(new ReporteResguardoVehicularService().exportar(List.of()));
    }
}
