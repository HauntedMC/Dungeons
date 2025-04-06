package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.Material;

@DeclaredFunction
public class FunctionLeaveDungeon extends DungeonFunction {
   public FunctionLeaveDungeon(Map<String, Object> config) {
      super("Leave Dungeon", config);
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionLeaveDungeon() {
      super("Leave Dungeon");
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      for (MythicPlayer aPlayer : targets) {
         this.instance.removePlayer(aPlayer, false);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.LADDER);
      functionButton.setDisplayName("&6Leave Dungeon");
      functionButton.addLore("&eImmediately takes the player");
      functionButton.addLore("&eor players out of the dungeon.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
   }
}
