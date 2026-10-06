package com.sibim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

class FirmaActualizacionesTest {

    private static final String SHA = "a".repeat(64);
    private static final String OTRO_SHA = "b".repeat(64);

    @Test
    void laCompilacionTraeLaClavePublica() {
        assertTrue(FirmaActualizaciones.deEstaVersion().tieneClave(),
            "sin " + FirmaActualizaciones.RECURSO_CLAVE_PUBLICA + " las PCs nunca aceptarían una actualización");
    }

    @Test
    void laFirmaValeSoloParaEsaVersionYEseInstalador() throws Exception {
        KeyPair ti = par();
        var firmas = FirmaActualizaciones.conClave(ti.getPublic());
        String firma = FirmaActualizaciones.firmar(ti.getPrivate(), "1.2.0", SHA);

        assertTrue(firmas.valida("1.2.0", SHA, firma));
        assertFalse(firmas.valida("1.3.0", SHA, firma), "otra versión");
        assertFalse(firmas.valida("1.2.0", OTRO_SHA, firma), "otro instalador");
        assertFalse(FirmaActualizaciones.conClave(par().getPublic()).valida("1.2.0", SHA, firma), "otra llave");
        assertFalse(FirmaActualizaciones.conClave(null).valida("1.2.0", SHA, firma), "sin clave no vale nada");
    }

    @Test
    void datosMalFormados_noValenNiRompen() throws Exception {
        KeyPair ti = par();
        var firmas = FirmaActualizaciones.conClave(ti.getPublic());
        String firma = FirmaActualizaciones.firmar(ti.getPrivate(), "1.2.0", SHA);
        assertFalse(firmas.valida("1.2.0", SHA, null));
        assertFalse(firmas.valida("1.2.0", SHA, "no es base64 %%%"));
        assertFalse(firmas.valida("1.2.0", SHA.toUpperCase(), firma));
        assertFalse(firmas.valida("1.2", SHA, firma));
        assertFalse(firmas.valida(null, SHA, firma));
    }

    @Test
    void elArchivoFirmaIdaYVuelta(@TempDir Path dir) throws Exception {
        File instalador = dir.resolve("SIBIM-Desktop-1.2.0-win64-setup.exe").toFile();
        File archivo = FirmaActualizaciones.archivoFirma(instalador);
        assertEquals("SIBIM-Desktop-1.2.0-win64-setup.exe.firma", archivo.getName());
        var f = new FirmaActualizaciones.Firma("1.2.0", SHA, "abc+/=");
        FirmaActualizaciones.escribir(archivo, f);
        assertEquals(f, FirmaActualizaciones.leer(archivo));
    }

    private static KeyPair par() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }
}
