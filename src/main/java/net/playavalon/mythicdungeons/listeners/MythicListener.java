package net.playavalon.mythicdungeons.listeners;

import io.lumine.mythic.api.skills.ISkillMechanic;
import io.lumine.mythic.api.skills.conditions.ISkillCondition;
import io.lumine.mythic.api.skills.targeters.ISkillTargeter;
import io.lumine.mythic.bukkit.events.MythicConditionLoadEvent;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.bukkit.events.MythicTargeterLoadEvent;
import net.playavalon.mythicdungeons.mythic.DungeonDifficultyCondition;
import net.playavalon.mythicdungeons.mythic.DungeonSignalMechanic;
import net.playavalon.mythicdungeons.mythic.EntitiesInRoomTargeter;
import net.playavalon.mythicdungeons.mythic.HasDungeonCooldownCondition;
import net.playavalon.mythicdungeons.mythic.HasFinishedDungeonCondition;
import net.playavalon.mythicdungeons.mythic.InSameRoomCondition;
import net.playavalon.mythicdungeons.mythic.RoomDoorMechanic;
import net.playavalon.mythicdungeons.mythic.SharesDungeonPartyCondition;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MythicListener implements Listener {
   @EventHandler
   public void onMechanicLoad(MythicMechanicLoadEvent e) {
      if (e.getMechanicName().equalsIgnoreCase("dungeonsignal") || e.getMechanicName().equalsIgnoreCase("dsignal")) {
         ISkillMechanic mechanic = new DungeonSignalMechanic(e.getConfig());
         e.register(mechanic);
      }

      if (e.getMechanicName().equalsIgnoreCase("roomdoor") || e.getMechanicName().equalsIgnoreCase("rdoor") || e.getMechanicName().equalsIgnoreCase("ddoor")) {
         ISkillMechanic mechanic = new RoomDoorMechanic(e.getConfig());
         e.register(mechanic);
      }
   }

   @EventHandler
   public void onConditionLoad(MythicConditionLoadEvent e) {
      if (e.getConditionName().equalsIgnoreCase("sharesparty") || e.getConditionName().equalsIgnoreCase("dungeonparty")) {
         ISkillCondition condition = new SharesDungeonPartyCondition(e.getConfig());
         e.register(condition);
      }

      if (e.getConditionName().equalsIgnoreCase("dungeonfinished") || e.getConditionName().equalsIgnoreCase("finisheddungeon")) {
         ISkillCondition condition = new HasFinishedDungeonCondition(e.getConfig());
         e.register(condition);
      }

      if (e.getConditionName().equalsIgnoreCase("dungeononcooldown") || e.getConditionName().equalsIgnoreCase("hasdungeoncooldown")) {
         ISkillCondition condition = new HasDungeonCooldownCondition(e.getConfig());
         e.register(condition);
      }

      if (e.getConditionName().equalsIgnoreCase("dungeondifficulty") || e.getConditionName().equalsIgnoreCase("dungeondifficultylevel")) {
         ISkillCondition condition = new DungeonDifficultyCondition(e.getConfig());
         e.register(condition);
      }

      if (e.getConditionName().equalsIgnoreCase("insameroom")
         || e.getConditionName().equalsIgnoreCase("isr")
         || e.getConditionName().equalsIgnoreCase("sameroom")) {
         ISkillCondition condition = new InSameRoomCondition(e.getConfig());
         e.register(condition);
      }
   }

   @EventHandler
   public void onTargetterLoad(MythicTargeterLoadEvent e) {
      if (e.getTargeterName().equalsIgnoreCase("room") || e.getTargeterName().equalsIgnoreCase("entitiesinroom")) {
         ISkillTargeter targetter = new EntitiesInRoomTargeter(e.getContainer().getManager(), e.getConfig());
         e.register(targetter);
      }
   }
}
