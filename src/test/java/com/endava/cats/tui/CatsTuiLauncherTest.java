package com.endava.cats.tui;

import io.quarkus.test.junit.QuarkusTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    }
}
