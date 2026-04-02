package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionSetStatus extends DungeonFunction {
   @SavedField
   private String status;

   public FunctionSetStatus(Map<String, Object> config) {
      super("Set Status", config);
      this.setCategory(FunctionCategory.DUNGEON);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
   }

   public FunctionSetStatus() {
      super("Set Status");
      this.setCategory(FunctionCategory.DUNGEON);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         instance.setStatus(this.status);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.NAME_TAG);
      functionButton.setDisplayName("&dDungeon Status");
      functionButton.addLore("&eSets the status of the dungeon,");
      functionButton.addLore("&ewhich can be used for condition");
      functionButton.addLore("&echecks and triggers.");
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
            this.button.addLore("&7Current status: &6" + FunctionSetStatus.this.status);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize("&eWhat should the dungeons new status be?"));
            player.sendMessage(HelperUtils.colorize("&eCurrent status: &6" + FunctionSetStatus.this.status));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionSetStatus.this.status = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet dungeon status to: &6" + message));
         }
      });
   }
}
