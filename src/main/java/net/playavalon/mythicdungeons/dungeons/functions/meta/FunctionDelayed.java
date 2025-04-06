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
public class FunctionDelayed extends FunctionMulti {
   @SavedField
   private DungeonFunction function;
   @SavedField
   private int delay = 0;
   private BukkitRunnable task;

   public FunctionDelayed(Map<String, Object> config) {
      super("Delayed Function", config);
   }

   public FunctionDelayed() {
      super("Delayed Function");
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
            .info(Util.colorize("&cWARNING :: A delayed function doesn't have a function in '" + this.instance.getDungeon().getWorldName() + "'!"));
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
      if (this.task != null && !this.task.isCancelled()) {
         this.task.cancel();
         this.task = null;
      }

      this.function.disable();
   }

   @Override
   public void addFunction(DungeonFunction function) {
      this.function = function;
   }

   @Override
   public void runFunction(final TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (this.function != null) {
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

         this.task = new BukkitRunnable() {
            public void run() {
               FunctionDelayed.this.function.runFunction(triggerEvent, functionTargets);
            }
         };
         this.task.runTaskLater(MythicDungeons.inst(), this.delay);
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.REPEATER);
      button.setDisplayName("&bDelayed Function");
      button.addLore("&eRuns a configured function");
      button.addLore("&eafter a delayed interval.");
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
            if (FunctionDelayed.this.function != null) {
               aPlayer.setHotbar(FunctionDelayed.this.function.getMenu());
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&cYou haven't set a function to run!"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lStart Delay");
            this.button.setAmount(FunctionDelayed.this.delay);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow long should we wait before running the function in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionDelayed.this.delay = value.orElse(FunctionDelayed.this.delay);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet delay to '&6" + FunctionDelayed.this.delay + "&a' ticks."));
            }
         }
      });
   }

   public FunctionDelayed clone() {
      FunctionDelayed clone = (FunctionDelayed)super.clone();
      if (this.function == null) {
         return clone;
      } else {
         clone.function = this.function.clone();
         clone.function.setLocation(clone.location);
         return clone;
      }
   }
}
