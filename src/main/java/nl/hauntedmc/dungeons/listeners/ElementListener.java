package nl.hauntedmc.dungeons.listeners;

import nl.hauntedmc.dungeons.api.events.HotbarSetEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonElement;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
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
             switch (this.element) {
                 case DungeonFunction dungeonFunction -> event.getDungeonPlayer().setActiveFunction(dungeonFunction);
                 case DungeonTrigger dungeonTrigger -> event.getDungeonPlayer().setActiveTrigger(dungeonTrigger);
                 case TriggerCondition triggerCondition ->
                         event.getDungeonPlayer().setActiveCondition(triggerCondition);
                 default -> {
                 }
             }
         }
      }
   }
}
