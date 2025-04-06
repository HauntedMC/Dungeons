package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.clip.placeholderapi.PlaceholderAPI;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (this.command != null && !this.command.isEmpty()) {
         List<MythicPlayer> players = new ArrayList<>();
         if (this.forEachPlayer) {
            players.addAll(targets);
         } else if (!targets.isEmpty()) {
            players.add(targets.get(0));
         } else if (triggerEvent.getPlayer() != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), PlaceholderAPI.setPlaceholders(triggerEvent.getPlayer(), this.command));
         } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), this.command);
         }

         for (MythicPlayer aPlayer : players) {
            Player player = aPlayer.getPlayer();
            switch (this.commandType) {
               case 0:
               default:
                  if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                     Bukkit.dispatchCommand(Bukkit.getConsoleSender(), PlaceholderAPI.setPlaceholders(player, this.command));
                  } else {
                     Bukkit.dispatchCommand(Bukkit.getConsoleSender(), this.command);
                  }
                  break;
               case 1:
                  if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                     Bukkit.dispatchCommand(player, PlaceholderAPI.setPlaceholders(player, this.command));
                  } else {
                     Bukkit.dispatchCommand(player, this.command);
                  }
                  break;
               case 2:
                  if (!Util.forceRunCommand(player, this.command)) {
                     MythicDungeons.inst().getLogger().info(MythicDungeons.debugPrefix + Util.colorize("&cCould not force-run command!"));
                  }
            }
         }
      } else {
         MythicDungeons.inst()
            .getLogger()
            .info(
               MythicDungeons.debugPrefix
                  + Util.colorize("&cCommand function in dungeon '" + this.instance.getDungeon().getWorldName() + "' has an empty command!")
            );
         MythicDungeons.inst()
            .getLogger()
            .info(
               MythicDungeons.debugPrefix
                  + Util.colorize("&c-- Position X:" + this.location.getBlockX() + ", Y:" + this.location.getBlockY() + ", Z:" + this.location.getBlockZ())
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
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eWithout the /, what command should be run by this function?"));
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eCurrent command: &6" + FunctionCommand.this.command));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionCommand.this.command = message;
            player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet command to: &6" + FunctionCommand.this.command));
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
                  if (MythicDungeons.inst().getConfig().getBoolean("General.CommandFunctionAsOp", false)) {
                     if (FunctionCommand.this.commandType > 2) {
                        FunctionCommand.this.commandType = 0;
                     }
                  } else if (FunctionCommand.this.commandType > 1) {
                     FunctionCommand.this.commandType = 0;
                  }

                  player.sendMessage(
                     MythicDungeons.debugPrefix
                        + Util.colorize(
                           "&aCommand will be run as '&6" + FunctionCommand.CommandType.intToCommandType(FunctionCommand.this.commandType) + "&a'!"
                        )
                  );
                  if (FunctionCommand.this.commandType == 2) {
                     player.sendMessage(
                        MythicDungeons.debugPrefix
                           + Util.colorize("&dNote: This will attempt to force the player to run the command, even if they don't have permission.")
                     );
                     player.sendMessage(
                        MythicDungeons.debugPrefix
                           + Util.colorize("&dIf the command doesn't work, consider changing '&eCommandFunctionAsOp&d' to '&6true&d' in the main config.yml")
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
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSwitched to '&6Run command once for each player&a'!"));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSwitched to '&6Run command once&a'!"));
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

   private static enum CommandType {
      CONSOLE,
      PLAYER,
      PLAYER_FORCED;

      public static FunctionCommand.CommandType intToCommandType(int index) {
         switch (index) {
            case 0:
            default:
               return CONSOLE;
            case 1:
               return PLAYER;
            case 2:
               return PLAYER_FORCED;
         }
      }
   }
}
