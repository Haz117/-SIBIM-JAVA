package com.sibim.db.offline;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que findCachedUserByUsername rechaza entradas cuyo cached_at
 * sea más antiguo que OFFLINE_CACHE_TTL_DAYS.
 *
 * Llama primero a cacheUser() (API pública) para inicializar la conexión
 * SQLite y correr las migraciones, y solo después hace UPDATEs directos
 * para simular entradas expiradas.
 */
class OfflineUserCacheExpiryTest {

    private static final String FRESH_ID   = "cache-expiry-fresh";
    private static final String EXPIRED_ID = "cache-expiry-expired";

    @BeforeEach
    void setUp() throws Exception {
        // Insertar usuarios via la API pública — esto inicializa la conexión y corre M4.
        OfflineStore.cacheUser(buildUser(FRESH_ID, "expiry_fresh"));
        OfflineStore.cacheUser(buildUser(EXPIRED_ID, "expiry_expired"));

        // Hacer expirar la segunda entrada actualizando cached_at directamente.
        String expiredAt = LocalDateTime.now()
            .minusDays(OfflineStore.OFFLINE_CACHE_TTL_DAYS + 1)
            .toString();
        setUserCachedAt(EXPIRED_ID, expiredAt);
    }

    @Test
    void expiredCacheEntry_returnsEmpty() throws Exception {
        Optional<Usuario> result = OfflineStore.findCachedUserByUsername("expiry_expired");
        assertTrue(result.isEmpty(),
            "Una entrada de caché con más de " + OfflineStore.OFFLINE_CACHE_TTL_DAYS
                + " días debe ser rechazada");
    }

    @Test
    void freshCacheEntry_returnsUser() throws Exception {
        Optional<Usuario> result = OfflineStore.findCachedUserByUsername("expiry_fresh");
        assertTrue(result.isPresent(), "Una entrada reciente debe ser aceptada");
        assertEquals("expiry_fresh", result.get().getUsername());
    }

    @Test
    void cacheUser_writesCurrentTimestamp() throws Exception {
        String username = "expiry_timestamp_verify";
        OfflineStore.cacheUser(buildUser("cache-ts-verify", username));

        Optional<Usuario> found = OfflineStore.findCachedUserByUsername(username);
        assertTrue(found.isPresent(), "El usuario recién cacheado debe encontrarse");
    }

    private static Usuario buildUser(String id, String username) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setPasswordHash("$2a$12$fakehashvalue");
        u.setNombre("Test User");
        u.setRol(Rol.SECRETARIO);
        return u;
    }

    /** Actualiza cached_at directamente en SQLite para simular una entrada expirada. */
    private static void setUserCachedAt(String userId, String cachedAt) throws Exception {
        Connection conn = OfflineStore.sharedConnection();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users_cache SET cached_at = ? WHERE id = ?")) {
            ps.setString(1, cachedAt);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }
}
