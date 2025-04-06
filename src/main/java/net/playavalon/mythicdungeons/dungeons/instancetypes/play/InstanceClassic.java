package net.playavalon.mythicdungeons.dungeons.instancetypes.play;

import java.util.concurrent.CountDownLatch;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonClassic;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionLeaveDungeon;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionStartDungeon;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public class InstanceClassic extends InstancePlayable {
   protected DungeonClassic dungeon;

   public InstanceClassic(DungeonClassic dungeon, CountDownLatch latch) {
      super(dungeon, latch);
      this.dungeon = dungeon;
   }

   @Override
   public void onLoadGame() {
      if (this.dungeon.isLobbyEnabled()) {
         DungeonFunction startFunction = null;

         for (DungeonFunction function : this.dungeon.getFunctions().values()) {
            if (function instanceof FunctionStartDungeon) {
               startFunction = function;
               DungeonFunction newFunction = function.clone();
               Location loc = newFunction.getLocation();
               loc.setWorld(this.instanceWorld);
               newFunction.enable(this, loc);
               this.functions.put(loc, newFunction);
            }

            if (function instanceof FunctionLeaveDungeon) {
               startFunction = function;
               DungeonFunction newFunction = function.clone();
               Location loc = newFunction.getLocation();
               loc.setWorld(this.instanceWorld);
               newFunction.enable(this, loc);
               this.functions.put(loc, newFunction);
            }
         }

         if (startFunction == null) {
            MythicDungeons.inst()
               .getLogger()
               .info(
                  MythicDungeons.debugPrefix
                     + Util.colorize("&cERROR :: Dungeon '" + this.dungeon.getWorldName() + "' is improperly configured! Missing start function!")
               );
         }
      } else {
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), this::startGame, 1L);
      }
   }

   public DungeonClassic getDungeon() {
      return this.dungeon;
   }
}
