package com.sibim.util;

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application-wide background executor backed by Java 21 virtual threads.
 * Virtual threads are daemon threads by default, block cheaply on I/O (DB
 * queries, file reads), and carry no cap on concurrency — ideal for this
 * app's mix of short-lived DB tasks.
 */
public final class AppExecutor {

    private AppExecutor() {}

    private static final ExecutorService POOL = Executors.newVirtualThreadPerTaskExecutor();

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
