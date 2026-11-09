package com.sibim.service;

import com.sibim.model.Resguardo;
import com.sibim.service.TitularesArea.Candidato;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The suggestions for the titular of each área (Datos de los formatos, 9-nov-2026):
 *  who signs resguardos there with a cargo that heads it. Fictitious names. */
class TitularesAreaCandidatosTest {

    private static Resguardo r(String area, String nombre, String cargo, boolean activo) {
        Resguardo x = new Resguardo();
        x.setResguardanteArea(area);
        x.setResguardanteNombre(nombre);
        x.setResguardanteCargo(cargo);
        x.setEstado(activo ? Resguardo.ESTADO_ACTIVO : "CANCELADO");
        return x;
    }

    @Test
    void elQueNombraElAreaVaPrimero_yLuegoElCargoMasDeJefe() {
        var c = TitularesArea.candidatos(List.of(
            r("Dirección de Catastro", "ANA PRUEBA UNO", "COORDINADORA TÉCNICA", true),
            r("Dirección de Catastro", "LIC. BETO PRUEBA DOS", "DIRECTOR DE CATASTRO", true),
            r("Dirección de Catastro", "CARLA PRUEBA TRES", "JEFA DE OFICINA", true),
            r("Dirección de Catastro", "DANIEL PRUEBA CUATRO", "AUXILIAR ADMINISTRATIVO", true)));
        List<Candidato> cat = c.get("Dirección de Catastro");
        assertEquals(List.of("BETO PRUEBA DOS", "ANA PRUEBA UNO", "CARLA PRUEBA TRES"),
            cat.stream().map(Candidato::nombre).toList(), "sin títulos y el auxiliar no aparece");
        assertEquals("BETO PRUEBA DOS · DIRECTOR DE CATASTRO", cat.get(0).texto());
    }

    @Test
    void unaSecretariaSolaEsUnPuesto_noLaTitular() {
        assertEquals(-1, TitularesArea.peso("SECRETARIA"));
        assertEquals(-1, TitularesArea.peso("Secretaria ejecutiva"));
        assertTrue(TitularesArea.peso("SECRETARIO GENERAL MUNICIPAL") >= 0);
        assertTrue(TitularesArea.peso("Secretaria de Bienestar Social") >= 0);
        assertEquals(0, TitularesArea.peso("TITULAR DE LA CONTRALORÍA MUNICIPAL"));
        assertTrue(TitularesArea.peso("SUBDIRECTORA") > TitularesArea.peso("DIRECTORA"), "subdirector no es director");
        assertEquals(-1, TitularesArea.peso("POLICIA 3RO"));
        assertTrue(TitularesArea.peso("JEFE DE INVENTARIOS") >= 0, "jefe y jefa");
        assertTrue(TitularesArea.peso("Jefa de oficina") >= 0);
    }

    @Test
    void laMismaPersonaUnaVez_ySoloDeResguardosVigentes() {
        var c = TitularesArea.candidatos(List.of(
            r("Contraloría Municipal", "EVA PRUEBA CINCO", "TITULAR DE LA CONTRALORÍA", true),
            r("Contraloría Municipal", "Eva  Prueba Cinco", "TITULAR DE LA CONTRALORIA MUNICIPAL", true),
            r("Contraloría Municipal", "FER PRUEBA SEIS", "DIRECTOR", false)));
        assertEquals(1, c.get("Contraloría Municipal").size());
        assertNull(c.get("Archivo Municipal"), "un área sin resguardos con cargo de jefe no tiene sugerencias");
    }

    @Test
    void nombraArea_sinContarPalabrasVacias() {
        assertTrue(TitularesArea.nombraArea("DIRECTOR DE CATASTRO", "Dirección de Catastro"));
        assertFalse(TitularesArea.nombraArea("DIRECTOR MUNICIPAL", "Dirección de Catastro"), "«municipal» y «dirección» no cuentan");
        assertTrue(TitularesArea.nombraArea("DIRECTORA DEL DIF MUNICIPAL", "Sistema DIF Municipal"));
    }

    @Test
    void masDeTres_noSeSugieren() {
        var c = TitularesArea.candidatos(List.of(
            r("Tesorería Municipal", "UNO PRUEBA", "DIRECTOR", true), r("Tesorería Municipal", "DOS PRUEBA", "DIRECTOR", true),
            r("Tesorería Municipal", "TRES PRUEBA", "DIRECTOR", true), r("Tesorería Municipal", "CUATRO PRUEBA", "DIRECTOR", true)));
        assertEquals(3, c.get("Tesorería Municipal").size());
    }
}
