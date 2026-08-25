package com.sibim.controller;

import com.sibim.model.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguracionControllerTest {

    @Test
    void canDeleteUser_rejectsNullOrSameUser() {
        Usuario current = new Usuario();
        current.setId("10");

        Usuario same = new Usuario();
        same.setId("10");

        Usuario other = new Usuario();
        other.setId("20");

        assertFalse(ConfiguracionController.canDeleteUser(null, current));
        assertFalse(ConfiguracionController.canDeleteUser(same, current));
        assertFalse(ConfiguracionController.canDeleteUser(other, null));
        assertTrue(ConfiguracionController.canDeleteUser(other, current));
    }

    @Test
    void canUseDatabaseBackup_rejectsOfflineAndDemoModes() {
        assertFalse(ConfiguracionController.canUseDatabaseBackup(true, false));
        assertFalse(ConfiguracionController.canUseDatabaseBackup(false, true));
        assertFalse(ConfiguracionController.canUseDatabaseBackup(true, true));
        assertTrue(ConfiguracionController.canUseDatabaseBackup(false, false));
    }
}
