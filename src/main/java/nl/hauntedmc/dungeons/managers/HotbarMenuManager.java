package nl.hauntedmc.dungeons.managers;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.gui.hotbar.DungeonPlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class HotbarMenuManager implements Listener {
   private final Set<UUID> pendingChatInputs = ConcurrentHashMap.newKeySet();

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onSlotSelect(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
         Player player = event.getPlayer();
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
         if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
            if (aPlayer.isChatListening()) {
               MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
               if (menuItem != null) {
                  event.setCancelled(true);
                  UUID playerId = player.getUniqueId();
                  if (!this.pendingChatInputs.add(playerId)) {
                     return;
                  }

                  String message = HelperUtils.plainText(event.originalMessage());
                  Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
                     try {
                        if (!aPlayer.isChatListening()) {
                           return;
                        }

                        menuItem.runChatActions(player, message);
                        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
                     } finally {
                        aPlayer.setChatListening(false);
                        menu.buildMenu();
                        this.pendingChatInputs.remove(playerId);
                     }
                  });
               }
            }
         }
      }
   }

   @EventHandler
   public void onClickHotbarItem(InventoryClickEvent event) {
      Player player = (Player)event.getWhoClicked();
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         if (event.getClickedInventory() instanceof PlayerInventory) {
            if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         if (event.getClickedInventory() instanceof PlayerInventory) {
            if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         if (event.getInventory() instanceof PlayerInventory) {
            if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (aPlayer != null) {
         if (aPlayer.getCurrentHotbar() instanceof DungeonPlayerHotbarMenu menu) {
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
