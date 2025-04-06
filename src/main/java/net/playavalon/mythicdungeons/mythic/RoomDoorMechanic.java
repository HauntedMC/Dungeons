package net.playavalon.mythicdungeons.mythic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.INoTargetSkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.generation.rooms.DoorAction;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

@MythicMechanic(
   name = "roomdoor",
   aliases = {"rdoor", "ddoor"},
   author = "MarcatoSound"
)
public class RoomDoorMechanic implements INoTargetSkill {
   private String doorName;
   private DoorAction action;
   private boolean keepEntranceOpen;

   public RoomDoorMechanic(MythicLineConfig mlc) {
      this.doorName = mlc.getString(new String[]{"door", "d", "name", "n"}, "", new String[0]);
      this.action = DoorAction.valueOf(mlc.getString(new String[]{"action", "a"}, "TOGGLE", new String[0]));
      this.keepEntranceOpen = mlc.getBoolean(new String[]{"keepEntranceOpen", "e"}, true);
   }

   public ThreadSafetyLevel getThreadSafetyLevel() {
      return ThreadSafetyLevel.SYNC_ONLY;
   }

   public SkillResult cast(SkillMetadata skillMetadata) {
      AbstractEntity caster = skillMetadata.getCaster().getEntity();
      World world = BukkitAdapter.adapt(caster.getWorld());
      if (world.getPlayers().size() <= 0) {
         return SkillResult.CONDITION_FAILED;
      } else {
         Player player = (Player)world.getPlayers().get(0);
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         AbstractInstance inst = aPlayer.getInstance();
         if (inst == null) {
            return SkillResult.CONDITION_FAILED;
         } else {
            InstanceProcedural proc = inst.as(InstanceProcedural.class);
            if (proc == null) {
               return SkillResult.CONDITION_FAILED;
            } else {
               Entity ent = caster.getBukkitEntity();
               PersistentDataContainer data = ent.getPersistentDataContainer();
               NamespacedKey key = new NamespacedKey(MythicDungeons.inst(), "originroom");
               InstanceRoom room;
               if (data.has(key)) {
                  room = proc.getRoom(UUID.fromString((String)data.get(key, PersistentDataType.STRING)));
               } else {
                  room = proc.getRoom(ent.getLocation());
               }

               if (room == null) {
                  return SkillResult.CONDITION_FAILED;
               } else {
                  switch (this.action) {
                     case TOGGLE:
                        if (this.doorName.equals("all")) {
                           room.toggleValidDoors(this.keepEntranceOpen);
                        } else {
                           room.toggleDoor(this.doorName);
                        }
                        break;
                     case OPEN:
                        if (this.doorName.equals("all")) {
                           room.openValidDoors(this.keepEntranceOpen);
                        } else {
                           room.openDoor(this.doorName);
                        }
                        break;
                     case CLOSE:
                        if (this.doorName.equals("all")) {
                           room.closeValidDoors(this.keepEntranceOpen);
                        } else {
                           room.closeDoor(this.doorName);
                        }
                  }

                  return SkillResult.SUCCESS;
               }
            }
         }
      }
   }
}
