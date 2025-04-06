package net.playavalon.mythicdungeons.listeners;

import net.playavalon.mythicdungeons.api.events.dungeon.HotbarSetEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonElement;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ElementListener implements Listener {
   private final DungeonElement element;

   public ElementListener(DungeonElement element) {
      this.element = element;
   }

   @EventHandler
   public void onHotbarLoad(HotbarSetEvent event) {
      if (event.getNewHotbar() != null && this.element.getMenu() != null) {
         if (event.getNewHotbar() == this.element.getMenu()) {
            if (this.element instanceof DungeonFunction) {
               event.getMythicPlayer().setActiveFunction((DungeonFunction)this.element);
            } else if (this.element instanceof DungeonTrigger) {
               event.getMythicPlayer().setActiveTrigger((DungeonTrigger)this.element);
            } else if (this.element instanceof TriggerCondition) {
               event.getMythicPlayer().setActiveCondition((TriggerCondition)this.element);
            }
         }
      }
   }
}
