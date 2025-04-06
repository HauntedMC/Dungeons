package net.playavalon.mythicdungeons.menu;

import java.util.HashMap;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.Hotbar;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.ServerVersion;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public abstract class HotbarMenu extends Hotbar implements Listener {
   protected HashMap<Integer, MenuItem> menuItems = new HashMap<>();
   protected HashMap<Integer, MenuAction<PlayerEvent>> selectActions;
   protected HashMap<Integer, MenuAction<AsyncPlayerChatEvent>> chatActions = new HashMap<>();
   protected HashMap<Integer, MenuAction<PlayerItemHeldEvent>> hoverActions;
   protected int selected;

   public HotbarMenu() {
      this.selectActions = new HashMap<>();
      this.hoverActions = new HashMap<>();
   }

   @Override
   public void setHotbar(PlayerInventory inv) {
      this.buildMenu();
      super.setHotbar(inv);
   }

   @Override
   public void setHotbar(PlayerInventory inv, boolean setHand) {
      this.buildMenu();
      super.setHotbar(inv, setHand);
   }

   public void addMenuItem(MenuItem item) {
      this.addMenuItem(this.menuItems.size(), item);
   }

   public void addMenuItem(int slot, MenuItem item) {
      this.menuItems.put(slot, item);
      item.setMenu(this);
   }

   public void removeMenuItem(int slot) {
      this.menuItems.remove(slot);
   }

   public void buildMenu() {
      for (Entry<Integer, MenuItem> pair : this.menuItems.entrySet()) {
         int slot = pair.getKey();
         MenuItem item = pair.getValue();
         if (item == null) {
            this.setItem(slot, null);
         } else {
            item.buildButton();
            if (item.getButton() == null) {
               this.setItem(slot, null);
            } else {
               this.setItem(slot, item.getButton().getItem());
            }
         }
      }
   }

   public void updateMenu(MythicPlayer aPlayer) {
      aPlayer.setHotbar(this);
   }

   public void initSelectActions() {
   }

   public void initChatActions() {
   }

   public void initHoverActions() {
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onSlotSelect(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
         Player player = event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getCurrentHotbar() == this) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
               this.selected = player.getInventory().getHeldItemSlot();
               MenuItem menuItem = this.menuItems.get(this.selected);
               if (menuItem != null) {
                  event.setCancelled(true);
                  player.playSound(player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
                  aPlayer.setChatListening(false);
                  menuItem.runSelectActions(event);
                  this.buildMenu();
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onSlotSelect(PlayerInteractAtEntityEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer.getCurrentHotbar() == this) {
         if (event.getHand() != EquipmentSlot.OFF_HAND) {
            this.selected = player.getInventory().getHeldItemSlot();
            MenuItem menuItem = this.menuItems.get(this.selected);
            if (menuItem != null) {
               event.setCancelled(true);
               player.playSound(player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
               aPlayer.setChatListening(false);
               menuItem.runSelectActions(event);
               this.buildMenu();
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onSlotClick(PlayerInteractEvent event) {
      if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
         Player player = event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getCurrentHotbar() == this) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
               this.selected = player.getInventory().getHeldItemSlot();
               MenuItem menuItem = this.menuItems.get(this.selected);
               if (menuItem != null) {
                  event.setCancelled(true);
                  player.playSound(player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
                  aPlayer.setChatListening(false);
                  menuItem.runClickActions(event);
                  this.buildMenu();
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() == this) {
            if (aPlayer.isChatListening()) {
               MenuItem menuItem = this.menuItems.get(this.selected);
               if (menuItem != null) {
                  menuItem.runChatActions(event);
                  event.setMessage("");
                  player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                  event.setCancelled(true);
                  aPlayer.setChatListening(false);
                  this.buildMenu();
               }
            }
         }
      }
   }

   @EventHandler
   public void onClickHotbarItem(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (event.getClickedInventory() instanceof PlayerInventory) {
            if (aPlayer.getCurrentHotbar() == this) {
               int slot = event.getSlot();
               if (slot <= 8) {
                  ItemStack newItem = this.getItem(slot);
                  if (newItem != null && newItem.getType() != Material.AIR) {
                     event.setCancelled(true);
                     player.closeInventory();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onClickHotbarItem(InventoryCreativeEvent event) {
      Player player = (Player)event.getWhoClicked();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (event.getClickedInventory() instanceof PlayerInventory) {
            if (aPlayer.getCurrentHotbar() == this) {
               int slot = event.getSlot();
               if (slot <= 8) {
                  ItemStack newItem = this.getItem(slot);
                  if (newItem != null && newItem.getType() != Material.AIR) {
                     event.setCancelled(true);
                     player.closeInventory();
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onDragHotbarItem(InventoryDragEvent event) {
      Player player = (Player)event.getWhoClicked();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (event.getInventory() instanceof PlayerInventory) {
            if (aPlayer.getCurrentHotbar() == this) {
               for (int slot : event.getInventorySlots()) {
                  if (slot <= 8) {
                     ItemStack newItem = this.getItem(slot);
                     if (newItem != null && newItem.getType() != Material.AIR) {
                        event.setCancelled(true);
                        player.closeInventory();
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onHover(PlayerItemHeldEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() == this) {
            int newSlot = event.getNewSlot();
            MenuItem menuItem = this.menuItems.get(newSlot);
            if (menuItem != null) {
               menuItem.runHoverActions(event);
               this.buildMenu();
            }
         }
      }
   }

   @EventHandler
   public void onUnhover(PlayerItemHeldEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() == this) {
            int oldSlot = event.getPreviousSlot();
            MenuItem menuItem = this.menuItems.get(oldSlot);
            if (menuItem != null) {
               menuItem.runUnhoverActions(event);
               this.buildMenu();
            }
         }
      }
   }

   public static HotbarMenu create() {
      return (HotbarMenu)(ServerVersion.isPaper() ? new PaperHotbarMenu() {} : new HotbarMenu() {});
   }

   public HashMap<Integer, MenuItem> getMenuItems() {
      return this.menuItems;
   }

   public int getSelected() {
      return this.selected;
   }

   public void setSelected(int selected) {
      this.selected = selected;
   }
}
