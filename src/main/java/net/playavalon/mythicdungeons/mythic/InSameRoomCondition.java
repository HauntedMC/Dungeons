package net.playavalon.mythicdungeons.mythic;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.conditions.ISkillMetaComparisonCondition;
import io.lumine.mythic.core.utils.annotations.MythicCondition;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
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
import org.bukkit.util.BoundingBox;

@MythicCondition(
   author = "MarcatoSound",
   name = "insameroom",
   aliases = {"isr", "sameroom"},
   description = "Checks if the target entity is in the same room as the caster."
)
public class InSameRoomCondition implements ISkillMetaComparisonCondition {
   public InSameRoomCondition(MythicLineConfig mlc) {
   }

   public boolean check(SkillMetadata meta, AbstractEntity absTarget) {
      SkillCaster skillCaster = meta.getCaster();
      Entity target = absTarget.getBukkitEntity();
      Entity caster = skillCaster.getEntity().getBukkitEntity();
      World world = caster.getWorld();
      if (world.getPlayers().size() <= 0) {
         return false;
      } else {
         Player player = (Player)world.getPlayers().get(0);
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         AbstractInstance abs = aPlayer.getInstance();
         if (abs == null) {
            return false;
         } else {
            InstanceProcedural instance = abs.as(InstanceProcedural.class);
            if (instance == null) {
               return false;
            } else {
               NamespacedKey key = new NamespacedKey(MythicDungeons.inst(), "originroom");
               PersistentDataContainer data = caster.getPersistentDataContainer();
               InstanceRoom room;
               if (data.has(key)) {
                  room = instance.getRoom(UUID.fromString((String)data.get(key, PersistentDataType.STRING)));
               } else {
                  room = instance.getRoom(caster.getLocation());
               }

               if (room == null) {
                  return false;
               } else {
                  BoundingBox area = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
                  return area.contains(target.getX(), target.getY(), target.getZ());
               }
            }
         }
      }
   }
}
