package net.playavalon.mythicdungeons.dungeons.triggers.gates;

import java.util.Map;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.menu.MenuButton;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;

@DeclaredTrigger
public class TriggerGateAnd extends TriggerGate {
   public TriggerGateAnd(Map<String, Object> config) {
      super("AND Gate", config);
      this.setHasTarget(true);
   }

   public TriggerGateAnd() {
      super("AND Gate");
      this.setHasTarget(true);
   }

   @EventHandler
   public void onChildTriggered(TriggerFireEvent event) {
      if (event.getInstance().getInstanceWorld() == this.instance.getInstanceWorld()) {
         DungeonTrigger trigger = event.getTrigger();
         if (this.triggerTracker.containsKey(trigger)) {
            this.triggerTracker.put(trigger, true);
            if (!this.triggerTracker.containsValue(false)) {
               this.trigger(event.getDPlayer());
            }
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.COMPARATOR);
      button.setDisplayName("&bAND Gate");
      button.addLore("&eTriggered when several other");
      button.addLore("&especified triggers have been");
      button.addLore("&eactivated.");
      return button;
   }
}
