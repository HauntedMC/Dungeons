package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      for (DungeonPlayer aPlayer : targets) {
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
