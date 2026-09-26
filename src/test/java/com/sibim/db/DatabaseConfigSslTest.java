package com.sibim.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConfigSslTest {

    // ── separarCredenciales: user/password en DB_URL ─────────────────────────

    @Test void urlConCredenciales_seLimpiaYLasDevuelve() {
        var r = DatabaseConfig.separarCredenciales(
            "jdbc:postgresql://pooler.example.com:5432/postgres?user=postgres.ref&password=s3cr%26to&sslmode=require");
        assertEquals("jdbc:postgresql://pooler.example.com:5432/postgres?sslmode=require", r.url());
        assertEquals("postgres.ref", r.user());
        assertEquals("s3cr&to", r.password());
    }

    @Test void urlSoloConCredenciales_quedaSinQuery() {
        var r = DatabaseConfig.separarCredenciales("jdbc:postgresql://h:5432/db?user=u&password=p");
        assertEquals("jdbc:postgresql://h:5432/db", r.url());
    }

    @Test void urlSinCredenciales_noCambia() {
        var r = DatabaseConfig.separarCredenciales("jdbc:postgresql://h:5432/db?sslmode=require");
        assertEquals("jdbc:postgresql://h:5432/db?sslmode=require", r.url());
        assertNull(r.user());
        assertNull(r.password());
    }

    // ── isRemoteUrl ───────────────────────────────────────────────────────────

    @Test void localhost_isLocal() {
        assertFalse(DatabaseConfig.isRemoteUrl("jdbc:postgresql://localhost:5432/sibim"));
    }

    @Test void loopbackIp_isLocal() {
        assertFalse(DatabaseConfig.isRemoteUrl("jdbc:postgresql://127.0.0.1:5432/sibim"));
    }

    @Test void ipv6Loopback_isLocal() {
        assertFalse(DatabaseConfig.isRemoteUrl("jdbc:postgresql://[::1]:5432/sibim"));
    }

    @Test void supabase_isRemote() {
        assertTrue(DatabaseConfig.isRemoteUrl("jdbc:postgresql://db.example.supabase.co:5432/postgres"));
    }

    @Test void publicIp_isRemote() {
        assertTrue(DatabaseConfig.isRemoteUrl("jdbc:postgresql://203.0.113.5:5432/sibim"));
    }

    // ── resolveSslMode ────────────────────────────────────────────────────────

    @Test void noConfig_local_defaultsToPrefer() {
        assertEquals("prefer", DatabaseConfig.resolveSslMode(null, false));
    }

    @Test void noConfig_remote_defaultsToRequire() {
        assertEquals("require", DatabaseConfig.resolveSslMode(null, true));
    }

    @Test void explicit_alwaysHonored() {
        assertEquals("verify-full", DatabaseConfig.resolveSslMode("verify-full", true));
        assertEquals("disable",     DatabaseConfig.resolveSslMode("disable",     false));
    }

    @Test void blank_treatedAsNotSet_local() {
        assertEquals("prefer", DatabaseConfig.resolveSslMode("", false));
        assertEquals("prefer", DatabaseConfig.resolveSslMode("  ", false));
    }

    @Test void blank_treatedAsNotSet_remote() {
        assertEquals("require", DatabaseConfig.resolveSslMode("", true));
    }

    // ── enforceSslPolicy ──────────────────────────────────────────────────────

    @Test void local_weakSsl_neverBlocked() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://localhost/db", "disable", false, false));
    }

    @Test void remote_requireSsl_passes() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://remote/db", "require", true, false));
    }

    @Test void remote_verifyCa_passes() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://remote/db", "verify-ca", true, false));
    }

    @Test void remote_verifyFull_passes() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://remote/db", "verify-full", true, false));
    }

    @Test void remote_prefer_noBypass_throws() {
        assertThrows(IllegalStateException.class, () ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://remote/db", "prefer", true, false));
    }

    @Test void remote_allow_noBypass_throws() {
        assertThrows(IllegalStateException.class, () ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://remote/db", "allow", true, false));
    }

    @Test void remote_disable_noBypass_throws() {
        assertThrows(IllegalStateException.class, () ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://remote/db", "disable", true, false));
    }

    @Test void remote_prefer_withBypass_doesNotThrow() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://remote/db", "prefer", true, true));
    }

    @Test void errorMessage_containsUrlAndMode() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            DatabaseConfig.enforceSslPolicy("jdbc:postgresql://prod.example.com/db", "prefer", true, false));
        assertTrue(ex.getMessage().contains("prod.example.com"));
        assertTrue(ex.getMessage().contains("prefer"));
    }

    // ── enforcePasswordPolicy ─────────────────────────────────────────────────

    @Test void local_blankPassword_neverBlocked() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforcePasswordPolicy("jdbc:postgresql://localhost/db", false, true, false));
    }

    @Test void remote_nonBlankPassword_passes() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforcePasswordPolicy("jdbc:postgresql://remote/db", true, false, false));
    }

    @Test void remote_blankPassword_noBypass_throws() {
        assertThrows(IllegalStateException.class, () ->
            DatabaseConfig.enforcePasswordPolicy("jdbc:postgresql://remote/db", true, true, false));
    }

    @Test void remote_blankPassword_withBypass_doesNotThrow() {
        assertDoesNotThrow(() ->
            DatabaseConfig.enforcePasswordPolicy("jdbc:postgresql://remote/db", true, true, true));
    }

    @Test void passwordErrorMessage_containsUrl() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            DatabaseConfig.enforcePasswordPolicy("jdbc:postgresql://prod.example.com/db", true, true, false));
        assertTrue(ex.getMessage().contains("prod.example.com"));
        assertTrue(ex.getMessage().contains("DB_PASSWORD"));
    }
}
