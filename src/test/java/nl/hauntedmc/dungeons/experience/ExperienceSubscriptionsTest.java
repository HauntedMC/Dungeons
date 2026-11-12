package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.api.DungeonExperienceApi.Change;
import org.junit.jupiter.api.Test;

class ExperienceSubscriptionsTest {
    private final ExperienceSubscriptions subscriptions = new ExperienceSubscriptions(() -> {});
    private final UUID viewer = UUID.randomUUID();
    private final Change change = new Change(UUID.randomUUID(), "objective-completed", "seal", 1);

    @Test
    void repeatedCloseCannotRemoveAnotherRegistrationOfTheSameListener() throws Exception {
        var received = new ArrayList<Change>();
        Consumer<Change> listener = received::add;
        var first = subscriptions.subscribe(viewer, listener);
        var second = subscriptions.subscribe(viewer, listener);
        first.close();
        first.close();
        subscriptions.publish(viewer, change, failure -> fail(failure));
        assertEquals(java.util.List.of(change), received);
        second.close();
        subscriptions.publish(viewer, change, failure -> fail(failure));
        assertEquals(1, received.size());
    }

    @Test
    void dispatchSupportsUnsubscribeAndShutdownInsideCallbacks() {
        var received = new ArrayList<Change>();
        subscriptions.subscribe(viewer, event -> subscriptions.close());
        subscriptions.subscribe(viewer, received::add);
        subscriptions.publish(viewer, change, failure -> fail(failure));
        assertTrue(received.isEmpty());
        assertThrows(
                IllegalStateException.class, () -> subscriptions.subscribe(viewer, received::add));
    }

    @Test
    void failuresAndOtherViewersAreIsolated() {
        var received = new ArrayList<Change>();
        var failures = new ArrayList<RuntimeException>();
        subscriptions.subscribe(
                viewer,
                event -> {
                    throw new IllegalStateException("consumer");
                });
        subscriptions.subscribe(viewer, received::add);
        subscriptions.subscribe(UUID.randomUUID(), event -> fail("Other viewer"));
        subscriptions.publish(viewer, change, failures::add);
        assertEquals(1, failures.size());
        assertEquals(java.util.List.of(change), received);
        assertThrows(NullPointerException.class, () -> subscriptions.subscribe(viewer, null));
    }
}
