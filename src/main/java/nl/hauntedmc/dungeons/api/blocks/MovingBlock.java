package nl.hauntedmc.dungeons.api.blocks;

import io.papermc.paper.entity.TeleportFlag.EntityState;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Shulker;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class MovingBlock {
   protected double movementSpeed;
   private boolean active;
   protected Location location;
   protected Location startLocation;
   protected Location destination;
   private double distX;
   private double distY;
   private double distZ;
   private double speedX;
   private double speedY;
   private double speedZ;
   protected Shulker shulker;
   protected ArmorStand stand;
   protected FallingBlock fallingBlock;

   public MovingBlock(Location startLocation, double blocksPerSecond) {
      this.startLocation = startLocation.clone();
      this.location = startLocation.clone();
      this.movementSpeed = blocksPerSecond / 20.0;
      this.initEntities();
   }

   public MovingBlock(Location startLocation, double blocksPerSecond, Location destination) {
      this.startLocation = startLocation.clone();
      this.location = startLocation.clone();
      this.movementSpeed = blocksPerSecond / 20.0;
      this.setDestination(destination);
      this.initEntities();
   }

   public void initEntities() {
      World world = this.startLocation.getWorld();
      Location targetLoc = this.startLocation.clone().add(0.5, 0.0, 0.5);

      // Capture the block data *before* clearing the block
      Block block = this.startLocation.getBlock();
      BlockData data = block.getBlockData();
      block.setType(Material.AIR);

      // ArmorStand
      this.stand = world.spawn(targetLoc, ArmorStand.class, as -> {
         as.setVisible(false);
         as.setInvulnerable(true);
         as.setMarker(true);
         as.setSmall(true);
         as.setAI(false);
      });

      // Shulker
      this.shulker = world.spawn(targetLoc, Shulker.class, s -> {
         s.setAI(false);
         s.setInvisible(true);
         s.setInvulnerable(true);
      });

      // FallingBlock (new API)
      this.fallingBlock = world.spawn(targetLoc, FallingBlock.class, fb -> {
         fb.setBlockData(data);
         fb.setInvulnerable(true);
         fb.setGravity(false);
         fb.setDropItem(false);
         fb.shouldAutoExpire(false);
      });

      // Mount passengers
      this.stand.addPassenger(this.shulker);
      this.stand.addPassenger(this.fallingBlock);
   }

   public void setDestination(Location loc) {
      this.setDestination(loc, true);
   }

   public void setDestination(Location loc, boolean start) {
      this.destination = loc;
      this.startLocation = this.location.clone();
      this.updateInterpolation();
      if (start) {
         this.start();
      }
   }

   public void updateInterpolation() {
      if (this.destination != null) {
         this.distX = this.destination.getX() - this.startLocation.getX();
         this.distY = this.destination.getY() - this.startLocation.getY();
         this.distZ = this.destination.getZ() - this.startLocation.getZ();
         double distTotal = Math.abs(this.distX) + Math.abs(this.distY) + Math.abs(this.distZ);
         this.speedX = Math.min(Math.abs(this.distX) / distTotal * this.movementSpeed, this.movementSpeed);
         this.speedY = Math.min(Math.abs(this.distY) / distTotal * this.movementSpeed, this.movementSpeed);
         this.speedZ = Math.min(Math.abs(this.distZ) / distTotal * this.movementSpeed, this.movementSpeed);
         if (MathUtils.isNegative(this.distX)) {
            this.speedX = -this.speedX;
         }

         if (MathUtils.isNegative(this.distY)) {
            this.speedY = -this.speedY;
         }

         if (MathUtils.isNegative(this.distZ)) {
            this.speedZ = -this.speedZ;
         }
      }
   }

   public void advance(MovingBlock.Direction direction) {
      Location newLocation = this.location.clone();
      switch (direction) {
         case NORTH:
            newLocation.setZ(this.location.getZ() - this.movementSpeed);
            break;
         case EAST:
            newLocation.setX(this.location.getX() + this.movementSpeed);
            break;
         case SOUTH:
            newLocation.setZ(this.location.getZ() + this.movementSpeed);
            break;
         case WEST:
            newLocation.setX(this.location.getX() - this.movementSpeed);
      }

      this.location = newLocation;
      this.updateEntity();
      this.applyInheritedVelocityToPassengers(this.movementSpeed, this.movementSpeed, this.movementSpeed);
   }

   public void advanceToDestination() {
      Location newLocation = this.location.clone();
      newLocation.setX(this.location.getX() + this.speedX);
      newLocation.setY(this.location.getY() + this.speedY);
      newLocation.setZ(this.location.getZ() + this.speedZ);
      if (Math.abs(newLocation.getX() - this.startLocation.getX()) > Math.abs(this.distX)) {
         newLocation.setX(this.destination.getX());
      }

      if (Math.abs(newLocation.getY() - this.startLocation.getY()) > Math.abs(this.distY)) {
         newLocation.setY(this.destination.getY());
      }

      if (Math.abs(newLocation.getZ() - this.startLocation.getZ()) > Math.abs(this.distZ)) {
         newLocation.setZ(this.destination.getZ());
      }

      this.location = newLocation;
      if (this.location.getX() == this.destination.getX() && this.location.getY() == this.destination.getY() && this.location.getZ() == this.destination.getZ()
         )
       {
         this.destination = null;
         this.stop();
      }

      this.updateEntity();
      this.applyInheritedVelocityToPassengers(this.speedX, this.speedY, this.speedZ);
   }

   public void updateEntity() {
      Location modLocation = this.location.clone().add(0.5, 0.0, 0.5);
      if (Dungeons.inst().isSupportsTeleportFlags()) {
         this.stand.teleport(modLocation, EntityState.RETAIN_PASSENGERS);
      } else {
         this.stand.removePassenger(this.shulker);
         this.stand.removePassenger(this.fallingBlock);
         this.stand.teleport(modLocation);
         this.stand.addPassenger(this.shulker);
         this.stand.addPassenger(this.fallingBlock);
      }
   }

   public void applyInheritedVelocityToPassengers(double inheritedX, double inheritedY, double inheritedZ) {
      if (Dungeons.inst().isInheritedVelocityEnabled()) {
         BoundingBox targetBox = this.shulker.getBoundingBox().shift(0.0, 1.0, 0.0).expand(-0.3, 0.0, -0.3).expand(BlockFace.UP, -0.8);

         for (Entity ent : this.location.getWorld().getNearbyEntities(targetBox)) {
            if (ent != this.stand && ent != this.shulker && ent != this.fallingBlock && ent.isOnGround()) {
               Vector entVel = ent.getVelocity();
               double absX = Math.abs(entVel.getX());
               double absZ = Math.abs(entVel.getZ());
               double finalX = inheritedX / 2.2;
               double finalZ = inheritedZ / 2.2;
               double finalY = 0.0;
               if (Bukkit.getServer().getAllowFlight()) {
                  finalY = MathUtils.isNegative(inheritedY) ? 0.0 : inheritedY * 4.0;
               }

               double absFinalX = Math.abs(finalX);
               double absFinalZ = Math.abs(finalZ);
               if (absX < absFinalX && absX > 0.0) {
                  finalX = HelperUtils.addRespectedVelocity(inheritedX, -(entVel.getX() + finalX));
               }

               if (absZ < absFinalZ && absZ > 0.0) {
                  finalZ = HelperUtils.addRespectedVelocity(inheritedZ, -(entVel.getZ() + finalZ));
               }

               entVel.add(new Vector(finalX, finalY, finalZ));
               Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> ent.setVelocity(entVel), 1L);
            }
         }
      }
   }

   public void start() {
      if (!this.active) {
         Dungeons.inst().getMovingBlockManager().add(this);
         this.active = true;
         if (this.destination != null) {
            if (this.destination.getBlockY() > this.startLocation.getBlockY()) {
               Dungeons.inst()
                  .getLogger()
                  .warning(
                     HelperUtils.colorize(
                        "&cWARNING :: Vertically moving blocks won't lift players unless `allow-flight` in server.properties is set to TRUE. Use with caution!"
                     )
                  );
            }
         }
      }
   }

   public void stop() {
      if (this.active) {
         Dungeons.inst().getMovingBlockManager().remove(this);
         this.active = false;
      }
   }

   public boolean isInactive() {
      return !this.active;
   }

   public Location getLocation() {
      return this.location;
   }


   public Location getDestination() {
      return this.destination;
   }

   public enum Direction {
      NORTH,
      EAST,
      SOUTH,
      WEST,
      NONE
   }
}
