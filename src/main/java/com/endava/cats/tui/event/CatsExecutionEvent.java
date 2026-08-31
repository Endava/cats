package com.endava.cats.tui.event;

import com.endava.cats.tui.model.RunConfigurationSnapshot;
import com.endava.cats.tui.model.RunSummarySnapshot;
import com.endava.cats.tui.model.TestResultSnapshot;

import java.time.Instant;

/**
 * Events emitted by the CATS execution engine for presentation layers such as the TUI.
 */
public sealed interface CatsExecutionEvent {
    /**
     * The time at which the event was created.
     *
     * @return event creation time
     */
    Instant occurredAt();

    /**
     * Signals that a CATS execution session has started.
     */
    record SessionStarted(Instant occurredAt, String applicationName, String applicationVersion,
                          String buildTime, String platform) implements CatsExecutionEvent {
    }

    /**
     * Signals that the effective run configuration is available.
     */
    record ConfigurationLoaded(Instant occurredAt,
                               RunConfigurationSnapshot configuration) implements CatsExecutionEvent {
    }

    /**
     * Signals that CATS started processing an OpenAPI path.
     */
    record PathStarted(Instant occurredAt, String path) implements CatsExecutionEvent {
    }

    /**
     * Signals that CATS finished processing an OpenAPI path.
     */
    record PathCompleted(Instant occurredAt, String path) implements CatsExecutionEvent {
    }

    /**
     * Signals that a fuzzer started for a path and HTTP method.
     */
    record FuzzerStarted(Instant occurredAt, String fuzzer, String path,
                         String httpMethod) implements CatsExecutionEvent {
    }

    /**
     * Signals that a fuzzer finished for a path and HTTP method.
     */
    record FuzzerCompleted(Instant occurredAt, String fuzzer, String path,
                           String httpMethod) implements CatsExecutionEvent {
    }

    /**
     * Signals that a single test case has completed.
     */
    record TestCompleted(Instant occurredAt, TestResultSnapshot test) implements CatsExecutionEvent {
    }

    /**
     * Signals that the complete execution and report generation lifecycle has finished.
     */
    record SessionCompleted(Instant occurredAt, RunSummarySnapshot summary) implements CatsExecutionEvent {
    }

    /**
     * Signals that the user cancelled execution before it completed.
     */
    record SessionCancelled(Instant occurredAt, String message) implements CatsExecutionEvent {
    }

    /**
     * Signals that execution could not complete normally.
     */
    record SessionFailed(Instant occurredAt, String message) implements CatsExecutionEvent {
    }
}
