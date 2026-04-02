package nl.hauntedmc.dungeons.managers;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.blocks.MovingBlock;
import nl.hauntedmc.dungeons.api.blocks.PushableBlock;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class MovingBlockManager {
   private final BukkitRunnable blockTicker;
   private final List<MovingBlock> blocks = new ArrayList<>();
   private final List<PushableBlock> pushableBlocks = new ArrayList<>();

   public MovingBlockManager() {
      this.blockTicker = new BukkitRunnable() {
         public void run() {
            for (PushableBlock pushable : MovingBlockManager.this.pushableBlocks) {
               Player player = pushable.getPushingPlayer();
               if (player != null) {
                  Location blockLoc = pushable.getLocation().clone();
                  Location playerLoc = player.getLocation().clone();
                  blockLoc.setY(0.0);
                  blockLoc.add(0.5, 0.0, 0.5);
                  playerLoc.setY(0.0);
                  Vector playerVec = playerLoc.toVector();
                  Vector blockVec = blockLoc.toVector().subtract(playerVec);
                  double angle = blockVec.angle(playerVec);
                  double dot = new Vector(0, 1, 0).dot(blockVec.crossProduct(playerVec));
                  angle *= Math.signum(dot);
                  angle = Math.toDegrees(angle);
                  MovingBlock.Direction dir = HelperUtils.getDirectionFromAngle(-angle, 90.0);
                  pushable.pushBlock(dir, player.getWalkSpeed());
               }
            }

            if (!MovingBlockManager.this.blocks.isEmpty()) {
               for (MovingBlock block : new ArrayList<>(MovingBlockManager.this.blocks)) {
                  if (block instanceof PushableBlock pushablex) {
                     pushablex.advance();
                  } else if (block.getDestination() != null) {
                     block.advanceToDestination();
                  }
               }
            }
         }
      };
      this.blockTicker.runTaskTimer(Dungeons.inst(), 0L, 1L);
   }

   public void add(MovingBlock block) {
      this.blocks.add(block);
   }

   public void addPushable(PushableBlock block) {
      this.pushableBlocks.add(block);
   }

   public void remove(MovingBlock block) {
      this.blocks.remove(block);
   }

   public void disable() {
      if (this.blockTicker != null && !this.blockTicker.isCancelled()) {
         this.blockTicker.cancel();
      }
   }
}
