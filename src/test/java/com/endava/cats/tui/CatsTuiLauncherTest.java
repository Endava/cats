package com.endava.cats.tui;

import com.endava.cats.tui.event.CatsExecutionEvent;
import com.endava.cats.tui.event.CatsExecutionEventPublisher;
import dev.tamboui.backend.jline3.JLineBackend;
import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@QuarkusTest
class CatsTuiLauncherTest {
    @Test
    void shouldInterruptAndJoinRunningWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread worker = Thread.ofVirtual().start(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException _) {
                interrupted.set(true);
            } finally {
                stopped.countDown();
            }
        });

        Assertions.assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        boolean cancellationRequested = CatsTuiLauncher.stopWorker(worker);

        Assertions.assertThat(cancellationRequested).isTrue();
        Assertions.assertThat(stopped.await(1, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(interrupted).isTrue();
        Assertions.assertThat(worker.isAlive()).isFalse();
    }

    @Test
    void shouldNotReportCancellationForCompletedWorker() throws Exception {
        Thread worker = Thread.ofVirtual().start(() -> {
        });
        worker.join();

        Assertions.assertThat(CatsTuiLauncher.stopWorker(worker)).isFalse();
    }

    @Test
    void shouldHandleAbsentWorkerAndFailure() throws Exception {
        AtomicBoolean cancellationActionCalled = new AtomicBoolean();

        Assertions.assertThat(CatsTuiLauncher.stopWorker(null, () -> cancellationActionCalled.set(true))).isFalse();
        Assertions.assertThat(cancellationActionCalled).isFalse();
        Assertions.assertThatCode(() -> CatsTuiLauncher.throwIfWorkerFailed(null)).doesNotThrowAnyException();
    }

    @Test
    void shouldCancelBlockingWorkWhenStoppingWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean cancellationActionCalled = new AtomicBoolean();
        Thread worker = Thread.ofVirtual().start(() -> {
            started.countDown();
            while (release.getCount() > 0) {
                Thread.onSpinWait();
            }
        });

        Assertions.assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        boolean cancellationRequested = CatsTuiLauncher.stopWorker(worker, () -> {
            cancellationActionCalled.set(true);
            release.countDown();
        });

        Assertions.assertThat(cancellationRequested).isTrue();
        Assertions.assertThat(cancellationActionCalled).isTrue();
        Assertions.assertThat(worker.isAlive()).isFalse();
    }

    @Test
    @SuppressWarnings("SystemConsoleNull")
    void shouldRejectNonInteractiveTerminal() {
        Assertions.assertThat(System.console() == null || !System.console().isTerminal()).isTrue();
        Assertions.assertThatThrownBy(CatsTuiLauncher::requireInteractiveTerminal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires an interactive terminal");

        CatsTuiLauncher launcher = new CatsTuiLauncher(Mockito.mock(CatsExecutionEventPublisher.class));
        Assertions.assertThatThrownBy(() -> launcher.run(() -> {
        }, 10)).isInstanceOf(IllegalStateException.class).hasMessageContaining("interactive terminal");
        Assertions.assertThatThrownBy(() -> launcher.run(() -> {
        }, () -> {
        }, 10)).isInstanceOf(IllegalStateException.class).hasMessageContaining("interactive terminal");
    }

    @Test
    void shouldCaptureWorkerFailuresAsEvents() {
        ConcurrentLinkedQueue<CatsExecutionEvent> events = new ConcurrentLinkedQueue<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        IllegalArgumentException cause = new IllegalArgumentException("broken execution");

        ReflectionTestUtils.invokeMethod(CatsTuiLauncher.class, "runExecution",
                (Runnable) () -> {
                    throw cause;
                }, events, failure);

        Assertions.assertThat(failure.get()).isSameAs(cause);
        Assertions.assertThat(events).singleElement().isInstanceOfSatisfying(CatsExecutionEvent.SessionFailed.class,
                event -> Assertions.assertThat(event.message()).contains("broken execution"));

        failure.set(null);
        events.clear();
        ReflectionTestUtils.invokeMethod(CatsTuiLauncher.class, "runExecution", (Runnable) () -> {
        }, events, failure);
        Assertions.assertThat(failure.get()).isNull();
        Assertions.assertThat(events).isEmpty();
    }

    @Test
    void shouldRunTheTuiLifecycleAndSurfaceWorkerFailures() throws Exception {
        CatsExecutionEventPublisher events = new CatsExecutionEventPublisher();
        CatsTuiLauncher launcher = new CatsTuiLauncher(events);
        ToolkitRunner runner = Mockito.mock(ToolkitRunner.class);
        ToolkitRunner.Builder builder = Mockito.mock(ToolkitRunner.Builder.class);
        CountDownLatch executionFinished = new CountDownLatch(1);

        try (MockedStatic<CatsTuiLauncher> launcherMethods = Mockito.mockStatic(
                CatsTuiLauncher.class, Mockito.CALLS_REAL_METHODS);
             MockedConstruction<JLineBackend> backends = Mockito.mockConstruction(JLineBackend.class,
                     (backend, _) -> Mockito.when(backend.size()).thenReturn(new Size(100, 30)));
             MockedStatic<ToolkitRunner> toolkit = Mockito.mockStatic(ToolkitRunner.class)) {
            launcherMethods.when(CatsTuiLauncher::requireInteractiveTerminal).thenAnswer(_ -> null);
            toolkit.when(ToolkitRunner::builder).thenReturn(builder);
            Mockito.when(builder.config(Mockito.any())).thenReturn(builder);
            Mockito.when(builder.tickRate(Mockito.any())).thenReturn(builder);
            Mockito.when(builder.faultTolerant(true)).thenReturn(builder);
            Mockito.when(builder.build()).thenReturn(runner);
            Mockito.doAnswer(invocation -> {
                executionFinished.await(1, TimeUnit.SECONDS);
                Supplier<Element> supplier = invocation.getArgument(0);
                Assertions.assertThat(supplier.get()).isNotNull();
                return null;
            }).when(runner).run(Mockito.any());

            boolean cancelled = launcher.run(() -> {
                events.publish(new CatsExecutionEvent.SessionStarted(java.time.Instant.now(),
                        "cats", "1", "today", "test"));
                executionFinished.countDown();
            }, 10);

            Assertions.assertThat(cancelled).isFalse();
            Mockito.verify(runner).close();
            Assertions.assertThat(backends.constructed()).hasSize(1);

            IllegalArgumentException failure = new IllegalArgumentException("failed worker");
            Assertions.assertThatThrownBy(() -> launcher.run(() -> {
                throw failure;
            }, 10)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CATS execution failed")
                    .hasCause(failure);
        }
    }

    @Test
    void shouldCancelAStillRunningTuiExecution() throws Exception {
        CatsTuiLauncher launcher = new CatsTuiLauncher(new CatsExecutionEventPublisher());
        ToolkitRunner runner = Mockito.mock(ToolkitRunner.class);
        ToolkitRunner.Builder builder = Mockito.mock(ToolkitRunner.Builder.class);
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch releaseExecution = new CountDownLatch(1);
        AtomicBoolean cancellationActionCalled = new AtomicBoolean();

        try (MockedStatic<CatsTuiLauncher> launcherMethods = Mockito.mockStatic(
                CatsTuiLauncher.class, Mockito.CALLS_REAL_METHODS);
             MockedConstruction<JLineBackend> _ = Mockito.mockConstruction(JLineBackend.class,
                     (backend, _) -> Mockito.when(backend.size()).thenReturn(new Size(100, 30)));
             MockedStatic<ToolkitRunner> toolkit = Mockito.mockStatic(ToolkitRunner.class)) {
            launcherMethods.when(CatsTuiLauncher::requireInteractiveTerminal).thenAnswer(_ -> null);
            toolkit.when(ToolkitRunner::builder).thenReturn(builder);
            Mockito.when(builder.config(Mockito.any())).thenReturn(builder);
            Mockito.when(builder.tickRate(Mockito.any())).thenReturn(builder);
            Mockito.when(builder.faultTolerant(true)).thenReturn(builder);
            Mockito.when(builder.build()).thenReturn(runner);
            Mockito.doAnswer(_ -> {
                Assertions.assertThat(executionStarted.await(1, TimeUnit.SECONDS)).isTrue();
                return null;
            }).when(runner).run(Mockito.any());

            boolean cancelled = launcher.run(() -> {
                executionStarted.countDown();
                try {
                    releaseExecution.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }, () -> {
                cancellationActionCalled.set(true);
                releaseExecution.countDown();
            }, 10);

            Assertions.assertThat(cancelled).isTrue();
            Assertions.assertThat(cancellationActionCalled).isTrue();
        }
    }

    @Test
    void shouldValidateBackendAndTolerateViewportReadFailures() throws Exception {
        try (MockedConstruction<JLineBackend> backend = Mockito.mockConstruction(JLineBackend.class,
                (mock, _) -> Mockito.when(mock.size()).thenReturn(new Size(79, 24)))) {
            Assertions.assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                            CatsTuiLauncher.class, "validatedBackend"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 80x24");
            Mockito.verify(backend.constructed().getFirst()).close();
        }

        CatsTuiState state = new CatsTuiState();
        JLineBackend backend = Mockito.mock(JLineBackend.class);
        Mockito.when(backend.size()).thenThrow(new IllegalStateException("resize race"));
        Assertions.assertThatCode(() -> ReflectionTestUtils.invokeMethod(
                CatsTuiLauncher.class, "updateViewport", state, backend)).doesNotThrowAnyException();
    }

    @Test
    void shouldPropagateWorkerFailure() {
        IllegalStateException failure = new IllegalStateException("broken report");

        Assertions.assertThatThrownBy(() -> CatsTuiLauncher.throwIfWorkerFailed(failure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CATS execution failed")
                .hasCause(failure);
    }

    @Test
    void shouldRejectATerminalTooSmallForTheLayout() {
        Assertions.assertThatThrownBy(() -> CatsTuiLauncher.validateTerminalSize(79, 24))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 80x24")
                .hasMessageContaining("79x24");
        Assertions.assertThatThrownBy(() -> CatsTuiLauncher.validateTerminalSize(80, 23))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("80x23");
        Assertions.assertThatCode(() -> CatsTuiLauncher.validateTerminalSize(80, 24)).doesNotThrowAnyException();
        Assertions.assertThatCode(() -> CatsTuiLauncher.validateTerminalSize(120, 50)).doesNotThrowAnyException();
    }
}
