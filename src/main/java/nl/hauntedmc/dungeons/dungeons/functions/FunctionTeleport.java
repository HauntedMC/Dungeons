package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredFunction
public class FunctionTeleport extends DungeonFunction {
   @SavedField
   private Location teleportTarget;
   private Location instanceLoc;
   private boolean awaitingInput = false;
   private BukkitRunnable inputWaiter;
   private BukkitRunnable particleIndicator;

   public FunctionTeleport(Map<String, Object> config) {
      super("Teleporter", config);
      this.setCategory(FunctionCategory.PLAYER);
      this.setRequiresTarget(true);
   }

   public FunctionTeleport() {
      super("Teleporter");
      this.setCategory(FunctionCategory.PLAYER);
      this.setRequiresTarget(true);
   }

   @Override
   public void onEnable() {
      if (this.teleportTarget == null) {
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cERROR :: Teleport function in dungeon '" + this.instance.getDungeon().getWorldName() + "' is invalid!"));
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cFunction is at &6" + this.location.getX() + ", " + this.location.getY() + ", " + this.location.getZ()));
      } else {
         this.instanceLoc = this.teleportTarget.clone();
         this.instanceLoc.setWorld(this.location.getWorld());
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (this.instanceLoc != null) {
         for (DungeonPlayer aPlayer : targets) {
            if (aPlayer.getInstance() == this.instance) {
               HelperUtils.forceTeleport(aPlayer.getPlayer(), this.instanceLoc);
            }
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.ENDER_PEARL);
      button.setDisplayName("&aTeleporter");
      button.addLore("&eTeleports the target player(s)");
      button.addLore("&eto a configured location.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu
         .addMenuItem(
            new MenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.COMPASS);
                  this.button.setDisplayName("&d&lSet Location");
               }

               @Override
               public void onSelect(PlayerEvent event) {
                  final Player player = event.getPlayer();
                  if (FunctionTeleport.this.awaitingInput) {
                     FunctionTeleport.this.awaitingInput = false;
                     FunctionTeleport.this.teleportTarget = player.getLocation();
                     FunctionTeleport.this.teleportTarget.setWorld(null);
                     if (FunctionTeleport.this.particleIndicator != null) {
                        FunctionTeleport.this.particleIndicator.cancel();
                     }

                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet teleport location to where you're standing!"));
                  } else {
                     FunctionTeleport.this.awaitingInput = true;
                     FunctionTeleport.this.inputWaiter = new BukkitRunnable() {
                        public void run() {
                           FunctionTeleport.this.awaitingInput = false;
                           if (FunctionTeleport.this.particleIndicator != null) {
                              FunctionTeleport.this.particleIndicator.cancel();
                           }
                        }
                     };
                     FunctionTeleport.this.inputWaiter.runTaskLater(Dungeons.inst(), 200L);
                     if (FunctionTeleport.this.teleportTarget == null) {
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent location is &6NONE"));
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eClick again to set the location to where you're standing."));
                        return;
                     }

                     final Location targetLoc = FunctionTeleport.this.teleportTarget.clone();
                     targetLoc.setY(targetLoc.getY() + 0.5);
                     targetLoc.setWorld(FunctionTeleport.this.location.getWorld());
                     FunctionTeleport.this.particleIndicator = new BukkitRunnable() {
                        public void run() {
                           if (FunctionTeleport.this.instance.isEditInstance()) {
                              player.spawnParticle(Particle.END_ROD, targetLoc, 4, 0.1, 0.1, 0.1, 0.0);
                           }
                        }
                     };
                     FunctionTeleport.this.particleIndicator.runTaskTimer(Dungeons.inst(), 0L, 10L);
                     Bukkit.getScheduler().runTaskLaterAsynchronously(Dungeons.inst(), () -> {
                        if (FunctionTeleport.this.particleIndicator != null) {
                           FunctionTeleport.this.particleIndicator.cancel();
                        }
                     }, 200L);
                     player.sendMessage(
                        Dungeons.logPrefix
                           + HelperUtils.colorize(
                              "&eCurrent location is &6X:"
                                 + MathUtils.round(FunctionTeleport.this.teleportTarget.getX(), 2)
                                 + ", Y:"
                                 + MathUtils.round(FunctionTeleport.this.teleportTarget.getY(), 2)
                                 + ", Z:"
                                 + MathUtils.round(FunctionTeleport.this.teleportTarget.getZ(), 2)
                           )
                     );
                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eClick again to set the location to where you're standing."));
                  }
               }
            }
         );
   }

   public Location getTeleportTarget() {
      return this.teleportTarget;
   }

   public void setTeleportTarget(Location teleportTarget) {
      this.teleportTarget = teleportTarget;
   }

   public Location getInstanceLoc() {
      return this.instanceLoc;
   }
}
