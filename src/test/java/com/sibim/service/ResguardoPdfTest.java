package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The PDF of a resguardo from the Resguardos screen, as it was printed on 13-nov-2026 for
 *  RSG-2026-000355: its bienes came in a random order and 30 of 35 said «$0.00». */
class ResguardoPdfTest {

    @BeforeEach
    void entrar() {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("u-1"); u.setNombre("Patrimonio"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
    }

    @AfterEach
    void salir() {
        SessionManager.logout();
        DemoDataStore.reiniciar();
        DatabaseConfig.setDemoMode(false);
    }

    private static ResguardoItem renglon(String codigo, String nombre, String valor) {
        ResguardoItem it = new ResguardoItem();
        it.setProductoCodigo(codigo);
        it.setProductoNombre(nombre);
        it.setArea("Mercado Municipal");
        it.setCantidad(1);
        it.setValorUnitario(valor == null ? null : new BigDecimal(valor));
        return it;
    }

    @Test
    void enOrdenDeNumero_ySinPrecioNoDiceCeroPesos() throws Exception {
        Resguardo r = new Resguardo();
        r.setNumero("RSG-PRUEBA-1");
        r.setResguardanteNombre("JUAN CARLOS FICTICIO CORTES");
        r.setResguardanteCargo("TESORERO DEL MERCADO");
        r.setResguardanteArea("Mercado Municipal");
        r.setEstado(Resguardo.ESTADO_ACTIVO);
        r.setItems(List.of(renglon("MM/10", "TORNIQUETE", "203000"), renglon("MM/2", "ARCHIVERO", "0"),
            renglon("MM/01", "ESCRITORIO", null)));

        File pdf = new ResguardoService().exportarPdf(r);
        String texto;
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(doc.getPage(i)));
            texto = sb.toString();
        }

        assertTrue(texto.indexOf("MM/01") < texto.indexOf("MM/2") && texto.indexOf("MM/2") < texto.indexOf("MM/10"),
            "en orden de número:\n" + texto);
        assertFalse(texto.contains("$0.00"), "sin precio no es $0.00:\n" + texto);
        assertTrue(texto.contains("$203,000.00"));
        assertTrue(texto.contains("hay bienes sin precio capturado"), texto);
    }
}
