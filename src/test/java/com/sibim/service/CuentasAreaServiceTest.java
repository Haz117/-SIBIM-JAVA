package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CuentasAreaServiceTest {

    @BeforeEach
    void admin() {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        Usuario u = new Usuario();
        u.setId("u-admin"); u.setUsername("superusuario"); u.setNombre("Admin"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
    }

    @AfterEach
    void limpiar() {
        SessionManager.logout();
        DemoDataStore.reiniciar();
        DatabaseConfig.setDemoMode(false);
    }

    @Test
    void creaUnaCuentaPorAreaConElRolQueLeToca() throws Exception {
        List<CuentasAreaService.Cuenta> cuentas = new CuentasAreaService().crearFaltantes();

        assertEquals(Areas.catalogo().entradas().size(), cuentas.size());
        assertEquals(cuentas.size(), new HashSet<>(cuentas.stream().map(CuentasAreaService.Cuenta::usuario).toList()).size(),
            "nombres de usuario únicos");
        Map<String, CuentasAreaService.Cuenta> porArea = cuentas.stream()
            .collect(Collectors.toMap(CuentasAreaService.Cuenta::area, c -> c));
        assertEquals(Rol.ADMIN, porArea.get("Recursos Materiales y Patrimonio").rol());
        assertEquals("patrimonio", porArea.get("Recursos Materiales y Patrimonio").usuario());
        assertEquals(Rol.SECRETARIO, porArea.get("Tesorería Municipal").rol());
        assertEquals(Rol.DIRECCION, porArea.get("Dirección de Catastro").rol());
        assertEquals("catastro", porArea.get("Dirección de Catastro").usuario());

        CuentasAreaService.Cuenta catastro = porArea.get("Dirección de Catastro");
        assertTrue(catastro.contrasena().length() >= 8);
        Usuario guardado = new UsuarioRepository().findByUsername("catastro").orElseThrow();
        assertEquals("Dirección de Catastro", guardado.getArea());
        assertFalse(guardado.isDebeCambiarPassword(), "cuenta compartida: no obliga a cambiarla");
    }

    @Test
    void segundaVez_noTocaLasQueYaExisten() throws Exception {
        new CuentasAreaService().crearFaltantes();
        List<CuentasAreaService.Cuenta> otra = new CuentasAreaService().crearFaltantes();
        assertTrue(otra.stream().noneMatch(CuentasAreaService.Cuenta::nueva));
        assertTrue(otra.stream().allMatch(c -> c.contrasena() == null));
    }

    @Test
    void soloElAdministrador() {
        Usuario u = new Usuario();
        u.setId("u-dir"); u.setRol(Rol.DIRECCION); u.setArea("Dirección de Catastro");
        SessionManager.setCurrentUser(u);
        assertThrows(SecurityException.class, () -> new CuentasAreaService().crearFaltantes());
    }

    @Test
    void pdfConLasCuentas() throws Exception {
        List<CuentasAreaService.Cuenta> cuentas = new CuentasAreaService().crearFaltantes();
        File pdf = new ReporteCuentasService().exportCuentas(cuentas);
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            StringBuilder texto = new StringBuilder();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) texto.append(PdfTextExtractor.getTextFromPage(doc.getPage(i)));
            assertTrue(texto.toString().contains("catastro"));
            assertTrue(texto.toString().contains(cuentas.get(0).contrasena()));
        } finally {
            pdf.delete();
        }
    }

    @Test
    void nombresDeUsuarioLegibles() {
        assertEquals("servicios.publicos", CuentasAreaService.nombreUsuario("Servicios Públicos y Limpias"));
        assertEquals("tesoreria.egresos", CuentasAreaService.nombreUsuario("Tesorería — Egresos"));
        assertEquals("sipinna", CuentasAreaService.nombreUsuario("SIPINNA"));
    }
}
