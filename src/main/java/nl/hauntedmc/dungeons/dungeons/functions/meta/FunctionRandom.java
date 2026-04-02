package nl.hauntedmc.dungeons.dungeons.functions.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Material;

@DeclaredFunction
public class FunctionRandom extends FunctionMulti {
   public FunctionRandom(Map<String, Object> config) {
      super("Random Function", config);
   }

   public FunctionRandom() {
      super("Random Function");
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (!this.functions.isEmpty()) {
         DungeonFunction function = this.functions.get(MathUtils.getRandomNumberInRange(0, this.functions.size() - 1));
         List<DungeonPlayer> functionTargets = new ArrayList<>();
         switch (this.targetType) {
            case PLAYER:
               if (triggerEvent.getDPlayer() != null) {
                  functionTargets.add(triggerEvent.getDPlayer());
               }
               break;
            case PARTY:
               functionTargets.addAll(this.instance.getPlayers());
         }

         function.runFunction(triggerEvent, functionTargets);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.STRUCTURE_BLOCK);
      button.setDisplayName("&bFunction Randomizer");
      button.addLore("&eRuns a random function from a");
      button.addLore("&econfigured list.");
      return button;
   }
}
