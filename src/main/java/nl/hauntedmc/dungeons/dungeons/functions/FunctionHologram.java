package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredFunction
public class FunctionHologram extends DungeonFunction {
   @SavedField
   private Location hologramLoc;
   @SavedField
   private String message;
   @SavedField
   private int radius = 25;
   @SavedField
   private boolean visibleByDefault = true;
   private boolean awaitingInput = false;
   private BukkitRunnable inputWaiter;
   private BukkitRunnable particleIndicator;
   private boolean visible = true;

   public FunctionHologram(Map<String, Object> config) {
      super("Hologram", config);
      this.targetType = FunctionTargetType.NONE;
      this.setCategory(FunctionCategory.LOCATION);
      this.setAllowRetriggerByDefault(true);
   }

   public FunctionHologram() {
      super("Hologram");
      this.setAllowChangingTargetType(false);
      this.targetType = FunctionTargetType.NONE;
      this.setCategory(FunctionCategory.LOCATION);
      this.setAllowRetriggerByDefault(true);
   }

   @Override
   public void onEnable() {
      this.visible = this.visibleByDefault;
      if (this.instance.isPlayInstance()) {
         this.instance.asPlayInstance().addHologram(this);
         if (!this.visible) {
            this.instance.asPlayInstance().hideHologramFunction(this);
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.NAME_TAG);
      functionButton.setDisplayName("&bHologram");
      functionButton.addLore("&eDisplays hologram text at a");
      functionButton.addLore("&especified location. Triggering");
      functionButton.addLore("&etoggles hologram visibility.");
      return functionButton;
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      this.toggleVisible();
   }

   private void toggleVisible() {
      InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         this.visible = !this.visible;
         if (this.visible) {
            instance.showHologramFunction(this);
         } else {
            instance.hideHologramFunction(this);
         }
      }
   }

   public Location getHologramLoc() {
      return this.hologramLoc == null ? this.location : this.hologramLoc;
   }

   public String getCleanMessage() {
      return this.message == null ? null : this.message.replace("\\n", "\n");
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
                  if (FunctionHologram.this.awaitingInput) {
                     FunctionHologram.this.awaitingInput = false;
                     FunctionHologram.this.hologramLoc = player.getLocation();
                     FunctionHologram.this.hologramLoc.setWorld(null);
                     FunctionHologram.this.hologramLoc.setY(FunctionHologram.this.hologramLoc.getY() + 1.75);
                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet hologram location to where you're standing!"));
                  } else {
                     FunctionHologram.this.awaitingInput = true;
                     FunctionHologram.this.inputWaiter = new BukkitRunnable() {
                        public void run() {
                           FunctionHologram.this.awaitingInput = false;
                           if (FunctionHologram.this.particleIndicator != null) {
                              FunctionHologram.this.particleIndicator.cancel();
                           }
                        }
                     };
                     FunctionHologram.this.inputWaiter.runTaskLater(Dungeons.inst(), 200L);
                     if (FunctionHologram.this.hologramLoc == null) {
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eCurrent location is &6NONE"));
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eClick again to set the location to where you're standing."));
                        return;
                     }

                     final Location targetLoc = FunctionHologram.this.hologramLoc.clone();
                     FunctionHologram.this.particleIndicator = new BukkitRunnable() {
                        public void run() {
                           player.spawnParticle(Particle.END_ROD, targetLoc, 4, 0.1, 0.1, 0.1, 0.0);
                        }
                     };
                     FunctionHologram.this.particleIndicator.runTaskTimer(Dungeons.inst(), 0L, 10L);
                     player.sendMessage(
                        Dungeons.logPrefix
                           + HelperUtils.colorize(
                              "&eCurrent location is &6X:"
                                 + MathUtils.round(FunctionHologram.this.hologramLoc.getX(), 2)
                                 + ", Y:"
                                 + MathUtils.round(FunctionHologram.this.hologramLoc.getY(), 2)
                                 + ", Z:"
                                 + MathUtils.round(FunctionHologram.this.hologramLoc.getZ(), 2)
                           )
                     );
                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&eClick again to set the location to where you're standing."));
                  }
               }
            }
         );
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NAME_TAG);
            this.button.setDisplayName("&d&lHologram Message");
         }

         @Override
         public void onSelect(Player player) {
            if (FunctionHologram.this.instance.getDisplayHandler() != null) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat message should be shown? &b(Use \\n for new lines!)"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat message should be shown?"));
            }

            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent message is:\n&6" + FunctionHologram.this.getCleanMessage()));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionHologram.this.message = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet hologram to:\n&6" + FunctionHologram.this.getCleanMessage()));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lView Distance");
            this.button.setAmount(FunctionHologram.this.radius);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eFrom how far should players be able to see the hologram?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionHologram.this.radius = value.orElse(FunctionHologram.this.radius);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet the view distance to '&6" + FunctionHologram.this.radius + "&a'."));
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.STRUCTURE_VOID);
            this.button.setDisplayName("&d&lVisible by Default");
            this.button.setEnchanted(FunctionHologram.this.visibleByDefault);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionHologram.this.visibleByDefault) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Hologram is &bVISIBLE &6by default&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Hologram is &cINVISIBLE &6by default&a'"));
            }

            FunctionHologram.this.visibleByDefault = !FunctionHologram.this.visibleByDefault;
         }
      });
   }

   public String getMessage() {
      return this.message;
   }

   public int getRadius() {
      return this.radius;
   }

   public boolean isVisibleByDefault() {
      return this.visibleByDefault;
   }

   public boolean isVisible() {
      return this.visible;
   }
}
