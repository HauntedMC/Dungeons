package net.playavalon.mythicdungeons.mythic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.IEntityCondition;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import org.bukkit.entity.Player;

public class HasFinishedDungeonCondition implements IEntityCondition {
   private final String dungeon;

   public HasFinishedDungeonCondition(MythicLineConfig mlc) {
      this.dungeon = mlc.getString(new String[]{"dungeon", "d"});
   }

   public boolean check(AbstractEntity target) {
      if (!target.isPlayer()) {
         return false;
      } else {
         AbstractDungeon dungeon = MythicDungeons.inst().getDungeonManager().get(this.dungeon);
         if (dungeon == null) {
            return false;
         } else {
            Player player = (Player)target.getBukkitEntity();
            return dungeon.hasPlayerCompletedDungeon(player);
         }
      }
   }
}
