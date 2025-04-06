package net.playavalon.mythicdungeons.mythic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.skills.SkillExecutor;
import io.lumine.mythic.core.skills.targeters.IEntitySelector;
import io.lumine.mythic.core.utils.annotations.MythicTargeter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@MythicTargeter(
   author = "MarcatoSound",
   name = "entitiesinroom",
   aliases = {"room"},
   description = "Targets all entities in the dungeon room of the caster."
)
public class EntitiesInRoomTargeter extends IEntitySelector {
   public EntitiesInRoomTargeter(SkillExecutor manager, MythicLineConfig mlc) {
      super(manager, mlc);
   }

   public Collection<AbstractEntity> getEntities(SkillMetadata data) {
      Set<AbstractEntity> targets = new HashSet<>();
      SkillCaster caster = data.getCaster();
      AbstractEntity aEnt = caster.getEntity();
      Entity ent = aEnt.getBukkitEntity();
      World world = ent.getWorld();
      if (world.getPlayers().size() <= 0) {
         return targets;
      } else {
         Player player = (Player)world.getPlayers().get(0);
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         AbstractInstance abs = aPlayer.getInstance();
         if (abs == null) {
            return targets;
         } else {
            InstanceProcedural instance = abs.as(InstanceProcedural.class);
            if (instance == null) {
               return targets;
            } else {
               InstanceRoom room = instance.getRoom(ent.getLocation());
               if (room == null) {
                  return targets;
               } else {
                  CountDownLatch latch = new CountDownLatch(1);
                  Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> {
                     for (Entity target : world.getNearbyEntities(room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))) {
                        if (Bukkit.getWorld(world.getName()) == null) {
                           break;
                        }

                        targets.add(BukkitAdapter.adapt(target));
                     }

                     latch.countDown();
                  });

                  try {
                     latch.await(200L, TimeUnit.MILLISECONDS);
                  } catch (InterruptedException var14) {
                  }

                  return targets;
               }
            }
         }
      }
   }
}
