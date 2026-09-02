package com.endava.cats.tui;

import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.event.CatsExecutionEventPublisher;
import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.tui.TuiConfig;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the Tamboui terminal lifecycle while CATS fuzzing runs on a worker thread.
 */
@ApplicationScoped
public class CatsTuiLauncher {
    private static final int MINIMUM_COLUMNS = 80;
    private static final int MINIMUM_ROWS = 24;
    private final CatsExecutionEventPublisher events;

    /**
     * Creates a launcher.
     *
     * @param events execution event source
     */
    public CatsTuiLauncher(CatsExecutionEventPublisher events) {
        this.events = events;
    }

    /**
     * Runs the supplied CATS execution in the TUI.
     *
     * @param execution              fuzzing lifecycle
     * @param maximumRetainedResults maximum number of complete test details kept in memory
     * @return {@code true} when leaving the TUI cancelled a live execution
     */
    public boolean run(Runnable execution, int maximumRetainedResults) {
        return run(execution, () -> {
        }, maximumRetainedResults);
    }

    /**
     * Runs the supplied CATS execution in the TUI and invokes the cancellation action when the user
     * leaves the interface while execution is still active.
     *
     * @param execution              fuzzing lifecycle
     * @param cancellationAction     action that cancels blocking work owned by the execution
     * @param maximumRetainedResults maximum number of complete test details kept in memory
     * @return {@code true} when leaving the TUI cancelled a live execution
     */
    public boolean run(Runnable execution, Runnable cancellationAction, int maximumRetainedResults) {
        requireInteractiveTerminal();
        ConcurrentLinkedQueue<CatsExecutionEvent> eventQueue = new ConcurrentLinkedQueue<>();
        CatsTuiState state = new CatsTuiState(maximumRetainedResults);
        AtomicReference<RuntimeException> workerFailure = new AtomicReference<>();
        Thread worker = null;
        boolean cancellationRequested = false;
        RuntimeException terminalFailure = null;

        JLineBackend backend;
        try {
            backend = validatedBackend();
        } catch (Exception e) {
            throw new IllegalStateException("terminal initialization or rendering failed: " + e, e);
        }
        try (ToolkitRunner runner = ToolkitRunner.builder()
                .config(TuiConfig.builder().backend(backend).build())
                .tickRate(Duration.ofMillis(100))
                .faultTolerant(true)
                .build();
             var _ = events.subscribe(eventQueue::offer)) {
                worker = Thread.ofVirtual().name("cats-fuzzing")
                        .start(() -> runExecution(execution, eventQueue, workerFailure));
                try {
                    runner.run(() -> {
                        updateViewport(state, backend);
                        state.drain(eventQueue);
                        return CatsTuiView.render(state);
                    });
                } finally {
                    cancellationRequested = requestStop(worker, cancellationAction);
                }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminalFailure = new IllegalStateException("TUI execution was interrupted", e);
        } catch (Exception e) {
            terminalFailure = new IllegalStateException("terminal initialization or rendering failed: " + e, e);
        }

        try {
            awaitWorker(worker);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for CATS to stop", e);
        }
        if (terminalFailure != null) {
            throw terminalFailure;
        }
        throwIfWorkerFailed(workerFailure.get());
        return cancellationRequested;
    }

    private static void runExecution(Runnable execution, ConcurrentLinkedQueue<CatsExecutionEvent> eventQueue,
                                     AtomicReference<RuntimeException> workerFailure) {
        try {
            execution.run();
        } catch (RuntimeException e) {
            workerFailure.set(e);
            eventQueue.offer(new CatsExecutionEvent.SessionFailed(Instant.now(), e.toString()));
        }
    }

    static boolean stopWorker(Thread worker) throws InterruptedException {
        return stopWorker(worker, () -> {
        });
    }

    static boolean stopWorker(Thread worker, Runnable cancellationAction) throws InterruptedException {
        boolean cancellationRequested = requestStop(worker, cancellationAction);
        awaitWorker(worker);
        return cancellationRequested;
    }

    private static boolean requestStop(Thread worker, Runnable cancellationAction) {
        if (worker != null && worker.isAlive()) {
            worker.interrupt();
            cancellationAction.run();
            return true;
        }
        return false;
    }

    private static void awaitWorker(Thread worker) throws InterruptedException {
        if (worker != null && worker.isAlive()) {
            worker.join(Duration.ofSeconds(30));
        }
    }

    @SuppressWarnings("SystemConsoleNull")
    static void requireInteractiveTerminal() {
        if (System.console() == null || !System.console().isTerminal()) {
            throw new IllegalStateException("--tui requires an interactive terminal; remove --tui when input or output is redirected");
        }
    }

    private static JLineBackend validatedBackend() throws Exception {
        JLineBackend backend = new JLineBackend();
        try {
            Size size = backend.size();
            validateTerminalSize(size.width(), size.height());
            return backend;
        } catch (Exception e) {
            backend.close();
            throw e;
        }
    }

    private static void updateViewport(CatsTuiState state, JLineBackend backend) {
        try {
            Size size = backend.size();
            state.updateViewport(size.width(), size.height());
        } catch (Exception _) {
            // Keep the last usable viewport size if a transient resize query fails.
        }
    }

    static void validateTerminalSize(int columns, int rows) {
        if (columns < MINIMUM_COLUMNS || rows < MINIMUM_ROWS) {
            throw new IllegalStateException("--tui requires a terminal of at least %dx%d; current size is %dx%d. Resize the terminal and retry"
                    .formatted(MINIMUM_COLUMNS, MINIMUM_ROWS, columns, rows));
        }
    }

    static void throwIfWorkerFailed(RuntimeException failure) {
        if (failure != null) {
            throw new IllegalStateException("CATS execution failed: " + failure, failure);
        }
    }
}
