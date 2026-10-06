package com.sibim.util;

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application-wide background executor: daemon platform threads, created as
 * needed and reused (idle ones end after a minute).
 *
 * <p>Not virtual threads. Much of what runs here blocks inside
 * {@code synchronized} — the shared list of bienes, the offline SQLite store
 * and its driver — and on Java 21 a virtual thread that blocks there (or waits
 * for that monitor) keeps its carrier thread. There are only as many carriers
 * as CPU cores, so three or four such tasks on an office PC can hold them all
 * and leave every other background task waiting. A desktop app runs a handful
 * of tasks at a time, so plain threads cost nothing here.
 */
public final class AppExecutor {

    private AppExecutor() {}

    private static final AtomicInteger NUMERO = new AtomicInteger();

    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sibim-fondo-" + NUMERO.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

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
