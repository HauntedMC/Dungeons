package nl.hauntedmc.dungeons.dungeons.conditions;

import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredCondition;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
      return instance != null && HelperUtils.compareVars(instance, this.comparison);
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
            player.sendMessage(HelperUtils.colorize("&eWhat status must the dungeon have?"));
            player.sendMessage(HelperUtils.colorize("&eExamples: \"<variable_name> = 5\", \"<variable_name> < 8\""));
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionInstanceVariable.this.comparison = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet required status to: &6" + message));
         }
      });
   }
}
