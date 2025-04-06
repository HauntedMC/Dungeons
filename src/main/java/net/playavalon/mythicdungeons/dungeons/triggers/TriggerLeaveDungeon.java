package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.PlayerLeaveDungeonEvent;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

@DeclaredTrigger
public class TriggerLeaveDungeon extends DungeonTrigger {
   @SavedField
   private boolean preventLeaving = false;

   public TriggerLeaveDungeon(Map<String, Object> config) {
      super("Player Leave", config);
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.DUNGEON);
      this.setHasTarget(true);
   }

   public TriggerLeaveDungeon() {
      super("Player Leave");
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.DUNGEON);
      this.setHasTarget(true);
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onPlayerLeave(PlayerLeaveDungeonEvent event) {
      MythicPlayer mPlayer = event.getMPlayer();
      if (this.preventLeaving) {
         event.setCancelled(true);
      }

      this.trigger(mPlayer, !this.allowRetrigger);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.DARK_OAK_DOOR);
      functionButton.setDisplayName("&6Leave Dungeon Listener");
      functionButton.addLore("&eTriggered when a player leaves");
      functionButton.addLore("&ethe dungeon.");
      functionButton.addLore("");
      functionButton.addLore("&7Optionally, can also stop players");
      functionButton.addLore("&7from leaving with /leave or leave");
      functionButton.addLore("&7functions.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&d&lPrevent Leaving");
            this.button.setEnchanted(TriggerLeaveDungeon.this.preventLeaving);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerLeaveDungeon.this.preventLeaving) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Player will be stopped from leaving&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Player will be allowed to leave&a'"));
            }

            TriggerLeaveDungeon.this.preventLeaving = !TriggerLeaveDungeon.this.preventLeaving;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerLeaveDungeon.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerLeaveDungeon.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerLeaveDungeon.this.allowRetrigger = !TriggerLeaveDungeon.this.allowRetrigger;
         }
      });
   }
}
