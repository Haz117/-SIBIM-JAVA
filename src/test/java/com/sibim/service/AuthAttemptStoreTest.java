package com.sibim.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuthAttemptStore.
 *
 * FILE is a static final field resolved at class-load time from
 * System.getProperty("user.home"), so path redirection via @TempDir is not
 * possible. Tests use a unique username suffix to avoid collisions with any
 * real session data, and @AfterEach + @AfterAll guarantee cleanup.
 */
class AuthAttemptStoreTest {

    private static final String USER = "test_user_" + System.currentTimeMillis();

    @AfterEach
    void cleanupAfterEach() {
        AuthAttemptStore.clear(USER);
    }

    @AfterAll
    static void cleanupAfterAll() {
        // Safety net: if any test leaves residue the per-test cleanup missed.
        AuthAttemptStore.clear(USER);
    }

    // -----------------------------------------------------------------------
    // getCount
    // -----------------------------------------------------------------------

    @Test
    void getCount_newUser_returnsZero() {
        assertEquals(0, AuthAttemptStore.getCount(USER));
    }

    // -----------------------------------------------------------------------
    // increment
    // -----------------------------------------------------------------------

    @Test
    void increment_once_countBecomesOne() {
        AuthAttemptStore.increment(USER);
        assertEquals(1, AuthAttemptStore.getCount(USER));
    }

    @Test
    void increment_thrice_countBecomesThree() {
        AuthAttemptStore.increment(USER);
        AuthAttemptStore.increment(USER);
        AuthAttemptStore.increment(USER);
        assertEquals(3, AuthAttemptStore.getCount(USER));
    }

    @Test
    void increment_setsWindowStartOnFirstCall() {
        long before = System.currentTimeMillis();
        AuthAttemptStore.increment(USER);
        long ws = AuthAttemptStore.getWindowStart(USER);
        long after = System.currentTimeMillis();
        assertTrue(ws >= before && ws <= after,
            "windowStart must be set during the first increment call");
    }

    @Test
    void increment_subsequentCalls_preserveWindowStart() {
        AuthAttemptStore.increment(USER);
        long ws1 = AuthAttemptStore.getWindowStart(USER);

        AuthAttemptStore.increment(USER);
        long ws2 = AuthAttemptStore.getWindowStart(USER);

        assertEquals(ws1, ws2,
            "windowStart must not change after the first increment");
    }

    // -----------------------------------------------------------------------
    // clear
    // -----------------------------------------------------------------------

    @Test
    void clear_afterIncrements_resetsCountToZero() {
        AuthAttemptStore.increment(USER);
        AuthAttemptStore.increment(USER);
        AuthAttemptStore.clear(USER);
        assertEquals(0, AuthAttemptStore.getCount(USER));
    }

    @Test
    void clear_afterIncrements_resetsWindowStartToZero() {
        AuthAttemptStore.increment(USER);
        AuthAttemptStore.clear(USER);
        assertEquals(0L, AuthAttemptStore.getWindowStart(USER));
    }

    @Test
    void clear_onFreshUser_doesNotThrow() {
        assertDoesNotThrow(() -> AuthAttemptStore.clear(USER));
    }

    // -----------------------------------------------------------------------
    // clearAll
    // -----------------------------------------------------------------------

    @Test
    void clearAll_removesStoredCount() {
        AuthAttemptStore.increment(USER);
        AuthAttemptStore.clearAll();
        assertEquals(0, AuthAttemptStore.getCount(USER),
            "clearAll must delete the backing file so getCount returns 0");
    }

    @Test
    void clearAll_doesNotThrowWhenFileAbsent() {
        // Ensure file is gone before calling clearAll a second time.
        AuthAttemptStore.clearAll();
        assertDoesNotThrow(AuthAttemptStore::clearAll);
    }
}
