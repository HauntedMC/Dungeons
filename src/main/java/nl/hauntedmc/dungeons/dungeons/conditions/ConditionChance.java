package nl.hauntedmc.dungeons.dungeons.conditions;

import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredCondition;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
            player.sendMessage(HelperUtils.colorize("&eWhat is the percent chance of this trigger running? (0.0-1.0)"));
            player.sendMessage(HelperUtils.colorize("&eCurrent chance is: &6" + ConditionChance.this.chance));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            ConditionChance.this.chance = value.orElse(ConditionChance.this.chance);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet success chance to '&6" + ConditionChance.this.chance + "&a'"));
            }
         }
      });
   }
}
