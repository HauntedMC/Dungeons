package net.playavalon.mythicdungeons.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.menu.HotbarMenu;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class HotbarMenuManager implements Listener {
   private final Map<UUID, HotbarMenu> hotbars = new HashMap<>();

   public void register(Player player, HotbarMenu hotbar) {
      if (!this.hotbars.containsValue(hotbar)) {
         Bukkit.getPluginManager().registerEvents(hotbar, MythicDungeons.inst());
      }

      this.hotbars.put(player.getUniqueId(), hotbar);
   }

   public void unregister(Player player, HotbarMenu hotbar) {
      this.hotbars.remove(player.getUniqueId());
      if (!this.hotbars.containsValue(hotbar)) {
         HandlerList.unregisterAll(hotbar);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onSlotSelect(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
         Player player = event.getPlayer();
         MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
               menu.setSelected(player.getInventory().getHeldItemSlot());
               MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
               if (menuItem != null) {
                  event.setCancelled(true);
                  player.playSound(player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
                  aPlayer.setChatListening(false);
                  menuItem.runSelectActions(event);
                  menu.buildMenu();
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
      if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
         if (event.getHand() != EquipmentSlot.OFF_HAND) {
            menu.setSelected(player.getInventory().getHeldItemSlot());
            MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
            if (menuItem != null) {
               event.setCancelled(true);
               player.playSound(player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
               aPlayer.setChatListening(false);
               menuItem.runSelectActions(event);
               menu.buildMenu();
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
         if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
            if (event.getHand() != EquipmentSlot.OFF_HAND) {
               menu.setSelected(player.getInventory().getHeldItemSlot());
               MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
               if (menuItem != null) {
                  event.setCancelled(true);
                  player.playSound(player.getLocation(), "minecraft:item.lodestone_compass.lock", 1.0F, 1.2F);
                  aPlayer.setChatListening(false);
                  menuItem.runClickActions(event);
                  menu.buildMenu();
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onChat(AsyncPlayerChatEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
            if (aPlayer.isChatListening()) {
               MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
               if (menuItem != null) {
                  menuItem.runChatActions(event);
                  event.setMessage("");
                  player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                  event.setCancelled(true);
                  aPlayer.setChatListening(false);
                  menu.buildMenu();
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
            if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
               int slot = event.getSlot();
               if (slot <= 8) {
                  ItemStack newItem = menu.getItem(slot);
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
            if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
               int slot = event.getSlot();
               if (slot <= 8) {
                  ItemStack newItem = menu.getItem(slot);
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
            if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
               for (int slot : event.getInventorySlots()) {
                  if (slot <= 8) {
                     ItemStack newItem = menu.getItem(slot);
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
         if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
            int newSlot = event.getNewSlot();
            MenuItem menuItem = menu.getMenuItems().get(newSlot);
            if (menuItem != null) {
               menuItem.runHoverActions(event);
               menu.buildMenu();
            }
         }
      }
   }

   @EventHandler
   public void onUnhover(PlayerItemHeldEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() instanceof HotbarMenu menu) {
            int oldSlot = event.getPreviousSlot();
            MenuItem menuItem = menu.getMenuItems().get(oldSlot);
            if (menuItem != null) {
               menuItem.runUnhoverActions(event);
               menu.buildMenu();
            }
         }
      }
   }
}
