package com.sibim.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal structural tests for BarcodeScanner that do NOT require a running
 * JavaFX toolkit (no Scene/Stage creation).  Timing-logic tests would need
 * a headless JavaFX environment (TestFX); those are omitted here because the
 * test environment does not guarantee a display server.
 */
class BarcodeScannerTest {

    @Test
    void classExists() {
        assertDoesNotThrow(() -> Class.forName("com.sibim.util.BarcodeScanner"),
            "La clase BarcodeScanner debe existir en el classpath");
    }

    @Test
    void attach_methodIsPublicStatic() throws NoSuchMethodException {
        // Verify the public API: attach(Scene, Consumer<String>) -> Runnable
        // We check the signature via reflection without instantiating a Scene.
        Method m = BarcodeScanner.class.getDeclaredMethod(
            "attach",
            javafx.scene.Scene.class,
            Consumer.class
        );
        assertTrue(Modifier.isPublic(m.getModifiers()), "attach() debe ser public");
        assertTrue(Modifier.isStatic(m.getModifiers()), "attach() debe ser static");
        assertEquals(Runnable.class, m.getReturnType(),
            "attach() debe retornar Runnable");
    }

    @Test
    void attach_returnTypeIsRunnable() throws NoSuchMethodException {
        Method m = BarcodeScanner.class.getDeclaredMethod(
            "attach",
            javafx.scene.Scene.class,
            Consumer.class
        );
        assertEquals(Runnable.class, m.getReturnType());
    }
}
