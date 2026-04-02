package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.DungeonDifficulty;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
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
            player.sendMessage(HelperUtils.colorize("&eWhat should the dungeons new difficulty be?"));
            player.sendMessage(HelperUtils.colorize("&eCurrent difficulty: &6" + FunctionSetDifficulty.this.difficulty));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionSetDifficulty.this.difficulty = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet dungeon difficulty to: &6" + message));
         }
      });
   }
}
