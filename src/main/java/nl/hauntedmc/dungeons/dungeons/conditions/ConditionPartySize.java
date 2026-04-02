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
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

@DeclaredCondition
public class ConditionPartySize extends TriggerCondition {
   @SavedField
   private int playersNeeded = 1;
   @SavedField
   private boolean playersExact = false;
   @SavedField
   private boolean useStartingPlayers = false;

   public ConditionPartySize(Map<String, Object> config) {
      super("partysize", config);
   }

   public ConditionPartySize() {
      super("partysize");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      InstancePlayable instance = this.getInstance().asPlayInstance();
      if (instance == null) {
         return false;
      } else {
         int playerCount = this.useStartingPlayers ? instance.getParticipants() : instance.getLivingPlayers().size();
         return this.playersExact ? playerCount == this.playersNeeded : playerCount >= this.playersNeeded;
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.PLAYER_HEAD);
      functionButton.setDisplayName("&6Player Count");
      functionButton.addLore("&eChecks if the specified number");
      functionButton.addLore("&eof players are in the instance,");
      functionButton.addLore("&ewith support for a minimum.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PLAYER_HEAD);
            this.button.setDisplayName(ConditionPartySize.this.playersExact ? "&d&lPlayers Required" : "&d&lMinimum Players Required");
            this.button.setAmount(ConditionPartySize.this.playersNeeded);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many are required?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> count = StringUtils.readIntegerInput(player, message);
            ConditionPartySize.this.playersNeeded = count.orElse(ConditionPartySize.this.playersNeeded);
            if (count.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet required players to '&6" + ConditionPartySize.this.playersNeeded + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lRequired Players Exact");
            this.button.setEnchanted(ConditionPartySize.this.playersExact);
         }

         @Override
         public void onSelect(Player player) {
            if (!ConditionPartySize.this.playersExact) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Required player is exact&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Required players is a minimum&a'"));
            }

            ConditionPartySize.this.playersExact = !ConditionPartySize.this.playersExact;
         }
      });
      this.menu
         .addMenuItem(
            new ToggleMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.MOSSY_COBBLESTONE);
                  this.button.setDisplayName("&d&lBased On Starting Players");
                  this.button.setEnchanted(ConditionPartySize.this.useStartingPlayers);
               }

               @Override
               public void onSelect(Player player) {
                  if (!ConditionPartySize.this.useStartingPlayers) {
                     player.sendMessage(
                        HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Required players check based on the start of the dungeon&a'")
                     );
                  } else {
                     player.sendMessage(
                        HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Required players check based on the current number of players&a'")
                     );
                  }

                  ConditionPartySize.this.useStartingPlayers = !ConditionPartySize.this.useStartingPlayers;
               }
            }
         );
   }
}
