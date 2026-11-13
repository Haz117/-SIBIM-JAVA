package com.sibim.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/** With no logo configured the formatos print the one the app ships: until 13-nov-2026 it
 *  pointed to "/img/logo-municipio.png", which was never in the build, so etiquetas,
 *  resguardos and reportes came out without a logo. */
class LogoIncluidoTest {

    @Test
    void elLogoIncluido_existeYEsUnaImagen() throws Exception {
        assertNotNull(ReporteService.class.getResource(ReporteService.BUNDLED_LOGO), ReporteService.BUNDLED_LOGO + " no viene en la app");
        String ruta = ReporteService.bundledLogoPath();
        assertNotNull(ruta);
        var imagen = ImageIO.read(new File(ruta));
        assertNotNull(imagen, "debe ser una imagen que se pueda leer");
        assertTrue(imagen.getWidth() > 100 && imagen.getHeight() > 30, "con tamaño para un encabezado");
    }
}
