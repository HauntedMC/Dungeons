package net.playavalon.mythicdungeons.dungeons.functions;

import io.lumine.mythic.api.skills.Skill;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class FunctionMythicSkill extends DungeonFunction {
   @SavedField
   private String mythicSkill = "NONE";

   public FunctionMythicSkill(Map<String, Object> config) {
      super("Mythic Skill", config);
      this.setTargetType(FunctionTargetType.NONE);
      this.setCategory(FunctionCategory.META);
   }

   public FunctionMythicSkill() {
      super("Mythic Skill");
      this.setTargetType(FunctionTargetType.NONE);
      this.setCategory(FunctionCategory.META);
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName("Skill: " + this.mythicSkill);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.NETHER_STAR);
      functionButton.setDisplayName("&bMythic Skill");
      functionButton.addLore("&ePerforms a specified skill from");
      functionButton.addLore("&eMythic Mobs at this location.");
      return functionButton;
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      Optional<Skill> optSkill = MythicDungeons.inst().getMythicApi().getSkillManager().getSkill(this.mythicSkill);
      if (optSkill.isPresent()) {
         List<Entity> mythicTargets = new ArrayList<>();

         for (MythicPlayer aPlayer : targets) {
            mythicTargets.add(aPlayer.getPlayer());
         }

         final ArmorStand caster = (ArmorStand)triggerEvent.getInstance()
            .getInstanceWorld()
            .spawnEntity(this.location.clone().add(0.0, 250.0, 0.0), EntityType.ARMOR_STAND);
         caster.setInvisible(true);
         caster.setMarker(true);
         caster.setAI(false);
         caster.teleport(this.location);
         MythicDungeons.inst()
            .getMythicApi()
            .getAPIHelper()
            .castSkill(caster, this.mythicSkill, triggerEvent.getPlayer(), this.location, mythicTargets, null, 1.0F, null);
         (new BukkitRunnable() {
            public void run() {
               caster.remove();
            }
         }).runTaskLater(MythicDungeons.inst(), 1L);
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_LAMP);
            this.button.setDisplayName("&d&lSkill Name");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat skill should be performed?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent skill is: &6" + FunctionMythicSkill.this.mythicSkill));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Skill> optSkill = MythicDungeons.inst().getMythicApi().getSkillManager().getSkill(message);
            if (!optSkill.isPresent()) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cERROR :: No skill exists by that name!"));
            } else {
               FunctionMythicSkill.this.mythicSkill = message;
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet skill to '&6" + message + "&a'"));
               FunctionMythicSkill.this.setDisplayName("Skill: " + FunctionMythicSkill.this.mythicSkill);
            }
         }
      });
   }

   public void setMythicSkill(String mythicSkill) {
      this.mythicSkill = mythicSkill;
   }
}
