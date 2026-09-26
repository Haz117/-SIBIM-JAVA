package com.sibim.db.integration;

import com.sibim.repository.LoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptRepositoryIntegrationTest extends IntegrationTestBase {

    private static final long VENTANA_MS = 15 * 60_000L;
    private final LoginAttemptRepository repo = new LoginAttemptRepository();

    @BeforeEach
    void limpiar() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("DELETE FROM login_attempts");
        }
    }

    @Test
    void cincoFallos_bloqueanConMinutosRestantes() throws Exception {
        for (int i = 0; i < 4; i++) repo.registrarFallo("ana", VENTANA_MS);
        assertEquals(0, repo.minutosBloqueo("ana", 5, VENTANA_MS), "4 fallos todavía no bloquean");

        repo.registrarFallo("ana", VENTANA_MS);
        long mins = repo.minutosBloqueo("ana", 5, VENTANA_MS);
        assertTrue(mins >= 14 && mins <= 15, "quedan ~15 minutos, fue " + mins);
        assertEquals(0, repo.minutosBloqueo("otro", 5, VENTANA_MS));
    }

    @Test
    void ventanaVencida_reiniciaElConteo() throws Exception {
        for (int i = 0; i < 5; i++) repo.registrarFallo("beto", VENTANA_MS);
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE login_attempts SET ventana_inicio = NOW() - INTERVAL '20 minutes' WHERE username = 'beto'");
        }
        assertEquals(0, repo.minutosBloqueo("beto", 5, VENTANA_MS), "la ventana ya venció");

        repo.registrarFallo("beto", VENTANA_MS);
        for (int i = 0; i < 3; i++) repo.registrarFallo("beto", VENTANA_MS);
        assertEquals(0, repo.minutosBloqueo("beto", 5, VENTANA_MS), "el fallo tras la ventana empieza de 1");
    }

    @Test
    void limpiar_desbloquea() throws Exception {
        for (int i = 0; i < 5; i++) repo.registrarFallo("carla", VENTANA_MS);
        repo.limpiar("carla");
        assertEquals(0, repo.minutosBloqueo("carla", 5, VENTANA_MS));
    }
}
