package com.endava.cats.tui.event;

import io.github.ludovicianul.prettylogger.PrettyLogger;
import io.github.ludovicianul.prettylogger.PrettyLoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Publishes execution events without coupling the execution engine to a concrete presentation layer.
 */
@ApplicationScoped
public class CatsExecutionEventPublisher {
    private final PrettyLogger logger = PrettyLoggerFactory.getLogger(CatsExecutionEventPublisher.class);
    private final CopyOnWriteArrayList<Consumer<CatsExecutionEvent>> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Registers an event subscriber.
     *
     * @param subscriber subscriber to register
     * @return a registration which removes the subscriber when closed
     */
    public Subscription subscribe(Consumer<CatsExecutionEvent> subscriber) {
        Consumer<CatsExecutionEvent> safeSubscriber = Objects.requireNonNull(subscriber, "subscriber");
        subscribers.add(safeSubscriber);
        return () -> subscribers.remove(safeSubscriber);
    }

    /**
     * Indicates whether publishing work is currently observed by a presentation layer.
     *
     * @return {@code true} when at least one subscriber is registered
     */
    public boolean hasSubscribers() {
        return !subscribers.isEmpty();
    }

    /**
     * Publishes an event to all current subscribers. Subscriber failures are isolated from fuzzing.
     *
     * @param event event to publish
     */
    public void publish(CatsExecutionEvent event) {
        CatsExecutionEvent safeEvent = Objects.requireNonNull(event, "event");
        for (Consumer<CatsExecutionEvent> subscriber : subscribers) {
            try {
                subscriber.accept(safeEvent);
            } catch (RuntimeException e) {
                logger.debug("Execution event subscriber failed: {}", e.getMessage());
            }
        }
    }

    /**
     * A removable event subscription.
     */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
