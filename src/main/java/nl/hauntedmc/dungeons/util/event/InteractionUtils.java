package nl.hauntedmc.dungeons.util.event;

import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;

/** Interaction-result helpers for Bukkit player interaction events. */
public final class InteractionUtils {

    /** Returns whether block interaction has been denied on this event. */
    public static boolean isInteractionDenied(PlayerInteractEvent event) {
        return event.useInteractedBlock() == Event.Result.DENY;
    }

    /** Denies both block interaction and item use on this interaction event. */
    public static void denyInteraction(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }
}
