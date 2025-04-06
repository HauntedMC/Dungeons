package net.playavalon.mythicdungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerKeyItem;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

@DeclaredFunction
public class FunctionDoorControl extends DungeonFunction {
   @SavedField
   private boolean lockedByDefault = true;
   @SavedField
   private boolean openOnUnlock = true;
   private boolean locked = true;

   public FunctionDoorControl(Map<String, Object> config) {
      super("Door Control", config);
      this.setRequiresTrigger(false);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
   }

   public FunctionDoorControl() {
      super("Door Control");
      this.setRequiresTrigger(false);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
   }

   @Override
   public void onEnable() {
      this.locked = this.lockedByDefault;
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("LockedByDefault")) {
         this.lockedByDefault = (Boolean)config.get("LockedByDefault");
      }

      if (config.containsKey("OpenOnUnlock")) {
         this.openOnUnlock = (Boolean)config.get("OpenOnUnlock");
      }
   }

   public void setLocked(boolean locked) {
      this.locked = locked;
      if (locked) {
         this.instance.messagePlayers(LangUtils.getMessage("instance.functions.doors.lock"));
         this.instance.getInstanceWorld().playSound(this.getLocation(), "minecraft:block.iron_trapdoor.close", 5.0F, 0.7F);
         if (this.openOnUnlock) {
            this.closeDoor();
         }
      } else {
         this.instance.messagePlayers(LangUtils.getMessage("instance.functions.doors.unlock"));
         this.instance.getInstanceWorld().playSound(this.getLocation(), "minecraft:block.iron_trapdoor.open", 5.0F, 0.7F);
         if (this.openOnUnlock) {
            this.openDoor();
         }
      }
   }

   public void openDoor() {
      if (this.location.getBlock().getBlockData() instanceof Openable door) {
         door.setOpen(true);
         this.location.getBlock().setBlockData(door);
      }
   }

   public void closeDoor() {
      if (this.location.getBlock().getBlockData() instanceof Openable door) {
         door.setOpen(false);
         this.location.getBlock().setBlockData(door);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onInteractDoor(PlayerInteractEvent event) {
      if (event.getPlayer().getWorld() == this.instance.getInstanceWorld()) {
         Player player = event.getPlayer();
         if (event.getHand() != EquipmentSlot.OFF_HAND) {
            if (!event.isCancelled()) {
               if (event.getClickedBlock() != null) {
                  Location blockLoc = event.getClickedBlock().getLocation();
                  Location aboveLoc = blockLoc.clone();
                  aboveLoc.setY(blockLoc.getY() + 1.0);
                  Location belowLoc = blockLoc.clone();
                  belowLoc.setY(blockLoc.getY() - 1.0);
                  if (blockLoc.equals(this.location) || aboveLoc.equals(this.location) || belowLoc.equals(this.location)) {
                     if (this.locked) {
                        event.setCancelled(true);
                        if (this.trigger instanceof TriggerKeyItem keyTrigger) {
                           ItemStack keyItem = keyTrigger.getItem();
                           ItemMeta keyMeta = keyItem.getItemMeta();

                           assert keyMeta != null;

                           String itemName;
                           if (keyMeta.getDisplayName().equals("")) {
                              String materialName = keyItem.getType().toString().toLowerCase();
                              itemName = WordUtils.capitalizeFully(materialName.replace("_", " "));
                           } else {
                              itemName = keyMeta.getDisplayName();
                           }

                           LangUtils.sendMessage(player, "instance.functions.doors.locked-key", itemName);
                        } else {
                           LangUtils.sendMessage(player, "instance.functions.doors.locked");
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      this.setLocked(!this.locked);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.IRON_DOOR);
      functionButton.setDisplayName("&dDoor Controller");
      functionButton.addLore("&eUnlocks or locks a door at");
      functionButton.addLore("&ethis location.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.IRON_DOOR);
            this.button.setDisplayName("&d&lStart Locked");
            this.button.setEnchanted(FunctionDoorControl.this.lockedByDefault);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionDoorControl.this.lockedByDefault) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Door Locked by Default&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Door Unlocked by Default&a'"));
            }

            FunctionDoorControl.this.lockedByDefault = !FunctionDoorControl.this.lockedByDefault;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAuto Open/Close");
            this.button.setEnchanted(FunctionDoorControl.this.openOnUnlock);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionDoorControl.this.openOnUnlock) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Door Auto Opens/Closes&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Door Doesn't Auto Open/Close&a'"));
            }

            FunctionDoorControl.this.openOnUnlock = !FunctionDoorControl.this.openOnUnlock;
         }
      });
   }

   public boolean isLockedByDefault() {
      return this.lockedByDefault;
   }

   public void setLockedByDefault(boolean lockedByDefault) {
      this.lockedByDefault = lockedByDefault;
   }

   public boolean isOpenOnUnlock() {
      return this.openOnUnlock;
   }

   public void setOpenOnUnlock(boolean openOnUnlock) {
      this.openOnUnlock = openOnUnlock;
   }

   public boolean isLocked() {
      return this.locked;
   }
}
