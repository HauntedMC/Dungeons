package net.playavalon.mythicdungeons.dungeons.instancetypes.play;

import java.util.concurrent.CountDownLatch;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonContinuous;
import org.bukkit.Bukkit;

public class InstanceContinuous extends InstanceClassic {
   protected DungeonContinuous dungeon;
   private int totalPlayersSinceStart;

   public InstanceContinuous(DungeonContinuous dungeon, CountDownLatch latch) {
      super(dungeon, latch);
      this.dungeon = dungeon;
   }

   @Override
   public void onDispose() {
      super.onDispose();
      this.dungeon.getActiveInstances().remove(this);
      if (this.dungeon.getConfig().getBoolean("ContinuousDungeons.LoadAtStart", false)) {
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> this.dungeon.preLoad(), 20L);
      }
   }

   public DungeonContinuous getDungeon() {
      return this.dungeon;
   }

   public int getTotalPlayersSinceStart() {
      return this.totalPlayersSinceStart;
   }

   public void setTotalPlayersSinceStart(int totalPlayersSinceStart) {
      this.totalPlayersSinceStart = totalPlayersSinceStart;
   }
}
