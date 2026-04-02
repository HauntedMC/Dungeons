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
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cWARNING :: A delayed function doesn't have a function in '" + this.instance.getDungeon().getWorldName() + "'!"));
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
   public void runFunction(final TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (this.function != null) {
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

         this.task = new BukkitRunnable() {
            public void run() {
               FunctionDelayed.this.function.runFunction(triggerEvent, functionTargets);
            }
         };
         this.task.runTaskLater(Dungeons.inst(), this.delay);
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
            Dungeons.inst().getAvnAPI().openGUI(player, "functionmenu");
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
            if (FunctionDelayed.this.function != null) {
               aPlayer.setHotbar(FunctionDelayed.this.function.getMenu());
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou haven't set a function to run!"));
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow long should we wait before running the function in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionDelayed.this.delay = value.orElse(FunctionDelayed.this.delay);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet delay to '&6" + FunctionDelayed.this.delay + "&a' ticks."));
            }
         }
      });
   }

   public FunctionDelayed clone() {
      FunctionDelayed clone = (FunctionDelayed)super.clone();
       if (this.function != null) {
           clone.function = this.function.clone();
           clone.function.setLocation(clone.location);
       }
       return clone;
   }
}
