package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.blocks.MovingBlock;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredFunction
public class FunctionMovingBlock extends DungeonFunction {
   @SavedField
   protected Location destination;
   @SavedField
   protected double speed;
   protected Location instanceDestination;
   private MovingBlock block;
   protected boolean awaitingInput = false;
   protected BukkitRunnable inputWaiter;
   protected BukkitRunnable particleIndicator;

   public FunctionMovingBlock(String namespace, Map<String, Object> config) {
      super(namespace, config);
      this.setCategory(FunctionCategory.LOCATION);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionMovingBlock(Map<String, Object> config) {
      this("Moving Block", config);
   }

   public FunctionMovingBlock(String namespace) {
      super(namespace);
      this.setCategory(FunctionCategory.LOCATION);
      this.targetType = FunctionTargetType.NONE;
      this.setAllowChangingTargetType(false);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionMovingBlock() {
      this("Moving Block");
   }

   @Override
   public void onEnable() {
      if (this.destination == null) {
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cERROR :: Moving block function in dungeon '" + this.instance.getDungeon().getWorldName() + "' has an invalid destination!"));
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cFunction is at &6" + this.location.getX() + ", " + this.location.getY() + ", " + this.location.getZ()));
      } else {
         this.instanceDestination = this.destination.clone();
         this.instanceDestination.setWorld(this.location.getWorld());
      }
   }

   @Override
   public void onDisable() {
      if (this.block != null) {
         this.block.stop();
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (this.block == null) {
         this.block = new MovingBlock(this.location, this.speed, this.destination);
         this.block.start();
      } else if (this.block.isInactive()) {
         this.block.start();
      } else {
         this.block.stop();
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.MINECART);
      functionButton.setDisplayName("&aMoving Block");
      functionButton.addLore("&eTurns the block at this location");
      functionButton.addLore("&einto a moving block with a");
      functionButton.addLore("&econfigurable destination.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu
         .addMenuItem(
            new MenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.COMPASS);
                  this.button.setDisplayName("&d&lSet Destination");
               }

               @Override
               public void onSelect(PlayerEvent event) {
                  final Player player = event.getPlayer();
                  if (FunctionMovingBlock.this.awaitingInput) {
                     FunctionMovingBlock.this.awaitingInput = false;
                     FunctionMovingBlock.this.destination = player.getTargetBlockExact(10).getLocation();
                     FunctionMovingBlock.this.destination.setWorld(null);
                     if (FunctionMovingBlock.this.particleIndicator != null) {
                        FunctionMovingBlock.this.particleIndicator.cancel();
                     }

                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet moving block destination to where you're looking!"));
                  } else {
                     FunctionMovingBlock.this.awaitingInput = true;
                     FunctionMovingBlock.this.inputWaiter = new BukkitRunnable() {
                        public void run() {
                           FunctionMovingBlock.this.awaitingInput = false;
                           if (FunctionMovingBlock.this.particleIndicator != null) {
                              FunctionMovingBlock.this.particleIndicator.cancel();
                           }
                        }
                     };
                     FunctionMovingBlock.this.inputWaiter.runTaskLater(Dungeons.inst(), 200L);
                     if (FunctionMovingBlock.this.destination == null) {
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent location is &6NONE"));
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eClick again to set the destination to where you're looking."));
                        return;
                     }

                     final Location targetLoc = FunctionMovingBlock.this.destination.clone();
                     targetLoc.setX(targetLoc.getX() + 0.5);
                     targetLoc.setY(targetLoc.getY() + 0.5);
                     targetLoc.setZ(targetLoc.getZ() + 0.5);
                     targetLoc.setWorld(FunctionMovingBlock.this.location.getWorld());
                     FunctionMovingBlock.this.particleIndicator = new BukkitRunnable() {
                        public void run() {
                           if (FunctionMovingBlock.this.instance.isEditInstance()) {
                              player.spawnParticle(Particle.END_ROD, targetLoc, 4, 0.5, 0.5, 0.5, 0.0);
                           }
                        }
                     };
                     FunctionMovingBlock.this.particleIndicator.runTaskTimer(Dungeons.inst(), 0L, 10L);
                     Bukkit.getScheduler().runTaskLaterAsynchronously(Dungeons.inst(), () -> {
                        if (FunctionMovingBlock.this.particleIndicator != null) {
                           FunctionMovingBlock.this.particleIndicator.cancel();
                        }
                     }, 200L);
                     player.sendMessage(
                        Dungeons.logPrefix
                           + HelperUtils.colorize(
                              "&eCurrent destination is &6X:"
                                 + MathUtils.round(FunctionMovingBlock.this.destination.getX(), 2)
                                 + ", Y:"
                                 + MathUtils.round(FunctionMovingBlock.this.destination.getY(), 2)
                                 + ", Z:"
                                 + MathUtils.round(FunctionMovingBlock.this.destination.getZ(), 2)
                           )
                     );
                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eClick again to set the destination to the block you're looking at."));
                  }
               }
            }
         );
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CLOCK);
            this.button.setDisplayName("&d&lMovement Speed");
            this.button.setAmount((int)FunctionMovingBlock.this.speed);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eMany blocks should the block travel per second?"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent speed: &6" + FunctionMovingBlock.this.speed));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            FunctionMovingBlock.this.speed = value.orElse(FunctionMovingBlock.this.speed);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet speed to '&6" + FunctionMovingBlock.this.speed + "&a' blocks per second."));
            }
         }
      });
   }
}
