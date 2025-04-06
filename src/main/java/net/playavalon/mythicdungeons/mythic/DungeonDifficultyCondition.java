package net.playavalon.mythicdungeons.mythic;

import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.adapters.AbstractWorld;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.ILocationCondition;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;

public class DungeonDifficultyCondition implements ILocationCondition {
   private final String difficulty;

   public DungeonDifficultyCondition(MythicLineConfig mlc) {
      this.difficulty = mlc.getString(new String[]{"difficulty", "d"});
   }

   public boolean check(AbstractLocation target) {
      AbstractWorld aWorld = target.getWorld();
      AbstractInstance inst = MythicDungeons.inst().getDungeonInstance(aWorld.getName());
      if (inst == null) {
         return false;
      } else if (!inst.isPlayInstance()) {
         return false;
      } else {
         return inst.asPlayInstance().getDifficulty() == null
            ? this.difficulty.equals("DEFAULT")
            : inst.asPlayInstance().getDifficulty().getNamespace().equals(this.difficulty);
      }
   }
}
