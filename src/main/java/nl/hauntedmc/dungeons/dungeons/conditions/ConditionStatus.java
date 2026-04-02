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
      return instance != null && this.status.equals(instance.getStatus());
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.NAME_TAG);
      functionButton.setDisplayName("&dDungeon Status");
      functionButton.addLore("&eChecks if the dungeon is set to");
      functionButton.addLore("&ea specific status.");
      functionButton.addLore("");
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
            player.sendMessage(HelperUtils.colorize("&eWhat status must the dungeon have?"));
            player.sendMessage(HelperUtils.colorize("&eCurrent status needed: &6" + ConditionStatus.this.status));
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionStatus.this.status = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet required status to: &6" + message));
         }
      });
   }
}
