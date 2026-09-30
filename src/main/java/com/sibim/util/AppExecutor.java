package com.sibim.util;

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

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
        submit((Runnable) task);
    }

    /** Submit any plain Runnable. Dropped once the app is closing: a timer or
     *  animation that fires after {@link #shutdown()} (e.g. the startup
     *  vencidos check) would otherwise throw on the FX thread while exiting. */
    public static void submit(Runnable task) {
        try {
            POOL.submit(task);
        } catch (RejectedExecutionException e) {
            if (!POOL.isShutdown()) throw e;
        }
    }

    /** The shared pool itself, for callers that fan out several tasks and
     *  join them (e.g. CompletableFuture.supplyAsync) instead of submitting
     *  one at a time. Never shut this down from a caller — only Main.stop()
     *  via {@link #shutdown()} owns its lifecycle. */
    public static ExecutorService pool() {
        return POOL;
    }

    /** Call from Main.stop() to allow clean shutdown. Not strictly required
     *  since threads are daemon, but makes shutdown log cleaner. */
    public static void shutdown() {
        POOL.shutdown();
    }
}
