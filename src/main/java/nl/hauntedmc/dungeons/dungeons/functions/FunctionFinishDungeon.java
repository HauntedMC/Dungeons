package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.PlayerFinishDungeonEvent;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionFinishDungeon extends DungeonFunction {
   @SavedField
   private boolean leave = true;

   public FunctionFinishDungeon(Map<String, Object> config) {
      super("Finish Dungeon", config);
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionFinishDungeon() {
      super("Finish Dungeon");
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.DUNGEON);
      this.setAllowRetriggerByDefault(true);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.ENCHANTING_TABLE);
      functionButton.setDisplayName("&6Finish Dungeon");
      functionButton.addLore("&eFormally finishes the dungeon,");
      functionButton.addLore("&emarking the dungeon as finished");
      functionButton.addLore("&efor the player.");
      functionButton.addLore("&eAlso puts the dungeon on cooldown");
      functionButton.addLore("&eif there is an access cooldown.");
      return functionButton;
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (!(this.instance instanceof InstanceEditable)) {
         InstancePlayable instance = (InstancePlayable)this.instance;

         for (DungeonPlayer aPlayer : targets) {
            PlayerFinishDungeonEvent finishEvent = new PlayerFinishDungeonEvent(instance, aPlayer);
            Bukkit.getPluginManager().callEvent(finishEvent);
            instance.setDungeonFinished(true);
            Player player = aPlayer.getPlayer();
            LangUtils.sendMessage(player, "instance.functions.finish-dungeon", instance.getDungeon().getDisplayName());
            instance.applyLootCooldowns(player);
            instance.pushPlayerRewards(player);
            AbstractDungeon dungeon = instance.getDungeon();
            if (!dungeon.hasPlayerCompletedDungeon(player)) {
               dungeon.setPlayerCompletedDungeon(player);
            }

            if (dungeon.isAccessCooldownEnabled() && dungeon.isCooldownOnFinish()) {
               instance.getDungeon().addAccessCooldown(player);
            }

            if (this.leave) {
               Location exitLoc = dungeon.getExitLoc();
               if (exitLoc != null) {
                  aPlayer.setSavedPosition(exitLoc);
               }

               instance.removePlayer(aPlayer);
            }
         }
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.ENCHANTING_TABLE);
            this.button.setDisplayName("&d&lLeave Dungeon");
            this.button.setEnchanted(FunctionFinishDungeon.this.leave);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionFinishDungeon.this.leave) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Player leaves after triggering&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Player stays after triggering&a'"));
            }

            FunctionFinishDungeon.this.leave = !FunctionFinishDungeon.this.leave;
         }
      });
   }

   public boolean isLeave() {
      return this.leave;
   }

   public void setLeave(boolean leave) {
      this.leave = leave;
   }
}
