package net.playavalon.mythicdungeons.dungeons.conditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredCondition;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@DeclaredCondition
public class ConditionPlayersWithin extends TriggerCondition {
   @SavedField
   private double radius = 3.0;
   @SavedField
   private int count = 1;
   @SavedField
   private boolean playersExact = false;

   public ConditionPlayersWithin(Map<String, Object> config) {
      super("playerswithin", config);
   }

   public ConditionPlayersWithin() {
      super("playerswithin");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      Collection<Entity> entities = this.location.getWorld().getNearbyEntities(this.location, this.radius, this.radius, this.radius);
      List<Player> players = new ArrayList<>();

      for (Entity ent : entities) {
         if (ent instanceof Player player && player.getGameMode() != GameMode.SPECTATOR) {
            players.add(player);
         }
      }

      return this.playersExact ? players.size() == this.count : players.size() >= this.count;
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.DETECTOR_RAIL);
      functionButton.setDisplayName("&dPlayers Within");
      functionButton.addLore("&eChecks for a number of players");
      functionButton.addLore("&ewithin a configured radius from");
      functionButton.addLore("&ethis function.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DETECTOR_RAIL);
            this.button.setDisplayName("&d&lDistance");
            this.button.setAmount((int)MathUtils.round(ConditionPlayersWithin.this.radius, 0));
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat is the range of the trigger in blocks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            ConditionPlayersWithin.this.radius = value.orElse(ConditionPlayersWithin.this.radius);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet condition radius to '&6" + ConditionPlayersWithin.this.radius + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PLAYER_HEAD);
            this.button.setDisplayName(ConditionPlayersWithin.this.playersExact ? "&d&lPlayers Required" : "&d&lMinimum Players Required");
            this.button.setAmount(ConditionPlayersWithin.this.count);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many are required?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            ConditionPlayersWithin.this.count = value.orElse(ConditionPlayersWithin.this.count);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet required players to '&6" + ConditionPlayersWithin.this.count + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lRequired Players Exact");
            this.button.setEnchanted(ConditionPlayersWithin.this.playersExact);
         }

         @Override
         public void onSelect(Player player) {
            if (!ConditionPlayersWithin.this.playersExact) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Required players is exact&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Required players is a minimum&a'"));
            }

            ConditionPlayersWithin.this.playersExact = !ConditionPlayersWithin.this.playersExact;
         }
      });
   }

   public double getRadius() {
      return this.radius;
   }

   public void setRadius(double radius) {
      this.radius = radius;
   }

   public int getCount() {
      return this.count;
   }

   public void setCount(int count) {
      this.count = count;
   }
}
