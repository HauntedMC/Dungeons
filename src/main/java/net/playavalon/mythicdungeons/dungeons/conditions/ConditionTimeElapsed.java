package net.playavalon.mythicdungeons.dungeons.conditions;

import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredCondition;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredCondition
public class ConditionTimeElapsed extends TriggerCondition {
   @SavedField
   private int time;

   public ConditionTimeElapsed(Map<String, Object> config) {
      super("Time Elapsed", config);
   }

   public ConditionTimeElapsed() {
      super("Time Elapsed");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance == null) {
         return false;
      } else {
         int timeElapsed = instance.getTimeElapsed();
         return timeElapsed >= this.time;
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.CLOCK);
      functionButton.setDisplayName("&6Time Elapsed");
      functionButton.addLore("&eChecks if the dungeon has been");
      functionButton.addLore("&eactive for at least a,");
      functionButton.addLore("&ea specified duration.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.CLOCK);
                  this.button.setDisplayName(ConditionTimeElapsed.this.inverted ? "&d&lMinimum Time" : "&d&lMaximum Time");
               }

               @Override
               public void onSelect(Player player) {
                  if (!ConditionTimeElapsed.this.inverted) {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat's the minimum dungeon time for this condition (in seconds)?"));
                  } else {
                     player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat's the max dungeon time for this condition (in seconds)?"));
                  }

                  player.sendMessage(
                     Util.colorize(MythicDungeons.debugPrefix + "&bCurrent time: " + StringUtils.formatDuration(ConditionTimeElapsed.this.time * 1000L))
                  );
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> count = StringUtils.readIntegerInput(player, message);
                  ConditionTimeElapsed.this.time = count.orElse(ConditionTimeElapsed.this.time);
                  if (count.isPresent()) {
                     if (!ConditionTimeElapsed.this.inverted) {
                        player.sendMessage(
                           Util.colorize(
                              MythicDungeons.debugPrefix
                                 + "&aSet required dungeon run time to '&bAT LEAST &6"
                                 + StringUtils.formatDuration(ConditionTimeElapsed.this.time * 1000L)
                                 + "&a'"
                           )
                        );
                     } else {
                        player.sendMessage(
                           Util.colorize(
                              MythicDungeons.debugPrefix
                                 + "&aSet required dungeon run time to '&bLESS THAN &6"
                                 + StringUtils.formatDuration(ConditionTimeElapsed.this.time * 1000L)
                                 + "&a'"
                           )
                        );
                     }
                  }
               }
            }
         );
   }
}
