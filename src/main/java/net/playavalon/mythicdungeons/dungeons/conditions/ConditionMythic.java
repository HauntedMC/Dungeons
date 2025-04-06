package net.playavalon.mythicdungeons.dungeons.conditions;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.mobs.GenericCaster;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillTrigger;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.skills.SkillCondition;
import io.lumine.mythic.core.skills.SkillMetadataImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ConditionMythic extends TriggerCondition {
   @SavedField
   private String mythicLine = "example{value=1}";
   @SavedField
   private Location targetLocation;
   @SavedField
   private boolean targetPlayer = true;
   private Location instanceLoc;

   public ConditionMythic(Map<String, Object> config) {
      super("mythiccondition", config);
   }

   public ConditionMythic() {
      super("mythiccondition");
   }

   @Override
   public void onEnable() {
      if (this.targetLocation == null) {
         this.instanceLoc = this.location.clone();
      } else {
         this.instanceLoc = this.targetLocation.clone();
         this.instanceLoc.setWorld(this.location.getWorld());
      }
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      SkillCondition condition = MythicBukkit.inst().getSkillManager().getCondition(this.mythicLine);
      final ArmorStand stand = (ArmorStand)event.getInstance()
         .getInstanceWorld()
         .spawnEntity(this.instanceLoc.clone().add(0.0, 250.0, 0.0), EntityType.ARMOR_STAND);
      stand.setInvisible(true);
      stand.setMarker(true);
      stand.setAI(false);
      stand.teleport(this.instanceLoc);
      (new BukkitRunnable() {
         public void run() {
            stand.remove();
         }
      }).runTaskLater(MythicDungeons.inst(), 1L);
      List<AbstractEntity> abstractTargets = new ArrayList<>();
      Entity target = event.getPlayer();
      if (target == null) {
         target = stand;
      }

      abstractTargets.add(BukkitAdapter.adapt(target));
      SkillCaster caster = new GenericCaster(BukkitAdapter.adapt(stand));
      SkillMetadata skillMeta = new SkillMetadataImpl(
         SkillTrigger.get("DEFAULT"), caster, BukkitAdapter.adapt(target), BukkitAdapter.adapt(this.instanceLoc), abstractTargets, null, 1.0F
      );
      return condition.evaluateTargets(skillMeta);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton conditionButton = new MenuButton(Material.NETHER_STAR);
      conditionButton.setDisplayName("&bMythic Condition");
      conditionButton.addLore("&eChecks if a configured Mythic");
      conditionButton.addLore("&eMobs condition is met.");
      return conditionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NETHER_STAR);
            this.button.setDisplayName("&d&lMythic Condition Data");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eEnter the mythic condition and its values."));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent data: &6" + ConditionMythic.this.mythicLine));
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionMythic.this.mythicLine = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet mythic condition data to '&6" + message + "&a'"));
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_LAMP);
            this.button.setDisplayName("&d&lCondition Targets Player");
            this.button.setEnchanted(ConditionMythic.this.targetPlayer);
         }

         @Override
         public void onSelect(Player player) {
            if (!ConditionMythic.this.targetPlayer) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&bEnabled &acondition compares to the triggering player.'"));
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&bNote: This option only works on 'player' triggers!"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&cDisabled &acondition compares to the triggering player."));
            }

            ConditionMythic.this.targetPlayer = !ConditionMythic.this.targetPlayer;
         }
      });
   }
}
