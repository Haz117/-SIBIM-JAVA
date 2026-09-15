package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Common base for controller smoke tests: activates demo mode, seeds a fake
 * admin session, and cleans up after each test class.
 *
 * On headless CI add to surefire argLine:
 *   -Dtestfx.headless=true -Dtestfx.robot=glass -Dprism.order=sw
 */
abstract class ControllerSmokeTestBase extends ApplicationTest {

    protected static Usuario adminUser() {
        Usuario u = new Usuario();
        u.setId("smoke-admin-1");
        u.setUsername("testadmin");
        u.setNombre("Test Admin");
        u.setRol(Rol.ADMIN);
        return u;
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }
}
