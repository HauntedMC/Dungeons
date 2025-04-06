package net.playavalon.mythicdungeons.mythic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.IEntityComparisonCondition;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;

public class SharesDungeonPartyCondition implements IEntityComparisonCondition {
   public SharesDungeonPartyCondition(MythicLineConfig mlc) {
   }

   public boolean check(AbstractEntity caster, AbstractEntity target) {
      MythicPlayer casterPlayer = null;
      MythicPlayer targetPlayer = null;
      if (caster.isPlayer()) {
         casterPlayer = MythicDungeons.inst().getMythicPlayer((Player)caster.getBukkitEntity());
      } else {
         ActiveMob mob = MythicBukkit.inst().getAPIHelper().getMythicMobInstance(caster.getBukkitEntity());
         if (mob != null && mob.getParent() != null && ((AbstractEntity)mob.getParent().get()).isPlayer()) {
            casterPlayer = MythicDungeons.inst().getMythicPlayer((Player)((AbstractEntity)mob.getParent().get()).getBukkitEntity());
         }
      }

      if (target.isPlayer()) {
         targetPlayer = MythicDungeons.inst().getMythicPlayer((Player)target.getBukkitEntity());
      } else {
         ActiveMob mobx = MythicBukkit.inst().getAPIHelper().getMythicMobInstance(target.getBukkitEntity());
         if (mobx != null && mobx.getParent() != null && ((AbstractEntity)mobx.getParent().get()).isPlayer()) {
            targetPlayer = MythicDungeons.inst().getMythicPlayer((Player)((AbstractEntity)mobx.getParent().get()).getBukkitEntity());
         }
      }

      if (casterPlayer != null && targetPlayer != null) {
         IDungeonParty casterParty = casterPlayer.getDungeonParty();
         IDungeonParty targetParty = targetPlayer.getDungeonParty();
         return casterParty != null && targetParty != null ? casterParty == targetParty : false;
      } else {
         return false;
      }
   }
}
