package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      for (DungeonPlayer aPlayer : targets) {
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
                     HelperUtils.colorize(
                        Dungeons.logPrefix
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
                     HelperUtils.colorize(
                        Dungeons.logPrefix
                           + "&bMakes it so if the player leaves without finishing the dungeon, they will start here next time they play."
                     )
                  );
                  if (!FunctionCheckpoint.this.savePoint) {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&a&lENABLED"));
                  } else {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&c&lDISABLED"));
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
