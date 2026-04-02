package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredFunction
public class FunctionRedstoneBlock extends DungeonFunction {
   @SavedField
   private int delay = 0;
   @SavedField
   private int count = 1;
   @SavedField
   private int rate = 5;
   private BukkitRunnable repeater;
   private int status = 0;

   public FunctionRedstoneBlock(Map<String, Object> config) {
      super("Redstone Block", config);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
   }

   public FunctionRedstoneBlock() {
      super("Redstone Block");
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
   }

   @Override
   public void onDisable() {
      if (this.repeater != null && !this.repeater.isCancelled()) {
         this.repeater.cancel();
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      final World world = this.instance.getInstanceWorld();
      this.repeater = new BukkitRunnable() {
         public void run() {
            world.getBlockAt(FunctionRedstoneBlock.this.location).setType(Material.REDSTONE_BLOCK);
            FunctionRedstoneBlock.this.status++;
            if (FunctionRedstoneBlock.this.status >= FunctionRedstoneBlock.this.count) {
               this.cancel();
            } else {
               (new BukkitRunnable() {
                  public void run() {
                     world.getBlockAt(FunctionRedstoneBlock.this.location).setType(Material.AIR);
                  }
               }).runTaskLater(Dungeons.inst(), FunctionRedstoneBlock.this.rate - 1);
            }
         }
      };
      this.repeater.runTaskTimer(Dungeons.inst(), this.delay, this.rate);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.REDSTONE_BLOCK);
      functionButton.setDisplayName("&dPlace Redstone Block");
      functionButton.addLore("&ePlaces a redstone block at");
      functionButton.addLore("&ethis location.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&a&lDelay");
            this.button.setAmount(FunctionRedstoneBlock.this.delay);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow long should the wait be before placing redstone?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRedstoneBlock.this.delay = value.orElse(FunctionRedstoneBlock.this.delay);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet redstone delay to '&6" + FunctionRedstoneBlock.this.delay + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.COMPARATOR);
            this.button.setDisplayName("&a&lRepeat Count");
            this.button.setAmount(FunctionRedstoneBlock.this.count);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many times should we repeat the redstone signal?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRedstoneBlock.this.count = value.orElse(FunctionRedstoneBlock.this.count);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet repeat count to '&6" + FunctionRedstoneBlock.this.count + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&a&lRepeat Interval");
            this.button.setAmount(FunctionRedstoneBlock.this.rate);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow long should the pause between signals be?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionRedstoneBlock.this.rate = value.orElse(FunctionRedstoneBlock.this.rate);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet repeat interval to '&6" + FunctionRedstoneBlock.this.rate + "&a'"));
            }
         }
      });
   }
}
