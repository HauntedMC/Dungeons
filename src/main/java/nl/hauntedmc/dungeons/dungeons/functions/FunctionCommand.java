package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

@DeclaredFunction
public class FunctionCommand extends DungeonFunction {
   @SavedField
   private String command = "";
   @SavedField
   private int commandType = 0;
   @SavedField
   private boolean forEachPlayer = false;

   public FunctionCommand(Map<String, Object> config) {
      super("Command", config);
      this.setCategory(FunctionCategory.META);
   }

   public FunctionCommand() {
      super("Command");
      this.setCategory(FunctionCategory.META);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (this.command != null && !this.command.isEmpty()) {
         List<DungeonPlayer> players = new ArrayList<>();
         if (this.forEachPlayer) {
            players.addAll(targets);
         } else if (!targets.isEmpty()) {
            players.add(targets.getFirst());
         } else if (triggerEvent.getPlayer() != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), PlaceholderAPI.setPlaceholders(triggerEvent.getPlayer(), this.command));
         } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), this.command);
         }

         for (DungeonPlayer aPlayer : players) {
            Player player = aPlayer.getPlayer();
            switch (this.commandType) {
               case 1:
                  if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                     Bukkit.dispatchCommand(player, PlaceholderAPI.setPlaceholders(player, this.command));
                  } else {
                     Bukkit.dispatchCommand(player, this.command);
                  }
                  break;
               case 2:
                  if (!HelperUtils.forceRunCommand(player, this.command)) {
                     Dungeons.inst().getLogger().info(Dungeons.logPrefix + HelperUtils.colorize("&cCould not force-run command!"));
                  }
               case 0:
               default:
                  if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                     Bukkit.dispatchCommand(Bukkit.getConsoleSender(), PlaceholderAPI.setPlaceholders(player, this.command));
                  } else {
                     Bukkit.dispatchCommand(Bukkit.getConsoleSender(), this.command);
                  }
                  break;
            }
         }
      } else {
         Dungeons.inst()
            .getLogger()
            .info(
               Dungeons.logPrefix
                  + HelperUtils.colorize("&cCommand function in dungeon '" + this.instance.getDungeon().getWorldName() + "' has an empty command!")
            );
         Dungeons.inst()
            .getLogger()
            .info(
               Dungeons.logPrefix
                  + HelperUtils.colorize("&c-- Position X:" + this.location.getBlockX() + ", Y:" + this.location.getBlockY() + ", Z:" + this.location.getBlockZ())
            );
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.COMMAND_BLOCK);
      button.setDisplayName("&bCommand Sender");
      button.addLore("&eRuns a command as the player(s),");
      button.addLore("&eOP'd player(s), or console.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMMAND_BLOCK);
            this.button.setDisplayName("&d&lSet Command");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eWithout the /, what command should be run by this function?"));
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent command: &6" + FunctionCommand.this.command));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionCommand.this.command = message;
            player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet command to: &6" + FunctionCommand.this.command));
         }
      });
      this.menu
         .addMenuItem(
            new MenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.REPEATING_COMMAND_BLOCK);
                  this.button.setDisplayName("&d&lCommand Sender: " + FunctionCommand.CommandType.intToCommandType(FunctionCommand.this.commandType));
               }

               @Override
               public void onSelect(PlayerEvent event) {
                  Player player = event.getPlayer();
                  FunctionCommand.this.commandType++;
                  if (Dungeons.inst().getConfig().getBoolean("General.CommandFunctionAsOp", false)) {
                     if (FunctionCommand.this.commandType > 2) {
                        FunctionCommand.this.commandType = 0;
                     }
                  } else if (FunctionCommand.this.commandType > 1) {
                     FunctionCommand.this.commandType = 0;
                  }

                  player.sendMessage(
                     Dungeons.logPrefix
                        + HelperUtils.colorize(
                           "&aCommand will be run as '&6" + FunctionCommand.CommandType.intToCommandType(FunctionCommand.this.commandType) + "&a'!"
                        )
                  );
                  if (FunctionCommand.this.commandType == 2) {
                     player.sendMessage(
                        Dungeons.logPrefix
                           + HelperUtils.colorize("&dNote: This will attempt to force the player to run the command, even if they don't have permission.")
                     );
                     player.sendMessage(
                        Dungeons.logPrefix
                           + HelperUtils.colorize("&dIf the command doesn't work, consider changing '&eCommandFunctionAsOp&d' to '&6true&d' in the main config.yml")
                     );
                  }
               }
            }
         );
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PLAYER_HEAD);
            this.button.setDisplayName("&d&lToggle 'For Each Player'");
            this.button.setEnchanted(FunctionCommand.this.forEachPlayer);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionCommand.this.forEachPlayer) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSwitched to '&6Run command once for each player&a'!"));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSwitched to '&6Run command once&a'!"));
            }

            FunctionCommand.this.forEachPlayer = !FunctionCommand.this.forEachPlayer;
         }
      });
   }

   public void setCommand(String command) {
      this.command = command;
   }

   public void setCommandType(int commandType) {
      this.commandType = commandType;
   }

   public void setForEachPlayer(boolean forEachPlayer) {
      this.forEachPlayer = forEachPlayer;
   }

   private enum CommandType {
      CONSOLE,
      PLAYER,
      PLAYER_FORCED;

      public static FunctionCommand.CommandType intToCommandType(int index) {
          return switch (index) {
              case 1 -> PLAYER;
              case 2 -> PLAYER_FORCED;
              default -> CONSOLE;
          };
      }
   }
}
