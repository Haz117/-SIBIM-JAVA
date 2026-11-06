package com.sibim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 5-nov-2026: "quitar los cargos de las personas, los Lic., Ing., Arq., solo dejar el puro nombre".
 *  Names here are made up. */
class NombrePersonaTest {

    @Test
    void quitaElTituloDeAdelante_conOSinPunto() {
        assertEquals("ANA RUIZ SOTO", NombrePersona.sinTitulos("LIC. ANA RUIZ SOTO"));
        assertEquals("ANA RUIZ SOTO", NombrePersona.sinTitulos("Lic ANA RUIZ SOTO"));
        assertEquals("Juan Pérez", NombrePersona.sinTitulos("Ing. Juan Pérez"));
        assertEquals("Juan Pérez", NombrePersona.sinTitulos("Inge. Juan Pérez"));
        assertEquals("Juan Pérez", NombrePersona.sinTitulos("ARQ. Juan Pérez"));
        assertEquals("Juan Pérez", NombrePersona.sinTitulos("Arqui Juan Pérez"));
        assertEquals("ROSA LUNA", NombrePersona.sinTitulos("DRA. ROSA LUNA"));
        assertEquals("ROSA LUNA", NombrePersona.sinTitulos("C. ROSA LUNA"));
        assertEquals("ROSA LUNA", NombrePersona.sinTitulos("PROFRA. ROSA LUNA"));
    }

    @Test
    void tituloPegadoOConPuntosIntermedios() {
        assertEquals("LEIDY PRUEBA ORTEGA", NombrePersona.sinTitulos("LIC.LEIDY PRUEBA ORTEGA"));
        assertEquals("TAURINO EJEMPLO CRUZ", NombrePersona.sinTitulos("L.A.P TAURINO EJEMPLO CRUZ"));
        assertEquals("MARIANNE FICTICIA GUERRERO", NombrePersona.sinTitulos("L.D. MARIANNE FICTICIA GUERRERO"));
        assertEquals("JUAN PÉREZ", NombrePersona.sinTitulos("C. P. JUAN PÉREZ"));
        assertEquals("JUAN PÉREZ", NombrePersona.sinTitulos("ING. ARQ. JUAN PÉREZ"), "dos títulos seguidos");
    }

    @Test
    void soloLoDeAdelante_esTitulo() {
        assertEquals("JUAN C. PÉREZ", NombrePersona.sinTitulos("JUAN C. PÉREZ"), "la inicial de en medio se queda");
        assertEquals("LIC. JUAN C. PÉREZ".substring(5), NombrePersona.sinTitulos("LIC. JUAN C. PÉREZ"));
        assertEquals("LICONA MARTINEZ ANA", NombrePersona.sinTitulos("LICONA MARTINEZ ANA"), "un apellido que empieza igual no es título");
        assertEquals("INGRID SOLIS", NombrePersona.sinTitulos("INGRID SOLIS"));
        assertEquals("CARMEN DÍAZ", NombrePersona.sinTitulos("CARMEN DÍAZ"));
    }

    @Test
    void unPuestoSinNombre_seQuedaComoEsta() {
        assertEquals("ENCARGADO DE ALMACÉN", NombrePersona.sinTitulos("ENCARGADO DE ALMACÉN"));
        assertEquals("LIC.", NombrePersona.sinTitulos("LIC."), "nada después del título");
        assertEquals("COMANDANTE", NombrePersona.sinTitulos("COMANDANTE"));
    }

    @Test
    void dosPersonas_cadaUnaSinSuTitulo() {
        assertEquals("ANA RUIZ / JUAN PÉREZ", NombrePersona.sinTitulos("LIC. ANA RUIZ / ING. JUAN PÉREZ"));
        assertEquals("ANA RUIZ/JUAN PÉREZ", NombrePersona.sinTitulos("LIC. ANA RUIZ/ING. JUAN PÉREZ"));
    }

    @Test
    void sinTitulo_regresaElMismoTexto() {
        String nombre = "  ANA RUIZ  ";
        assertSame(nombre, NombrePersona.sinTitulos(nombre));
        assertNull(NombrePersona.sinTitulos(null));
        assertFalse(NombrePersona.tieneTitulo("ANA RUIZ"));
        assertTrue(NombrePersona.tieneTitulo("LIC. ANA RUIZ"));
        assertFalse(NombrePersona.tieneTitulo(null));
    }
}
