package org.oqteris.ui;

import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Manages background execution of expensive tasks (parsing, calculation, rendering)
 * to keep the JavaFX UI responsive.
 */
public class AsyncExecutor {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Oqteris-Worker-" + (++counter));
                    t.setDaemon(true);
                    return t;
                }
            }
    );

    /**
     * Executes a background task and delivers the result on the JavaFX Application Thread.
     */
    public static <T> void execute(Supplier<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        EXECUTOR.submit(() -> {
            try {
                T result = task.get();
                if (onSuccess != null) {
                    Platform.runLater(() -> onSuccess.accept(result));
                }
            } catch (Throwable t) {
                if (onError != null) {
                    Platform.runLater(() -> onError.accept(t));
                } else {
                    t.printStackTrace();
                }
            }
        });
    }

    /**
     * Executes a Runnable in the background worker pool.
     */
    public static void execute(Runnable runnable) {
        EXECUTOR.submit(runnable);
    }
}
