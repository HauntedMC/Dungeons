package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         if (!instance.isStarted()) {
            if (targets.size() > 1) {
               instance.startGame();
            } else if (!this.readyPlayers.contains(triggerEvent.getPlayer().getUniqueId())) {
               this.readyPlayers.add(triggerEvent.getPlayer().getUniqueId());
               LangUtils.sendMessage(triggerEvent.getPlayer(), "instance.functions.start-dungeon-ready");

               for (MythicPlayer aPlayer : instance.getPlayers()) {
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
