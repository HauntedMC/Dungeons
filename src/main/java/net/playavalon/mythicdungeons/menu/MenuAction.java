package net.playavalon.mythicdungeons.menu;

import org.bukkit.event.Event;

public interface MenuAction<T extends Event> {
   void run(T var1);
}
