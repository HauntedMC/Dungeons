package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import org.bukkit.Material;

@DeclaredFunction
public class FunctionStartDungeon extends DungeonFunction {
   private List<UUID> readyPlayers;

   public FunctionStartDungeon(Map<String, Object> config) {
      super("Start Dungeon", config);
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
      this.setRequiresTarget(true);
   }

   public FunctionStartDungeon() {
      super("Start Dungeon");
      this.targetType = FunctionTargetType.PLAYER;
      this.readyPlayers = new ArrayList<>();
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
      this.setRequiresTarget(true);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.GOLD_BLOCK);
      functionButton.setDisplayName("&6Start Dungeon");
      functionButton.addLore("&eStarts the dungeon when the");
      functionButton.addLore("&etrigger condition is met.");
      return functionButton;
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         if (!instance.isStarted()) {
            if (targets.size() > 1) {
               instance.startGame();
            } else if (!this.readyPlayers.contains(triggerEvent.getPlayer().getUniqueId())) {
               this.readyPlayers.add(triggerEvent.getPlayer().getUniqueId());
               LangUtils.sendMessage(triggerEvent.getPlayer(), "instance.functions.start-dungeon-ready");

               for (DungeonPlayer aPlayer : instance.getPlayers()) {
                  if (!this.readyPlayers.contains(aPlayer.getPlayer().getUniqueId())) {
                     return;
                  }
               }

               instance.startGame();
            }
         }
      }
   }

   @Override
   public void buildHotbarMenu() {
   }

   @Override
   public DungeonFunction clone() {
      FunctionStartDungeon clone = (FunctionStartDungeon)super.clone();
      clone.readyPlayers = new ArrayList<>();
      return clone;
   }
}
