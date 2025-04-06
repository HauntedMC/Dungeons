package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.blocks.MovingBlockCluster;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ParticleUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.util.BoundingBox;

@DeclaredFunction
public class FunctionMovingCluster extends FunctionMovingBlock {
   @SavedField
   private List<Location> blockLocs = new ArrayList<>();
   private Location pos1;
   private Location pos2;
   private List<Location> instanceBlockLocs;
   private MovingBlockCluster cluster;

   public FunctionMovingCluster(Map<String, Object> config) {
      super("Moving Block Cluster", config);
   }

   public FunctionMovingCluster() {
      super("Moving Block Cluster");
   }

   @Override
   public void onEnable() {
      if (this.destination == null) {
         MythicDungeons.inst()
            .getLogger()
            .info(
               Util.colorize(
                  "&cERROR :: Moving block group function in dungeon '" + this.instance.getDungeon().getWorldName() + "' has an invalid destination!"
               )
            );
         MythicDungeons.inst()
            .getLogger()
            .info(Util.colorize("&cFunction is at &6" + this.location.getX() + ", " + this.location.getY() + ", " + this.location.getZ()));
      } else {
         this.instanceDestination = this.destination.clone();
         this.instanceDestination.setWorld(this.location.getWorld());
         this.instanceBlockLocs = new ArrayList<>();

         for (Location loc : this.blockLocs) {
            if (loc != null) {
               Location instanceLoc = loc.clone();
               instanceLoc.setWorld(this.instance.getInstanceWorld());
               this.instanceBlockLocs.add(instanceLoc);
            }
         }
      }
   }

   @Override
   public void onDisable() {
      if (this.cluster != null) {
         this.cluster.stop();
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (this.cluster == null) {
         this.cluster = new MovingBlockCluster(this.instanceBlockLocs, this.speed, this.location);
         this.cluster.setDestination(this.destination);
      }

      if (!this.cluster.isActive()) {
         this.cluster.start();
      } else {
         this.cluster.stop();
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.FURNACE_MINECART);
      functionButton.setDisplayName("&aMoving Block Cluster");
      functionButton.addLore("&eTurns a group of blocks into");
      functionButton.addLore("&emoving blocks with a configurable");
      functionButton.addLore("&edestination.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      super.buildHotbarMenu();
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NETHER_STAR);
            this.button.setDisplayName("&d&lSelect Area");
            this.button.addLore("&8Right-click selects corner 1");
            this.button.addLore("&8Left-click selects corner 2");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            Block block = player.getTargetBlockExact(10);
            if (block == null) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet first corner to your position."));
               FunctionMovingCluster.this.pos1 = player.getLocation().toBlockLocation();
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet first corner to target block."));
               FunctionMovingCluster.this.pos1 = block.getLocation();
            }

            if (FunctionMovingCluster.this.pos2 != null) {
               FunctionMovingCluster.this.captureSelectionArea(player);
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&bAdded blocks in selected area!"));
            }
         }

         @Override
         public void onClick(PlayerEvent event) {
            Player player = event.getPlayer();
            Block block = player.getTargetBlockExact(10);
            if (block == null) {
               FunctionMovingCluster.this.pos2 = player.getLocation().toBlockLocation();
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet second corner to your position."));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet second corner to target block."));
               FunctionMovingCluster.this.pos2 = block.getLocation();
            }

            if (FunctionMovingCluster.this.pos1 != null) {
               FunctionMovingCluster.this.captureSelectionArea(player);
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&bAdded blocks in selected area!"));
            }
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&d&lRemove Area");
            this.button.addLore("&8Right-click selects corner 1");
            this.button.addLore("&8Left-click selects corner 2");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            Block block = player.getTargetBlockExact(10);
            if (block == null) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet first corner to your position."));
               FunctionMovingCluster.this.pos1 = player.getLocation().toBlockLocation();
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet first corner to target block."));
               FunctionMovingCluster.this.pos1 = block.getLocation();
            }

            if (FunctionMovingCluster.this.pos2 != null) {
               FunctionMovingCluster.this.removeSelectionArea(player);
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&bRemoved blocks in selected area!"));
            }
         }

         @Override
         public void onClick(PlayerEvent event) {
            Player player = event.getPlayer();
            Block block = player.getTargetBlockExact(10);
            if (block == null) {
               FunctionMovingCluster.this.pos2 = player.getLocation().toBlockLocation();
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet second corner to your position."));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet second corner to target block."));
               FunctionMovingCluster.this.pos2 = block.getLocation();
            }

            if (FunctionMovingCluster.this.pos1 != null) {
               FunctionMovingCluster.this.removeSelectionArea(player);
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&bRemoved blocks in selected area!"));
            }
         }
      });
   }

   private void captureSelectionArea(Player player) {
      BoundingBox area = new BoundingBox(
         this.pos1.getBlockX(), this.pos1.getBlockY(), this.pos1.getBlockZ(), this.pos2.getBlockX(), this.pos2.getBlockY(), this.pos2.getBlockZ()
      );
      area.expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

      for (double x = area.getMinX(); x < area.getMaxX(); x++) {
         for (double y = area.getMinY(); y < area.getMaxY(); y++) {
            for (double z = area.getMinZ(); z < area.getMaxZ(); z++) {
               Location target = new Location(this.instance.getInstanceWorld(), x, y, z);
               Block block = target.getBlock();
               if (!block.getType().isAir() && !block.isLiquid()) {
                  target.setWorld(null);
                  if (!this.blockLocs.contains(target)) {
                     this.blockLocs.add(target);
                  }
               }
            }
         }
      }

      ParticleUtils.displayBoundingBox(player, 100, area);
      this.pos1 = null;
      this.pos2 = null;
   }

   private void removeSelectionArea(Player player) {
      BoundingBox area = new BoundingBox(
         this.pos1.getBlockX(), this.pos1.getBlockY(), this.pos1.getBlockZ(), this.pos2.getBlockX(), this.pos2.getBlockY(), this.pos2.getBlockZ()
      );
      area.expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      List<Location> removal = new ArrayList<>();

      for (Location loc : this.blockLocs) {
         if (area.contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
            removal.add(loc);
         }
      }

      this.blockLocs.removeAll(removal);
      ParticleUtils.displayBoundingBox(player, 40, area);
      this.pos1 = null;
      this.pos2 = null;
   }

   private void displayBoundingBox(Player to) {
   }
}
