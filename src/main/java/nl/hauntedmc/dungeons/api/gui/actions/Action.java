package nl.hauntedmc.dungeons.api.gui.actions;

import org.bukkit.event.Event;

public interface Action<T extends Event> {
   void run(T var1);
}
