package com.guildedsmp.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A single background thread for file writes. Writes are executed in submission
 * order, so a later save of the same file can never be overwritten by an earlier one.
 * Only pre-serialized strings are handed to this thread; it never touches server state.
 */
public final class IoExecutor {

    private final Logger logger;
    private final ExecutorService executor;

    public IoExecutor(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GuildedSMP-IO");
            t.setDaemon(true);
            return t;
        });
    }

    public void submit(Runnable task) {
        if (executor.isShutdown()) {
            task.run();
            return;
        }
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Background save failed", t);
            }
        });
    }

    /** Waits for pending writes; called from onDisable. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for pending saves to finish.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
