package com.sibim.util;

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application-wide background thread pool.
 *
 * Replaces the previous pattern of "new Thread(task).start()" scattered across
 * controllers: reuses threads, caps concurrency to 4, and marks threads as
 * daemon so they never block JVM shutdown.
 */
public final class AppExecutor {

    private AppExecutor() {}

    private static final AtomicInteger IDX = new AtomicInteger(0);

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "sibim-bg-" + IDX.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /** Submit a JavaFX Task (its succeeded/failed callbacks still fire on the FX thread). */
    public static void submit(Task<?> task) {
        POOL.submit(task);
    }

    /** Submit any plain Runnable. */
    public static void submit(Runnable task) {
        POOL.submit(task);
    }

    /** Call from Main.stop() to allow clean shutdown. Not strictly required
     *  since threads are daemon, but makes shutdown log cleaner. */
    public static void shutdown() {
        POOL.shutdown();
    }
}
