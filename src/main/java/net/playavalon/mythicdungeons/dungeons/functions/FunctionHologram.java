package net.playavalon.mythicdungeons.dungeons.functions;

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
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
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
                     player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet hologram location to where you're standing!"));
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
                     FunctionHologram.this.inputWaiter.runTaskLater(MythicDungeons.inst(), 200L);
                     if (FunctionHologram.this.hologramLoc == null) {
                        player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eCurrent location is &6NONE"));
                        player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eClick again to set the location to where you're standing."));
                        return;
                     }

                     final Location targetLoc = FunctionHologram.this.hologramLoc.clone();
                     FunctionHologram.this.particleIndicator = new BukkitRunnable() {
                        public void run() {
                           player.spawnParticle(Particle.END_ROD, targetLoc, 4, 0.1, 0.1, 0.1, 0.0);
                        }
                     };
                     FunctionHologram.this.particleIndicator.runTaskTimer(MythicDungeons.inst(), 0L, 10L);
                     player.sendMessage(
                        MythicDungeons.debugPrefix
                           + Util.colorize(
                              "&eCurrent location is &6X:"
                                 + MathUtils.round(FunctionHologram.this.hologramLoc.getX(), 2)
                                 + ", Y:"
                                 + MathUtils.round(FunctionHologram.this.hologramLoc.getY(), 2)
                                 + ", Z:"
                                 + MathUtils.round(FunctionHologram.this.hologramLoc.getZ(), 2)
                           )
                     );
                     player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&eClick again to set the location to where you're standing."));
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
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat message should be shown? &b(Use \\n for new lines!)"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat message should be shown?"));
            }

            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent message is:\n&6" + FunctionHologram.this.getCleanMessage()));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionHologram.this.message = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet hologram to:\n&6" + FunctionHologram.this.getCleanMessage()));
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
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eFrom how far should players be able to see the hologram?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionHologram.this.radius = value.orElse(FunctionHologram.this.radius);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet the view distance to '&6" + FunctionHologram.this.radius + "&a'."));
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
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Hologram is &bVISIBLE &6by default&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Hologram is &cINVISIBLE &6by default&a'"));
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
