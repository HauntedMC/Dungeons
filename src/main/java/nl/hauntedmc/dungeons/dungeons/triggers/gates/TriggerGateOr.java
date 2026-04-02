package nl.hauntedmc.dungeons.dungeons.triggers.gates;

import java.util.Map;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;

@DeclaredTrigger
public class TriggerGateOr extends TriggerGate {
   public TriggerGateOr(Map<String, Object> config) {
      super("OR Gate", config);
      this.setHasTarget(true);
   }

   public TriggerGateOr() {
      super("OR Gate");
      this.setHasTarget(true);
   }

   @EventHandler
   public void onChildTriggered(TriggerFireEvent event) {
      if (event.getInstance().getInstanceWorld() == this.instance.getInstanceWorld()) {
         DungeonTrigger trigger = event.getTrigger();
         if (this.triggerTracker.containsKey(trigger)) {
            this.trigger(event.getDPlayer());
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.COMPARATOR);
      button.setDisplayName("&bOR Gate");
      button.addLore("&eTriggered when one of several");
      button.addLore("&especified triggers have been");
      button.addLore("&eactivated.");
      return button;
   }
}
