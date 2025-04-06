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
import net.playavalon.mythicdungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

@DeclaredFunction
public class FunctionCheckpoint extends DungeonFunction {
   @SavedField
   private double yaw = 0.0;
   @SavedField
   private double pitch = 0.0;
   @SavedField
   private boolean savePoint = false;

   public FunctionCheckpoint(Map<String, Object> config) {
      super("Checkpoint", config);
      this.targetType = FunctionTargetType.PARTY;
      this.setCategory(FunctionCategory.DUNGEON);
   }

   public FunctionCheckpoint() {
      super("Checkpoint");
      this.targetType = FunctionTargetType.PARTY;
      this.setCategory(FunctionCategory.DUNGEON);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      for (MythicPlayer aPlayer : targets) {
         Location loc = this.location.clone();
         loc.setYaw((float)this.yaw);
         loc.setPitch((float)this.pitch);
         aPlayer.setDungeonRespawn(loc);
         if (this.savePoint && this.instance.as(InstanceProcedural.class) == null) {
            aPlayer.setDungeonSavePoint(loc, this.instance.getDungeon().getWorldName());
         }

         LangUtils.sendMessage(aPlayer.getPlayer(), "instance.functions.checkpoint");
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.ORANGE_BANNER);
      functionButton.setDisplayName("&6Respawn Checkpoint");
      functionButton.addLore("&eSets a respawn checkpoint at");
      functionButton.addLore("&ethis location. If the checkpoint");
      functionButton.addLore("&ehas been triggered, players will");
      functionButton.addLore("&erespawn here after losing a life.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu
         .addMenuItem(
            new MenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.COMPASS);
                  this.button.setDisplayName("&d&lPlayer Direction");
               }

               @Override
               public void onSelect(PlayerEvent event) {
                  Player player = event.getPlayer();
                  FunctionCheckpoint.this.yaw = player.getLocation().getYaw();
                  FunctionCheckpoint.this.pitch = player.getLocation().getPitch();
                  player.sendMessage(
                     Util.colorize(
                        MythicDungeons.debugPrefix
                           + "&aSet player spawn direction to '&6"
                           + FunctionCheckpoint.this.yaw
                           + "&a' degrees. (Where you're looking.)"
                     )
                  );
               }
            }
         );
      this.menu
         .addMenuItem(
            new ToggleMenuItem() {
               @Override
               public void buildButton() {
                  if (FunctionCheckpoint.this.instance.as(InstanceEditableProcedural.class) != null) {
                     this.button = null;
                  } else {
                     this.button = new MenuButton(Material.LIME_BANNER);
                     this.button.setDisplayName("&d&lUse as Save Point");
                     this.button.setEnchanted(FunctionCheckpoint.this.savePoint);
                  }
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(
                     Util.colorize(
                        MythicDungeons.debugPrefix
                           + "&bMakes it so if the player leaves without finishing the dungeon, they will start here next time they play."
                     )
                  );
                  if (!FunctionCheckpoint.this.savePoint) {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&a&lENABLED"));
                  } else {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&c&lDISABLED"));
                  }

                  FunctionCheckpoint.this.savePoint = !FunctionCheckpoint.this.savePoint;
               }
            }
         );
   }

   public double getYaw() {
      return this.yaw;
   }

   public void setYaw(double yaw) {
      this.yaw = yaw;
   }

   public double getPitch() {
      return this.pitch;
   }

   public void setPitch(double pitch) {
      this.pitch = pitch;
   }

   public boolean isSavePoint() {
      return this.savePoint;
   }
}
