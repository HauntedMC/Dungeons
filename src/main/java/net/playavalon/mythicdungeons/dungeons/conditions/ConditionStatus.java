package net.playavalon.mythicdungeons.dungeons.conditions;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredCondition;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredCondition
public class ConditionStatus extends TriggerCondition {
   @SavedField
   private String status;

   public ConditionStatus(Map<String, Object> config) {
      super("Status", config);
   }

   public ConditionStatus() {
      super("Status");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      InstancePlayable instance = this.instance.asPlayInstance();
      return instance == null ? false : this.status.equals(instance.getStatus());
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.NAME_TAG);
      functionButton.setDisplayName("&dDungeon Status");
      functionButton.addLore("&eChecks if the dungeon is set to");
      functionButton.addLore("&ea specific status.");
      functionButton.addLore("");
      functionButton.addLore("&7Similar to Mythic Mobs stances.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NAME_TAG);
            this.button.setDisplayName("&d&lSet Status");
            this.button.addLore("&7Current status: &6" + ConditionStatus.this.status);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize("&eWhat status must the dungeon have?"));
            player.sendMessage(Util.colorize("&eCurrent status needed: &6" + ConditionStatus.this.status));
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionStatus.this.status = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet required status to: &6" + message));
         }
      });
   }
}
