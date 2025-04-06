package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.DungeonDifficulty;
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
public class FunctionSetDifficulty extends DungeonFunction {
   @SavedField
   private String difficulty;

   public FunctionSetDifficulty(Map<String, Object> config) {
      super("Set Difficulty", config);
      this.setCategory(FunctionCategory.DUNGEON);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
   }

   public FunctionSetDifficulty() {
      super("Set Difficulty");
      this.setCategory(FunctionCategory.DUNGEON);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         DungeonDifficulty difficulty = instance.getDungeon().getDifficultyLevels().get(this.difficulty);
         if (difficulty != null) {
            instance.setDifficulty(difficulty);
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.BLAZE_POWDER);
      functionButton.setDisplayName("&dDungeon Difficulty");
      functionButton.addLore("&eSets the difficulty level of the");
      functionButton.addLore("&edungeon, which can be used for");
      functionButton.addLore("&econdition checks and triggers.");
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
            this.button.addLore("&7Current difficulty: &6" + FunctionSetDifficulty.this.difficulty);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize("&eWhat should the dungeons new difficulty be?"));
            player.sendMessage(Util.colorize("&eCurrent difficulty: &6" + FunctionSetDifficulty.this.difficulty));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionSetDifficulty.this.difficulty = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet dungeon difficulty to: &6" + message));
         }
      });
   }
}
