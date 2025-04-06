package net.playavalon.mythicdungeons.dungeons.functions.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (!this.functions.isEmpty()) {
         DungeonFunction function = this.functions.get(MathUtils.getRandomNumberInRange(0, this.functions.size() - 1));
         List<MythicPlayer> functionTargets = new ArrayList<>();
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
