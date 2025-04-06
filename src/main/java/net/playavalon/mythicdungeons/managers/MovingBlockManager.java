package net.playavalon.mythicdungeons.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.blocks.MovingBlock;
import net.playavalon.mythicdungeons.api.blocks.MovingBlockCluster;
import net.playavalon.mythicdungeons.api.blocks.PushableBlock;
import net.playavalon.mythicdungeons.api.blocks.PushableBlockCluster;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class MovingBlockManager {
   private final BukkitRunnable blockTicker;
   private final List<MovingBlock> blocks = new ArrayList<>();
   private final Map<String, MovingBlockCluster> blockClusters = new HashMap<>();
   private final List<PushableBlock> pushableBlocks = new ArrayList<>();
   private final Map<String, PushableBlockCluster> pushableBlockClusters = new HashMap<>();

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
                  MovingBlock.Direction dir = Util.getDirectionFromAngle(-angle, 90.0);
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
      this.blockTicker.runTaskTimer(MythicDungeons.inst(), 0L, 1L);
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

   public void removePushable(PushableBlock block) {
      this.pushableBlocks.remove(block);
   }

   public void disable() {
      if (this.blockTicker != null && !this.blockTicker.isCancelled()) {
         this.blockTicker.cancel();
      }
   }
}
