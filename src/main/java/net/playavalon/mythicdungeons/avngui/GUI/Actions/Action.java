package net.playavalon.mythicdungeons.avngui.GUI.Actions;

import org.bukkit.event.Event;

public interface Action<T extends Event> {
   void run(T var1);
}
