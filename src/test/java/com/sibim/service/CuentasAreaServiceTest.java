package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.config.AreaCatalog;
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
        assertEquals("tesoreria.administracion", CuentasAreaService.nombreUsuario("Tesorería — Administración"));
        assertEquals("sipinna", CuentasAreaService.nombreUsuario("SIPINNA"));
        assertEquals("seguridad.publica", CuentasAreaService.nombreUsuario("Secretaría de Seguridad Pública y Tránsito"));
        assertEquals("seguridad.c2", CuentasAreaService.nombreUsuario("Seguridad Pública — C-2"));
        assertEquals("seguridad.prevencion.delito", CuentasAreaService.nombreUsuario("Seguridad Pública — Prevención del Delito"));
    }

    /** Production's organigrama (2026-10-12): Seguridad Pública's nine
     *  direcciones and its secretaría all shortened to "seguridad.publica",
     *  so only one account was created and nine áreas were left without one. */
    @Test
    void areasQueSeAbrevianIgual_tienenCadaUnaSuCuenta() throws Exception {
        AreaCatalog antes = Areas.catalogo();
        String ssp = "Secretaría de Seguridad Pública y Tránsito";
        AreaCatalog c = antes.con(new AreaCatalog.Entrada(ssp, AreaCatalog.Grupo.SECRETARIA, null, "SSP"));
        String[][] dirs = {{"C-2", "SSPC"}, {"Prevención del Delito", "SSPPD"}, {"SITE", "SSPST"},
            {"Central de Radio", "SSPCR"}, {"Sobre Guardia", "SSPSG"}, {"Área Operativa", "SSPAO"},
            {"Plataforma México", "SSPPM"}, {"Área Administrativa", "SSPAD"}, {"Armamento", "SSPAR"}};
        for (String[] d : dirs)
            c = c.con(new AreaCatalog.Entrada("Seguridad Pública — " + d[0], AreaCatalog.Grupo.DIRECCION, ssp, d[1]));
        // Two names no rule tells apart: the second one gets its prefix.
        c = c.con(new AreaCatalog.Entrada("Dirección de Obras Públicas", AreaCatalog.Grupo.DIRECCION,
            "Secretaría de Obras Públicas", "DOP"));
        Areas.usar(c);
        try {
            List<CuentasAreaService.Cuenta> cuentas = new CuentasAreaService().crearFaltantes();

            assertEquals(c.entradas().size(), cuentas.size());
            assertTrue(cuentas.stream().allMatch(CuentasAreaService.Cuenta::nueva), "todas nuevas: " + cuentas);
            assertEquals(cuentas.size(), new HashSet<>(cuentas.stream().map(CuentasAreaService.Cuenta::usuario).toList()).size(),
                "nombres de usuario únicos");
            Map<String, String> usuario = cuentas.stream()
                .collect(Collectors.toMap(CuentasAreaService.Cuenta::area, CuentasAreaService.Cuenta::usuario));
            assertEquals("seguridad.publica", usuario.get(ssp));
            assertEquals("seguridad.c2", usuario.get("Seguridad Pública — C-2"));
            assertEquals("obras.publicas", usuario.get("Secretaría de Obras Públicas"));
            assertEquals("obras.publicas.dop", usuario.get("Dirección de Obras Públicas"));
            for (CuentasAreaService.Cuenta cuenta : cuentas)
                assertEquals(cuenta.area(), new UsuarioRepository().findByUsername(cuenta.usuario()).orElseThrow().getArea());
        } finally {
            Areas.usar(antes);
        }
    }

    @Test
    void unaCuentaPersonalConElMismoNombre_noSeTomaPorLaDelArea() throws Exception {
        Usuario personal = new Usuario();
        personal.setUsername("catastro"); personal.setNombre("Juan Pérez"); personal.setRol(Rol.ADMIN);
        personal.setArea("Recursos Materiales y Patrimonio"); personal.setActivo(true);
        personal.setPasswordHash("x");
        new UsuarioRepository().save(personal);

        CuentasAreaService.Cuenta catastro = new CuentasAreaService().crearFaltantes().stream()
            .filter(c -> c.area().equals("Dirección de Catastro")).findFirst().orElseThrow();

        assertTrue(catastro.nueva());
        assertEquals("catastro.dcat", catastro.usuario());
        assertEquals(Rol.DIRECCION, new UsuarioRepository().findByUsername("catastro.dcat").orElseThrow().getRol());
    }
}
