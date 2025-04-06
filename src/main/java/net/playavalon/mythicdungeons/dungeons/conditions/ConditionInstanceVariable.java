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
public class ConditionInstanceVariable extends TriggerCondition {
   @SavedField
   private String comparison;

   public ConditionInstanceVariable(Map<String, Object> config) {
      super("Instance Variable", config);
   }

   public ConditionInstanceVariable() {
      super("Instance Variable");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      InstancePlayable instance = this.instance.asPlayInstance();
      return instance == null ? false : Util.compareVars(instance, this.comparison);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
      functionButton.setDisplayName("&dVariable Comparison");
      functionButton.addLore("&eChecks if a variable matches");
      functionButton.addLore("&ea given comparison value.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NAME_TAG);
            this.button.setDisplayName("&d&lSet Comparison");
            this.button.addLore("&7Current comparison: &6" + ConditionInstanceVariable.this.comparison);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize("&eWhat status must the dungeon have?"));
            player.sendMessage(Util.colorize("&eExamples: \"<variable_name> = 5\", \"<variable_name> < 8\""));
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionInstanceVariable.this.comparison = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet required status to: &6" + message));
         }
      });
   }
}
