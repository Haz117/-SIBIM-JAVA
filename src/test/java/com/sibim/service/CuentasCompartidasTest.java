package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
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

import static org.junit.jupiter.api.Assertions.*;

/** Shared área accounts: only Patrimonio sets their password, and resetting
 *  it doesn't force a change (that would lock the rest of the área out). */
class CuentasCompartidasTest {

    private final UsuarioRepository repo = new UsuarioRepository();

    @BeforeEach
    void admin() throws Exception {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        SessionManager.setCurrentUser(usuario("u-admin", Rol.ADMIN, null, "Administrador"));
    }

    @AfterEach
    void limpiar() {
        SessionManager.logout();
        DemoDataStore.reiniciar();
        DatabaseConfig.setDemoMode(false);
    }

    @Test
    void restablecerLaDeUnArea_noObligaACambiarla() throws Exception {
        new CuentasAreaService().crearFaltantes();
        Usuario catastro = repo.findByUsername("catastro").orElseThrow();
        repo.updatePassword(catastro.getId(), "$2a$12$nuevohash");
        assertFalse(repo.findByUsername("catastro").orElseThrow().isDebeCambiarPassword());
    }

    @Test
    void elPersonalDelArea_noPuedeCambiarla() throws Exception {
        new CuentasAreaService().crearFaltantes();
        Usuario catastro = repo.findByUsername("catastro").orElseThrow();
        SessionManager.setCurrentUser(catastro);
        assertThrows(SecurityException.class, () -> repo.completarCambioPassword(catastro.getId(), "$2a$12$x"));
    }

    @Test
    void guiaRapida_cubreLoQueNecesitanLasAreas() throws Exception {
        File pdf = new ReporteGuiaRapidaService().exportGuia();
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            StringBuilder t = new StringBuilder();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) t.append(PdfTextExtractor.getTextFromPage(doc.getPage(i)));
            for (String s : new String[]{"ENTRAR AL SISTEMA", "SIN INTERNET", "TRANSFERENCIAS", "ACTUALIZACIONES",
                    "Ejecutar de todas formas"}) {
                assertTrue(t.toString().contains(s), "falta «" + s + "»");
            }
        } finally {
            pdf.delete();
        }
    }

    private static Usuario usuario(String id, Rol rol, String area, String nombre) {
        Usuario u = new Usuario();
        u.setId(id); u.setRol(rol); u.setArea(area); u.setNombre(nombre); u.setActivo(true);
        return u;
    }
}
