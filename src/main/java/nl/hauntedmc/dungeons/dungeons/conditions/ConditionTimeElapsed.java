package nl.hauntedmc.dungeons.dungeons.conditions;

import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredCondition;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat's the minimum dungeon time for this condition (in seconds)?"));
                  } else {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat's the max dungeon time for this condition (in seconds)?"));
                  }

                  player.sendMessage(
                     HelperUtils.colorize(Dungeons.logPrefix + "&bCurrent time: " + StringUtils.formatDuration(ConditionTimeElapsed.this.time * 1000L))
                  );
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> count = StringUtils.readIntegerInput(player, message);
                  ConditionTimeElapsed.this.time = count.orElse(ConditionTimeElapsed.this.time);
                  if (count.isPresent()) {
                     if (!ConditionTimeElapsed.this.inverted) {
                        player.sendMessage(
                           HelperUtils.colorize(
                              Dungeons.logPrefix
                                 + "&aSet required dungeon run time to '&bAT LEAST &6"
                                 + StringUtils.formatDuration(ConditionTimeElapsed.this.time * 1000L)
                                 + "&a'"
                           )
                        );
                     } else {
                        player.sendMessage(
                           HelperUtils.colorize(
                              Dungeons.logPrefix
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
