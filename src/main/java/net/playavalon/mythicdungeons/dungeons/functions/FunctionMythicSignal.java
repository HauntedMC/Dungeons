package net.playavalon.mythicdungeons.dungeons.functions;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class FunctionMythicSignal extends DungeonFunction {
   @SavedField
   private String mythicSignal = "NONE";
   @SavedField
   private int radius = 15;
   @SavedField
   private String targetsString = "";
   private final List<String> targets = new ArrayList<>();

   public FunctionMythicSignal(Map<String, Object> config) {
      super("Mythic Signal", config);
      this.setTargetType(FunctionTargetType.NONE);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.META);
   }

   public FunctionMythicSignal() {
      super("Mythic Signal");
      this.setTargetType(FunctionTargetType.NONE);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.META);
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName("Signal: " + this.mythicSignal);
      this.targets.addAll(Arrays.asList(this.targetsString.split(",")));
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.REDSTONE_TORCH);
      functionButton.setDisplayName("&bMythic Signal");
      functionButton.addLore("&eBroadcasts a MythicMobs signal for");
      functionButton.addLore("&euse with '~onSignal' Mythic triggers.");
      return functionButton;
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      World world = this.location.getWorld();

      assert world != null;

      final ArmorStand caster = (ArmorStand)triggerEvent.getInstance()
         .getInstanceWorld()
         .spawnEntity(this.location.clone().add(0.0, 250.0, 0.0), EntityType.ARMOR_STAND);
      caster.setInvisible(true);
      caster.setMarker(true);
      caster.setAI(false);
      caster.teleport(this.location);

      for (Entity ent : world.getNearbyEntities(this.location, this.radius, this.radius, this.radius)) {
         ActiveMob aMob = MythicDungeons.inst().getMythicApi().getAPIHelper().getMythicMobInstance(ent);
         if (aMob != null && (this.targets.isEmpty() || this.targets.contains(aMob.getMobType()))) {
            aMob.signalMob(BukkitAdapter.adapt(caster), this.mythicSignal);
         }
      }

      (new BukkitRunnable() {
         public void run() {
            caster.remove();
         }
      }).runTaskLater(MythicDungeons.inst(), 1L);
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_LAMP);
            this.button.setDisplayName("&d&lSignal Name");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat signal should be sent?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent signal is: &6" + FunctionMythicSignal.this.mythicSignal));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionMythicSignal.this.mythicSignal = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet signal to '&6" + message + "&a'"));
            FunctionMythicSignal.this.setDisplayName("Signal: " + FunctionMythicSignal.this.mythicSignal);
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DETECTOR_RAIL);
            this.button.setDisplayName("&d&lRange");
            this.button.setAmount(FunctionMythicSignal.this.radius);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat's the range in blocks of the mobs we are sending the signal to?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionMythicSignal.this.radius = value.orElse(FunctionMythicSignal.this.radius);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet signal radius to '&6" + FunctionMythicSignal.this.radius + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CREEPER_HEAD);
            this.button.setDisplayName("&d&lMob Filter");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat mobs should receive the signal? (Separate multiple mob names by &6,&e)"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent filter is: &6" + FunctionMythicSignal.this.targetsString));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionMythicSignal.this.targetsString = message.replace(" ", "");
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet mob filter to '&6" + FunctionMythicSignal.this.targetsString + "&a'"));
            FunctionMythicSignal.this.targets.addAll(Arrays.asList(FunctionMythicSignal.this.targetsString.split(",")));
         }
      });
   }

   public void setMythicSignal(String mythicSignal) {
      this.mythicSignal = mythicSignal;
   }
}
