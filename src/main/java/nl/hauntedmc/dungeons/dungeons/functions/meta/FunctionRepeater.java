package nl.hauntedmc.dungeons.dungeons.functions.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cWARNING :: A function repeater doesn't have a function in '" + this.instance.getDungeon().getWorldName() + "'!"));
         Dungeons.inst()
            .getLogger()
            .info(
               HelperUtils.colorize("&c--- Function is located at: " + this.location.getBlockX() + ", " + this.location.getBlockY() + ", " + this.location.getBlockZ())
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
   public void runFunction(final TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      final List<DungeonPlayer> functionTargets = new ArrayList<>();
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
      this.repeater.runTaskTimer(Dungeons.inst(), this.delay, this.interval);
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
            Dungeons.inst().getGuiApi().openGUI(player, "functionmenu");
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
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
            if (FunctionRepeater.this.function != null) {
               aPlayer.setHotbar(FunctionRepeater.this.function.getMenu());
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou haven't set a function to repeat!"));
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow long should we wait before starting the repeater in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRepeater.this.delay = value.orElse(FunctionRepeater.this.delay);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet delay to '&6" + FunctionRepeater.this.delay + "&a' ticks."));
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
                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow frequently in ticks should the function run?"));
               }

               @Override
               public void onInput(Player player, String message) {
                  Optional<Integer> value = StringUtils.readIntegerInput(player, message);
                  FunctionRepeater.this.interval = value.orElse(FunctionRepeater.this.interval);
                  if (value.isPresent()) {
                     player.sendMessage(
                        HelperUtils.colorize(Dungeons.logPrefix + "&aSet interval to '&6" + FunctionRepeater.this.interval + "&a' ticks between each run.")
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many times should the function run?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRepeater.this.maxRepeats = value.orElse(FunctionRepeater.this.maxRepeats);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet max repeats to '&6" + FunctionRepeater.this.maxRepeats + "&a'."));
            }
         }
      });
   }

   public FunctionRepeater clone() {
      FunctionRepeater clone = (FunctionRepeater)super.clone();
       if (this.function != null) {
           clone.function = this.function.clone();
           clone.function.setLocation(clone.location);
       }
       return clone;
   }
}
