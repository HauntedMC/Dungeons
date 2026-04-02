package nl.hauntedmc.dungeons.api.blocks;

import javax.annotation.Nullable;
import nl.hauntedmc.dungeons.Dungeons;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

public class PushableBlock extends MovingBlock {
   private double weight;
   private int slideFactor = 2;
   private MovingBlock.Direction activeDirection;
   private int slideDist;
   private double speedLossRate;

   public PushableBlock(Location startLocation) {
      super(startLocation, 0.0);
      Dungeons.inst().getMovingBlockManager().addPushable(this);
   }

   @Nullable
   public Player getPushingPlayer() {
      BoundingBox targetBox = this.shulker.getBoundingBox().expand(0.375, 0.0, 0.375);
      Player pusher = null;

      for (Player player : this.location.getWorld().getPlayers()) {
         Location loc = player.getLocation();
         if (targetBox.contains(loc.getX(), loc.getY(), loc.getZ())) {
            pusher = player;
            break;
         }
      }

      return pusher;
   }

   public void pushBlock(MovingBlock.Direction direction, double velocity) {
      double finalVelocity = Math.max(velocity + 0.02 - this.weight, 0.0);
      this.activeDirection = direction;
      this.movementSpeed = finalVelocity;
      this.startSlide();
      if (this.isInactive()) {
         this.start();
      }
   }

   public void advance() {
      this.advance(this.activeDirection);
   }

   @Override
   public void advance(MovingBlock.Direction direction) {
      this.updateSlide();
      super.advance(direction);
      if (this.movementSpeed <= 0.0) {
         this.stop();
      }
   }

   private void startSlide() {
      this.slideDist = this.slideFactor;
      this.speedLossRate = 1.0;
   }

   private void updateSlide() {
      this.slideDist--;
      int half = this.slideFactor / 2;
      if (this.slideDist < half) {
         this.speedLossRate = this.speedLossRate - (1.0 - (float)this.slideDist / half);
         this.movementSpeed = this.movementSpeed * this.speedLossRate;
      }
   }

   public void setSlideFactor(int factor) {
      this.slideFactor = Math.max(factor, 2);
   }

   public double getWeight() {
      return this.weight;
   }

   public void setWeight(double weight) {
      this.weight = weight;
   }

}
