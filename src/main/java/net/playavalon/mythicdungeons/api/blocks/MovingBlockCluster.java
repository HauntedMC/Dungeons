package net.playavalon.mythicdungeons.api.blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;

public class MovingBlockCluster {
   private boolean active;
   private double movementSpeed;
   private Location anchor;
   private final List<MovingBlock> blocks = new ArrayList<>();

   public MovingBlockCluster() {
   }

   public MovingBlockCluster(Collection<Location> blocks, double movementSpeed, Location anchor) {
      this.movementSpeed = movementSpeed;
      this.anchor = anchor.clone();

      for (Location loc : blocks) {
         MovingBlock block = new MovingBlock(loc, movementSpeed);
         this.blocks.add(block);
      }
   }

   public void setDestination(Location dest) {
      for (MovingBlock block : this.blocks) {
         Location loc = block.startLocation;
         int diffX = loc.getBlockX() - this.anchor.getBlockX();
         int diffY = loc.getBlockY() - this.anchor.getBlockY();
         int diffZ = loc.getBlockZ() - this.anchor.getBlockZ();
         Location finalDest = dest.clone().add(diffX, diffY, diffZ);
         block.setDestination(finalDest);
      }
   }

   public void start() {
      this.active = true;

      for (MovingBlock block : this.blocks) {
         block.start();
      }
   }

   public void stop() {
      this.active = false;

      for (MovingBlock block : this.blocks) {
         block.stop();
      }
   }

   public boolean isActive() {
      return this.active;
   }

   public double getMovementSpeed() {
      return this.movementSpeed;
   }

   public void setMovementSpeed(double movementSpeed) {
      this.movementSpeed = movementSpeed;
   }

   public Location getAnchor() {
      return this.anchor;
   }

   public void setAnchor(Location anchor) {
      this.anchor = anchor;
   }

   public List<MovingBlock> getBlocks() {
      return this.blocks;
   }
}
