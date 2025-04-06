package net.playavalon.mythicdungeons.dungeons.conditions;

import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredCondition;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredCondition
public class ConditionChance extends TriggerCondition {
   @SavedField
   private double chance = 1.0;

   public ConditionChance(Map<String, Object> config) {
      super("Chance", config);
   }

   public ConditionChance() {
      super("Chance");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      return MathUtils.getRandomBoolean(this.chance);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.ENDER_EYE);
      functionButton.setDisplayName("&dTrigger Chance");
      functionButton.addLore("&eRandomizes whether or not the");
      functionButton.addLore("&etrigger should run.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.ENDER_EYE);
            this.button.setDisplayName("&d&lSet Chance");
            this.button.addLore("&7Current chance: &6" + ConditionChance.this.chance);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize("&eWhat is the percent chance of this trigger running? (0.0-1.0)"));
            player.sendMessage(Util.colorize("&eCurrent chance is: &6" + ConditionChance.this.chance));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            ConditionChance.this.chance = value.orElse(ConditionChance.this.chance);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet success chance to '&6" + ConditionChance.this.chance + "&a'"));
            }
         }
      });
   }
}
