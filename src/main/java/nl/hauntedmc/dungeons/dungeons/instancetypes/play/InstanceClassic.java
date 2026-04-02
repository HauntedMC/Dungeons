package nl.hauntedmc.dungeons.dungeons.instancetypes.play;

import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonClassic;
import nl.hauntedmc.dungeons.dungeons.functions.FunctionLeaveDungeon;
import nl.hauntedmc.dungeons.dungeons.functions.FunctionStartDungeon;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
            Dungeons.inst()
               .getLogger()
               .info(
                  Dungeons.logPrefix
                     + HelperUtils.colorize("&cERROR :: Dungeon '" + this.dungeon.getWorldName() + "' is improperly configured! Missing start function!")
               );
         }
      } else {
         Bukkit.getScheduler().runTaskLater(Dungeons.inst(), this::startGame, 1L);
      }
   }

   public DungeonClassic getDungeon() {
      return this.dungeon;
   }
}
