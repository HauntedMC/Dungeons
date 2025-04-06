package net.playavalon.mythicdungeons.dungeons.functions.variables;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.dungeons.variables.VariableEditMode;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

@DeclaredFunction
public class FunctionInstanceVariable extends DungeonFunction {
   @SavedField
   private String varName;
   @SavedField
   private String varValue;
   @SavedField
   private VariableEditMode mode = VariableEditMode.SET;
   private int modeIndex;

   public FunctionInstanceVariable(Map<String, Object> config) {
      super("Instance Variable", config);
      this.setTargetType(FunctionTargetType.NONE);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.META);
   }

   public FunctionInstanceVariable() {
      super("Instance Variable");
      this.setTargetType(FunctionTargetType.NONE);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.META);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      Bukkit.getScheduler()
         .runTaskLater(
            MythicDungeons.inst(),
            () -> {
               InstancePlayable play = this.instance.asPlayInstance();
               if (play != null) {
                  double change = 0.0;
                  switch (this.mode) {
                     case SET:
                        play.getInstanceVariables().set(this.varName, this.varValue);
                        break;
                     case ADD:
                        try {
                           change = Double.parseDouble(this.varValue);
                           play.getInstanceVariables().add(this.varName, change);
                        } catch (NumberFormatException var6) {
                           MythicDungeons.inst()
                              .getLogger()
                              .warning("WARNING :: Addition to " + this.varName + " variable failed: " + this.varValue + " is not a valid number!");
                           MythicDungeons.inst().getLogger().warning("-- Found in dungeon: " + this.instance.getDungeon().getWorldName());
                        }
                        break;
                     case SUBTRACT:
                        try {
                           change = Double.parseDouble(this.varValue);
                           play.getInstanceVariables().subtract(this.varName, change);
                        } catch (NumberFormatException var5) {
                           MythicDungeons.inst()
                              .getLogger()
                              .warning("WARNING :: Subtraction to " + this.varName + " variable failed: " + this.varValue + " is not a valid number!");
                           MythicDungeons.inst().getLogger().warning("-- Found in dungeon: " + this.instance.getDungeon().getWorldName());
                        }
                  }
               }
            },
            1L
         );
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.OAK_SIGN);
      functionButton.setDisplayName("&aDungeon Variable");
      functionButton.addLore("&eStores and manages instance-wide");
      functionButton.addLore("&evariables. Used in combination with");
      functionButton.addLore("&ethe dungeon variable trigger condition.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PAPER);
            this.button.setDisplayName("&d&lEdit Name");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat is the variable called?"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent name: &6" + FunctionInstanceVariable.this.varName));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionInstanceVariable.this.varName = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet variable name to '&6" + FunctionInstanceVariable.this.varName + "&a'"));
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.OBSERVER);
            this.button.setDisplayName("&d&lEdit Mode: " + FunctionInstanceVariable.this.mode);
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            FunctionInstanceVariable.this.modeIndex++;
            FunctionInstanceVariable.this.verifyModeType();
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched mode to '&6" + FunctionInstanceVariable.this.mode.toString() + "&a'"));
            aPlayer.setHotbar(this.menu);
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PAPER);
            this.button.setDisplayName("&d&lValue");
         }

         @Override
         public void onSelect(Player player) {
            switch (FunctionInstanceVariable.this.mode) {
               case SET:
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat should the variable be set to?"));
                  break;
               case ADD:
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow much should we add to the variable?"));
                  break;
               case SUBTRACT:
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow much should we subtract from the variable?"));
            }

            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent value: &6" + FunctionInstanceVariable.this.varValue));
         }

         @Override
         public void onInput(Player player, String message) {
            if (FunctionInstanceVariable.this.mode != VariableEditMode.SET) {
               Optional<Double> value = StringUtils.readDoubleInput(player, message);
               FunctionInstanceVariable.this.varValue = value.isPresent() ? message : FunctionInstanceVariable.this.varValue;
               if (value.isPresent()) {
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet value to '&6" + FunctionInstanceVariable.this.varValue + "&a'"));
               }
            } else {
               FunctionInstanceVariable.this.varValue = message;
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet value to '&6" + FunctionInstanceVariable.this.varValue + "&a'"));
            }
         }
      });
   }

   private void verifyModeType() {
      if (this.modeIndex >= VariableEditMode.values().length) {
         this.modeIndex = 0;
      }

      this.mode = VariableEditMode.intToModeType(this.modeIndex);
   }
}
