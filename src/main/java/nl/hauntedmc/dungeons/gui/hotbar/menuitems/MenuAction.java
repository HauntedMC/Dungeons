package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import org.bukkit.event.Event;

public interface MenuAction<T extends Event> {
   void run(T var1);
}
