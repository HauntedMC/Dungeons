package net.playavalon.mythicdungeons.dungeons.functions.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredFunction
public class FunctionRepeater extends FunctionMulti {
   @SavedField
   private DungeonFunction function;
   @SavedField
   private int interval = 20;
   @SavedField
   private int delay = 0;
   @SavedField
   private int maxRepeats = 0;
   private int repeatCount = 0;
   private BukkitRunnable repeater;

   public FunctionRepeater(Map<String, Object> config) {
      super("Function Repeater", config);
      this.setAllowChangingTargetType(false);
   }

   public FunctionRepeater() {
      super("Function Repeater");
      this.setAllowChangingTargetType(false);
   }

   @Override
   public void init() {
      super.init();
      if (this.function != null) {
         this.function.setLocation(this.location);
         this.function.init();
      }
   }

   @Override
   public void onEnable() {
      if (this.function == null) {
         MythicDungeons.inst()
            .getLogger()
            .info(Util.colorize("&cWARNING :: A function repeater doesn't have a function in '" + this.instance.getDungeon().getWorldName() + "'!"));
         MythicDungeons.inst()
            .getLogger()
            .info(
               Util.colorize("&c--- Function is located at: " + this.location.getBlockX() + ", " + this.location.getBlockY() + ", " + this.location.getBlockZ())
            );
      } else {
         this.function.setInstance(this.instance);
         this.function.setLocation(this.location);
         this.function.enable(this.instance, this.location);
      }
   }

   @Override
   public void onDisable() {
      if (this.repeater != null && !this.repeater.isCancelled()) {
         this.repeater.cancel();
         this.repeater = null;
      }

      if (this.function != null) {
         this.function.disable();
      }
   }

   @Override
   public void addFunction(DungeonFunction function) {
      this.function = function;
   }

   @Override
   public void runFunction(final TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      final List<MythicPlayer> functionTargets = new ArrayList<>();
      switch (this.targetType) {
         case PLAYER:
            if (triggerEvent.getDPlayer() != null) {
               functionTargets.add(triggerEvent.getDPlayer());
            }
            break;
         case PARTY:
            functionTargets.addAll(this.instance.getPlayers());
      }

      this.repeater = new BukkitRunnable() {
         public void run() {
            if (FunctionRepeater.this.maxRepeats <= 0) {
               FunctionRepeater.this.function.runFunction(triggerEvent, functionTargets);
            } else {
               if (FunctionRepeater.this.repeatCount >= FunctionRepeater.this.maxRepeats) {
                  this.cancel();
                  if (FunctionRepeater.this.trigger != null && FunctionRepeater.this.trigger.isAllowRetrigger()) {
                     FunctionRepeater.this.repeatCount = 0;
                  }

                  return;
               }

               FunctionRepeater.this.function.runFunction(triggerEvent, functionTargets);
               FunctionRepeater.this.repeatCount++;
            }
         }
      };
      this.repeater.runTaskTimer(MythicDungeons.inst(), this.delay, this.interval);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.REPEATING_COMMAND_BLOCK);
      button.setDisplayName("&bFunction Repeater");
      button.addLore("&eRuns a configured function");
      button.addLore("&erepeatedly on an interval.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMMAND_BLOCK);
            this.button.setDisplayName("&a&lSet Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "functionmenu");
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
            this.button.setDisplayName("&e&lEdit Function");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
            if (FunctionRepeater.this.function != null) {
               aPlayer.setHotbar(FunctionRepeater.this.function.getMenu());
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cYou haven't set a function to repeat!"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lStart Delay");
            this.button.setAmount(FunctionRepeater.this.delay);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow long should we wait before starting the repeater in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRepeater.this.delay = value.orElse(FunctionRepeater.this.delay);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet delay to '&6" + FunctionRepeater.this.delay + "&a' ticks."));
            }
         }
      });
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.CLOCK);
                  this.button.setDisplayName("&d&lRepeater Interval");
                  this.button.setAmount(FunctionRepeater.this.interval);
               }

               @Override
               public void onSelect(Player player) {
                  player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow frequently in ticks should the function run?"));
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> value = StringUtils.readIntegerInput(player, message);
                  FunctionRepeater.this.interval = value.orElse(FunctionRepeater.this.interval);
                  if (value.isPresent()) {
                     player.sendMessage(
                        Util.colorize(MythicDungeons.debugPrefix + "&aSet interval to '&6" + FunctionRepeater.this.interval + "&a' ticks between each run.")
                     );
                  }
               }
            }
         );
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lMax Repeats");
            this.button.setAmount(FunctionRepeater.this.maxRepeats);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many times should the function run?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRepeater.this.maxRepeats = value.orElse(FunctionRepeater.this.maxRepeats);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet max repeats to '&6" + FunctionRepeater.this.maxRepeats + "&a'."));
            }
         }
      });
   }

   public FunctionRepeater clone() {
      FunctionRepeater clone = (FunctionRepeater)super.clone();
      if (this.function == null) {
         return clone;
      } else {
         clone.function = this.function.clone();
         clone.function.setLocation(clone.location);
         return clone;
      }
   }
}
