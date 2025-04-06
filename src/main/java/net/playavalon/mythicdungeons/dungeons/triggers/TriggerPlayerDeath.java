package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

@DeclaredTrigger
public class TriggerPlayerDeath extends DungeonTrigger {
   @SavedField
   private int deathsRequired = 1;
   @SavedField
   private boolean oneDeathPerPlayer = false;
   private final List<UUID> playerDeaths;

   public TriggerPlayerDeath(Map<String, Object> config) {
      super("Player Death", config);
      this.waitForConditions = true;
      this.playerDeaths = new ArrayList<>();
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   public TriggerPlayerDeath() {
      super("Player Death");
      this.waitForConditions = true;
      this.playerDeaths = new ArrayList<>();
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   @EventHandler
   public void onPlayerDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      World world = player.getWorld();
      if (world == this.instance.getInstanceWorld()) {
         if (this.playerDeaths.size() < this.deathsRequired) {
            if (!this.oneDeathPerPlayer || !this.playerDeaths.contains(player.getUniqueId())) {
               if (this.matchesRoom(player.getLocation())) {
                  this.playerDeaths.add(player.getUniqueId());
                  if (this.playerDeaths.size() >= this.deathsRequired) {
                     this.trigger(MythicDungeons.inst().getMythicPlayer(player));
                     if (this.allowRetrigger) {
                        this.playerDeaths.clear();
                     }
                  }
               }
            }
         }
      }
   }

   private boolean matchesRoom(Location origin) {
      InstanceProcedural inst = this.instance.as(InstanceProcedural.class);
      if (inst == null) {
         return true;
      } else if (!this.limitToRoom) {
         return true;
      } else {
         InstanceRoom originRoom = inst.getRoom(origin);
         InstanceRoom thisRoom = inst.getRoom(this.location);
         return thisRoom == originRoom;
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.PLAYER_HEAD);
      functionButton.setDisplayName("&aPlayer Death Counter");
      functionButton.addLore("&eTriggered when a certain number of");
      functionButton.addLore("&eplayers have died.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BONE);
            this.button.setDisplayName("&d&lAmount");
            this.button.setAmount(TriggerPlayerDeath.this.deathsRequired);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many players must die?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            TriggerPlayerDeath.this.deathsRequired = value.orElse(TriggerPlayerDeath.this.deathsRequired);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet amount to '&6" + TriggerPlayerDeath.this.deathsRequired + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE);
            this.button.setDisplayName("&d&lToggle Deaths Per Player");
            this.button.setEnchanted(TriggerPlayerDeath.this.oneDeathPerPlayer);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerPlayerDeath.this.oneDeathPerPlayer) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Count One Death Per Player&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Count All Player Deaths&a'"));
            }

            TriggerPlayerDeath.this.oneDeathPerPlayer = !TriggerPlayerDeath.this.oneDeathPerPlayer;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerPlayerDeath.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerPlayerDeath.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerPlayerDeath.this.allowRetrigger = !TriggerPlayerDeath.this.allowRetrigger;
         }
      });
      this.addRoomLimitToggleButton();
   }
}
