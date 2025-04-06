package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
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
            this.button.addLore("&7Current status: &6" + FunctionSetStatus.this.status);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize("&eWhat should the dungeons new status be?"));
            player.sendMessage(Util.colorize("&eCurrent status: &6" + FunctionSetStatus.this.status));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionSetStatus.this.status = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet dungeon status to: &6" + message));
         }
      });
   }
}
