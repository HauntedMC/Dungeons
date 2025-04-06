package net.playavalon.mythicdungeons.mythic;

import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.INoTargetSkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.events.dungeon.RemoteTriggerEvent;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@MythicMechanic(
   name = "dungeonsignal",
   aliases = {"dsignal"},
   author = "MarcatoSound"
)
public class DungeonSignalMechanic implements INoTargetSkill {
   protected String triggerName;
   protected double radius;

   public DungeonSignalMechanic(MythicLineConfig mlc) {
      this.triggerName = mlc.getString(new String[]{"signal", "s"}, "trigger", new String[0]);
      this.radius = mlc.getDouble(new String[]{"radius", "r"}, 0.0);
   }

   public ThreadSafetyLevel getThreadSafetyLevel() {
      return ThreadSafetyLevel.SYNC_ONLY;
   }

   public SkillResult cast(SkillMetadata skillMetadata) {
      World world = BukkitAdapter.adapt(skillMetadata.getCaster().getEntity().getWorld());
      if (world.getPlayers().size() <= 0) {
         return SkillResult.CONDITION_FAILED;
      } else {
         Player player = (Player)world.getPlayers().get(0);
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         InstancePlayable inst = aPlayer.getInstance().asPlayInstance();
         if (inst == null) {
            return SkillResult.CONDITION_FAILED;
         } else {
            Entity caster = skillMetadata.getCaster().getEntity().getBukkitEntity();
            MythicPlayer mTarget = null;
            if (skillMetadata.getCaster().getEntity().getBukkitEntity() instanceof Player) {
               mTarget = MythicDungeons.inst().getMythicPlayer((Player)caster);
            }

            RemoteTriggerEvent event = new RemoteTriggerEvent(this.triggerName, null, inst, this.radius, null, mTarget);
            Bukkit.getPluginManager().callEvent(event);
            return SkillResult.SUCCESS;
         }
      }
   }
}
