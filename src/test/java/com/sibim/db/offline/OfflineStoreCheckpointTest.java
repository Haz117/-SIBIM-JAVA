package com.sibim.db.offline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OfflineStore.checkpoint() — the periodic hot-backup that lets a
 * crash between checkpoints lose only a few minutes of offline work instead
 * of everything since the last clean shutdown (see the class-level Javadoc
 * on OfflineStore's checkpointExecutor field).
 *
 * Exercises the real SQLite VACUUM INTO + AES-256-GCM round trip against a
 * throwaway DB file, never touching the real %USERPROFILE%\.sibim\ store.
 */
class OfflineStoreCheckpointTest {

    private Connection conn;

    @AfterEach
    void closeConnection() throws Exception {
        if (conn != null && !conn.isClosed()) conn.close();
    }

    @Test
    void checkpoint_producesDecryptableSnapshot_withCurrentData(@TempDir Path dir) throws Exception {
        Path dbFile  = dir.resolve("work.db");
        Path encFile = dir.resolve("out.enc");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)");
            st.execute("INSERT INTO t (val) VALUES ('hello')");
        }
        SecretKey key = OfflineEncryption.keyFrom(OfflineKeyManager.deriveKey());

        OfflineStore.checkpoint(conn, dir, encFile, key);

        assertTrue(Files.exists(encFile), "el checkpoint debe producir un archivo cifrado");
        assertTrue(Files.size(encFile) > 0, "el archivo cifrado no debe estar vacío");

        Path decrypted = dir.resolve("decrypted.db");
        OfflineEncryption.decryptTo(encFile, decrypted, key);
        try (Connection verify = DriverManager.getConnection("jdbc:sqlite:" + decrypted);
             Statement st = verify.createStatement();
             var rs = st.executeQuery("SELECT val FROM t WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("hello", rs.getString("val"));
        }
    }

    @Test
    void checkpoint_capturesWritesMadeBeforeIt(@TempDir Path dir) throws Exception {
        Path dbFile  = dir.resolve("work.db");
        Path encFile = dir.resolve("out.enc");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE outbox (id INTEGER PRIMARY KEY, status TEXT)");
            st.execute("INSERT INTO outbox (status) VALUES ('PENDING')");
            st.execute("INSERT INTO outbox (status) VALUES ('PENDING')");
        }
        SecretKey key = OfflineEncryption.keyFrom(OfflineKeyManager.deriveKey());

        OfflineStore.checkpoint(conn, dir, encFile, key);

        Path decrypted = dir.resolve("decrypted.db");
        OfflineEncryption.decryptTo(encFile, decrypted, key);
        try (Connection verify = DriverManager.getConnection("jdbc:sqlite:" + decrypted);
             Statement st = verify.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) AS n FROM outbox WHERE status = 'PENDING'")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("n"), "las filas del outbox pendientes de sincronizar deben sobrevivir al checkpoint");
        }
    }

    @Test
    void checkpoint_cleansUpTempSnapshotFile(@TempDir Path dir) throws Exception {
        Path dbFile  = dir.resolve("work.db");
        Path encFile = dir.resolve("out.enc");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t (id INTEGER PRIMARY KEY)");
        }
        SecretKey key = OfflineEncryption.keyFrom(OfflineKeyManager.deriveKey());

        OfflineStore.checkpoint(conn, dir, encFile, key);

        assertFalse(Files.exists(dir.resolve("offline.db.checkpoint-tmp")),
            "el archivo temporal de la instantánea no debe quedar en disco");
    }

    @Test
    void checkpoint_overwritesPreviousEncryptedSnapshot(@TempDir Path dir) throws Exception {
        Path dbFile  = dir.resolve("work.db");
        Path encFile = dir.resolve("out.enc");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        SecretKey key = OfflineEncryption.keyFrom(OfflineKeyManager.deriveKey());

        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)");
            st.execute("INSERT INTO t (val) VALUES ('primero')");
        }
        OfflineStore.checkpoint(conn, dir, encFile, key);

        try (Statement st = conn.createStatement()) {
            st.execute("UPDATE t SET val = 'segundo' WHERE id = 1");
        }
        OfflineStore.checkpoint(conn, dir, encFile, key);

        Path decrypted = dir.resolve("decrypted.db");
        OfflineEncryption.decryptTo(encFile, decrypted, key);
        try (Connection verify = DriverManager.getConnection("jdbc:sqlite:" + decrypted);
             Statement st = verify.createStatement();
             var rs = st.executeQuery("SELECT val FROM t WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("segundo", rs.getString("val"), "el segundo checkpoint debe reflejar el estado más reciente");
        }
    }

    @Test
    void checkpoint_doesNotThrow_whenConnectionIsBroken(@TempDir Path dir) throws Exception {
        Path dbFile  = dir.resolve("work.db");
        Path encFile = dir.resolve("out.enc");
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        conn.close(); // simula una conexión inválida en el momento del checkpoint
        SecretKey key = OfflineEncryption.keyFrom(OfflineKeyManager.deriveKey());

        assertDoesNotThrow(() -> OfflineStore.checkpoint(conn, dir, encFile, key),
            "un checkpoint fallido debe registrarse y reintentarse después, no interrumpir la app");
    }
}
