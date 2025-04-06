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
public class ConditionDifficulty extends TriggerCondition {
   @SavedField
   private String difficulty;

   public ConditionDifficulty(Map<String, Object> config) {
      super("Difficulty", config);
   }

   public ConditionDifficulty() {
      super("Difficulty");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance == null) {
         return false;
      } else {
         String instanceDif;
         if (instance.getDifficulty() == null) {
            instanceDif = "DEFAULT";
         } else {
            instanceDif = instance.getDifficulty().getNamespace();
         }

         return this.difficulty.equals(instanceDif);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.BLAZE_POWDER);
      functionButton.setDisplayName("&dDungeon Difficulty");
      functionButton.addLore("&eChecks if the dungeon is set to");
      functionButton.addLore("&ea specific difficulty level.");
      functionButton.addLore("");
      functionButton.addLore("&cNOTE: Unrelated to Minecraft's");
      functionButton.addLore("&c/difficulty command!");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BLAZE_POWDER);
            this.button.setDisplayName("&d&lSet Difficulty");
            this.button.addLore("&7Current difficulty: &6" + ConditionDifficulty.this.difficulty);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize("&eWhat difficulty level must the dungeon be?"));
            player.sendMessage(Util.colorize("&eCurrent difficulty needed: &6" + ConditionDifficulty.this.difficulty));
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionDifficulty.this.difficulty = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet required difficulty to: &6" + message));
         }
      });
   }
}
