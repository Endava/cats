package com.endava.cats.tui.event;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class CatsExecutionEventPublisherTest {
    @Test
    void givenASubscriber_whenPublishing_thenItReceivesTheEvent() {
        CatsExecutionEventPublisher publisher = new CatsExecutionEventPublisher();
        List<CatsExecutionEvent> received = new ArrayList<>();
        publisher.subscribe(received::add);
        CatsExecutionEvent event = new CatsExecutionEvent.PathStarted(Instant.EPOCH, "/pets");

        publisher.publish(event);

        assertThat(received).containsExactly(event);
    }

    @Test
    void givenAClosedSubscription_whenPublishing_thenItNoLongerReceivesEvents() {
        CatsExecutionEventPublisher publisher = new CatsExecutionEventPublisher();
        List<CatsExecutionEvent> received = new ArrayList<>();
        CatsExecutionEventPublisher.Subscription subscription = publisher.subscribe(received::add);
        assertThat(publisher.hasSubscribers()).isTrue();
        subscription.close();

        publisher.publish(new CatsExecutionEvent.PathStarted(Instant.EPOCH, "/pets"));

        assertThat(received).isEmpty();
        assertThat(publisher.hasSubscribers()).isFalse();
    }

    @Test
    void givenAFailingSubscriber_whenPublishing_thenOtherSubscribersStillReceiveTheEvent() {
        CatsExecutionEventPublisher publisher = new CatsExecutionEventPublisher();
        List<CatsExecutionEvent> received = new ArrayList<>();
        publisher.subscribe(_ -> {
            throw new IllegalStateException("broken subscriber");
        });
        publisher.subscribe(received::add);
        CatsExecutionEvent event = new CatsExecutionEvent.PathStarted(Instant.EPOCH, "/pets");

        publisher.publish(event);

        assertThat(received).containsExactly(event);
    }
}
