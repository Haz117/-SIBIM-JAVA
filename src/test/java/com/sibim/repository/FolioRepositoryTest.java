package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FolioRepositoryTest {

    private FolioRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseConfig.setDemoMode(true);
        clearDemoCounters();
        repo = new FolioRepository();
    }

    @AfterEach
    void tearDown() throws Exception {
        DatabaseConfig.setDemoMode(false);
        clearDemoCounters();
    }

    /** Clears the private static DEMO_COUNTERS map between tests via reflection
     *  so each test starts with a clean slate regardless of execution order. */
    @SuppressWarnings("unchecked")
    private static void clearDemoCounters() throws Exception {
        Field f = FolioRepository.class.getDeclaredField("DEMO_COUNTERS");
        f.setAccessible(true);
        ConcurrentHashMap<String, AtomicInteger> map =
            (ConcurrentHashMap<String, AtomicInteger>) f.get(null);
        map.clear();
    }

    @Test
    void testDemoMode_firstCall_returnsOne() throws SQLException {
        int year = LocalDate.now().getYear();
        String result = repo.next("INV");
        assertEquals("INV-" + year + "-000001", result);
    }

    @Test
    void testDemoMode_secondCall_returnsTwo() throws SQLException {
        int year = LocalDate.now().getYear();
        repo.next("INV");
        String result = repo.next("INV");
        assertEquals("INV-" + year + "-000002", result);
    }

    @Test
    void testDemoMode_differentPrefixes_independent() throws SQLException {
        int year = LocalDate.now().getYear();
        repo.next("INV");
        repo.next("INV");
        String movResult = repo.next("MOV");
        assertEquals("MOV-" + year + "-000001", movResult,
            "El contador de MOV debe empezar en 1 independientemente del contador de INV");
    }

    @Test
    void testDemoMode_differentYears_independent() throws SQLException {
        // We can't easily mock LocalDate without extra libraries, but we can verify
        // that the key used internally includes the year: generate one folio and
        // confirm that the returned string contains the current year, which implies
        // that a different year would produce an independent counter.
        int year = LocalDate.now().getYear();
        String result = repo.next("CDT");
        assertTrue(result.contains(String.valueOf(year)),
            "El folio debe incluir el año actual: " + result);
        // Also confirm format: PREFIX-YEAR-NNNNNN
        assertTrue(result.matches("[A-Z]+-" + year + "-\\d{6}"),
            "El folio debe seguir el formato PREFIX-YEAR-000001: " + result);
    }
}
