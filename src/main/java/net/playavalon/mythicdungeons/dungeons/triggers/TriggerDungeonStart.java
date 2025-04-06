package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.events.dungeon.DungeonStartEvent;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.menu.MenuButton;
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

         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
            if (!event.getMythicPlayers().isEmpty()) {
               this.trigger(event.getMythicPlayers().get(0));
            }
         }, delay);
      }
   }

   @Override
   public void buildHotbarMenu() {
   }
}
