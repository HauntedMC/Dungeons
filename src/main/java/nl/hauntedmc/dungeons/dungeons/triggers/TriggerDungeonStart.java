package nl.hauntedmc.dungeons.dungeons.triggers;

import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.events.DungeonStartEvent;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;

@DeclaredTrigger
public class TriggerDungeonStart extends DungeonTrigger {
   public TriggerDungeonStart(Map<String, Object> config) {
      super("Dungeon Start", config);
      this.setCategory(TriggerCategory.DUNGEON);
      this.setHasTarget(true);
   }

   public TriggerDungeonStart() {
      super("Dungeon Start");
      this.setCategory(TriggerCategory.DUNGEON);
      this.setHasTarget(true);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.MOSSY_COBBLESTONE);
      functionButton.setDisplayName("&6Dungeon Start");
      functionButton.addLore("&eTriggered when the dungeon");
      functionButton.addLore("&ebegins.");
      return functionButton;
   }

   @EventHandler
   public void onDungeonStart(DungeonStartEvent event) {
      if (event.getInstance() == this.instance) {
         int delay = 10;
         if (this.instance.getDungeon().isLobbyEnabled()) {
            delay = 1;
         }

         Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
            if (!event.getDungeonPlayers().isEmpty()) {
               this.trigger(event.getDungeonPlayers().getFirst());
            }
         }, delay);
      }
   }

   @Override
   public void buildHotbarMenu() {
   }
}
