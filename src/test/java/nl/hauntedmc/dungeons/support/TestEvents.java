package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.event.Cancellable;

/**
 * Stateful cancellation at the event boundary, avoiding assertions against stubbed return values.
 */
public final class TestEvents {
    private TestEvents() {}

    public static <T extends Cancellable> T cancellable(Class<T> type) {
        T event = mock(type);
        boolean[] cancelled = {false};
        when(event.isCancelled()).thenAnswer(c -> cancelled[0]);
        doAnswer(
                        c -> {
                            cancelled[0] = c.getArgument(0);
                            return null;
                        })
                .when(event)
                .setCancelled(anyBoolean());
        return event;
    }
}
